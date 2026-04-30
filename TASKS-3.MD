# Задания на доработку — Часть 3: Elasticsearch + Redis + Kafka

---

## Зачем всё это: общая картина

В первой части мы наводили порядок **внутри** одного приложения (транзакции, валидация, тесты). Во второй — учились работать с **инфраструктурой сборки и контракта** (Gradle, OpenAPI, multi-module). В третьей мы выходим на следующий уровень: учимся работать с **внешними системами**, без которых не обходится ни один реальный бэкенд.

Все три технологии этой части — **Elasticsearch, Redis, Kafka** — встречаются почти в каждом продакшен-проекте. И каждая решает свой класс задач, который на одной БД решить либо невозможно, либо очень дорого.

Главное в этой части — не «выучить синтаксис ещё одной библиотеки». Главное — научиться **видеть проблему до решения**. Поэтому каждая часть устроена так: сначала показывается реальная боль, в которую упирается текущий код, и только потом вводится технология, которая её снимает. Если ты в какой-то момент почувствуешь «а зачем мы вообще это делаем?» — значит, нужно вернуться на пару заданий назад и переcмотреть проблему. Без боли решение не запоминается.

Сюжет такой. После Part 2 у нас есть аккуратное multi-module приложение с OpenAPI-контрактом. Представим, что библиотекой начинают пользоваться по-настоящему:

1. **Каталог разрастается до тысяч книг.** Поиск через `LIKE '%толстой%'` тормозит, не находит «Толстого» по запросу «толстой», бесполезен при опечатках, не умеет фасетами по жанру/автору. → **Elasticsearch.**
2. **Сервис становится популярным, инстансов несколько.** Двое одновременно жмут «забронировать» одну и ту же книгу, и из-за гонки оба получают `200 OK`. Кроме того, `GET /books/{id}` бьёт в БД на каждый запрос, хотя данные меняются раз в неделю. → **Redis: кеш + распределённая блокировка.**
3. **Появляется второй сервис** — `library-notifier`, который должен уведомлять читателей о бронировании. HTTP-вызовы между сервисами — путь к жёсткой связности. Между сервисами должна быть **шина событий**, и события должны доставляться **надёжно**, даже когда брокер недоступен. → **Kafka + outbox-паттерн.**

К концу этих заданий у тебя будет распределённое приложение из четырёх Gradle-модулей и четырёх backing-services (Postgres, Elasticsearch, Redis, Kafka). Ты увидишь и решишь руками: проблему синхронизации двух хранилищ, гонку при параллельных запросах, потерю событий между транзакцией и брокером. Всё это — реальные темы реальных собеседований уровня middle/senior.

---

## Структура репозитория после Part 3

После Part 2 у тебя два модуля: `library-api` (контракт) и `library-app` (реализация). В Part 3 добавляется ещё два:

```
library-demo/
├── docker-compose.yml         ← + elasticsearch, redis, kafka, kafka-ui
├── settings.gradle.kts        ← include 4 модулей
├── library-api/               ← без изменений (расширим спеку для /search)
├── library-events/            ← НОВОЕ: event-классы (java-library, без Spring)
├── library-app/               ← +ES, +Redis, +Kafka producer, +outbox
└── library-notifier/          ← НОВОЕ: Spring Boot, Kafka consumer
```

`library-events` — отдельный модуль ровно по тем же причинам, что и `library-api` в Part 2: контракт нужен **обоим** концам шины. Если положить event-классы внутрь `library-app` — `library-notifier` будет вынужден тащить весь сервер ради двух POJO. Это та же дилемма «контракт vs реализация», с которой ты уже сталкивался.

---

## Часть A: Elasticsearch — поиск, который не стыдно показать

### Почему Postgres LIKE — это не поиск

Текущий код умеет фильтровать книги через `Specification` и `LIKE '%query%'`. Это работает, пока книг сотни. На тысячах — начинаются проблемы:

| Что хочет пользователь | Что умеет LIKE | Что умеет Elasticsearch |
|---|---|---|
| Найти «Толстого» по запросу «толстой» | Не найдёт (регистрозависимость, окончания) | Найдёт (русская морфология) |
| Найти «Война и мир» по запросу «войан и мир» (опечатка) | Не найдёт | Найдёт (fuzzy search) |
| Релевантность: точные совпадения выше частичных | Нет ранжирования | BM25 из коробки |
| Фасеты: «сколько книг каждого жанра в результатах» | Нужен отдельный `GROUP BY` запрос | Aggregations в одном запросе |
| Скорость на миллионах документов | O(N) по таблице | O(log N) по инвертированному индексу |

Elasticsearch — это **не замена БД**, это специализированное хранилище для полнотекстового поиска. Postgres остаётся **источником правды** (system of record): транзакции, бронирование, целостность. Elasticsearch — это **read-модель**: денормализованная копия данных, оптимизированная под поиск.

Главная инженерная проблема, которая всплывает: **синхронизация двух хранилищ**. Если книга обновилась в Postgres, как она попадёт в индекс? Есть несколько подходов (CDC, application events, batch reindex), и в этой части мы пройдём через два: ручная переиндексация и публикация application-events. В Части C мы увидим, почему оба этих подхода **не идеальны**, и как это правильно решается через outbox.

---

### 1. Поднять Elasticsearch в `docker-compose` и подготовить Testcontainers

**Что сделать:**

1. В `docker-compose.yml` добавить сервис:

```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.13.0
  environment:
    - discovery.type=single-node
    - xpack.security.enabled=false
    - ES_JAVA_OPTS=-Xms512m -Xmx512m
  ports:
    - "9200:9200"
  healthcheck:
    test: ["CMD-SHELL", "curl -fs http://localhost:9200/_cluster/health || exit 1"]
    interval: 5s
    timeout: 3s
    retries: 20
```

2. Добавить `testImplementation` зависимости в `library-app`:
   - `org.testcontainers:elasticsearch`
   - `org.testcontainers:junit-jupiter`
3. Создать абстрактный класс `BaseElasticsearchIntegrationTest` со static-полем `@Container ElasticsearchContainer`. Использовать `@DynamicPropertySource`, чтобы прокинуть URL контейнера в `spring.elasticsearch.uris`.
4. Убедиться, что `docker compose up elasticsearch` поднимается, `curl localhost:9200` отвечает, а пустой тест от `BaseElasticsearchIntegrationTest` стартует контейнер.

**Зачем:** В реальной разработке инфраструктура нужна и для тестов, и для ручных проверок. Testcontainers — стандарт де-факто для интеграционных тестов с настоящими брокерами и БД (не моками). docker-compose — это то, что ты будешь поднимать каждый рабочий день, чтобы потыкать API в Postman. Оба навыка обязательны.

**Подсказка:** Для разработки (`xpack.security.enabled=false`) — нормально. В проде security включают всегда, и тогда нужны username/password. Но это не твоя боль на этом этапе.

---

### 2. Подключить Elasticsearch Java Client

**Что сделать:**

1. Добавить в `library-app/build.gradle.kts` зависимость:
```kotlin
implementation("co.elastic.clients:elasticsearch-java:8.13.0")
implementation("com.fasterxml.jackson.core:jackson-databind")
```
2. Создать конфиг `ElasticsearchConfig` с бинами `RestClient`, `ElasticsearchTransport`, `ElasticsearchClient`. URL читать из `spring.elasticsearch.uris`.
3. В `application.properties` добавить:
```
spring.elasticsearch.uris=http://localhost:9200
```
4. Убедиться, что приложение стартует и через `ElasticsearchClient` можно вызвать `client.cluster().health()`.

**Зачем:** Есть искушение взять `spring-data-elasticsearch` — там есть знакомые `Repository`-интерфейсы. Не делай этого. Spring Data ES прячет от тебя главное: **Query DSL**. На собеседовании или при отладке прода тебе придётся читать запросы и агрегации руками — без понимания того, как устроен `match`, `bool`, `aggs`, ты будешь беспомощен. Низкоуровневый клиент учит **думать запросами**, а не аннотациями.

**Ловушка:** Не путай старый `RestHighLevelClient` (deprecated с 7.15) и новый `ElasticsearchClient` (актуальный с 8.x). В интернете куча примеров на старом — они не подходят.

---

### 3. Описать индекс `books` с маппингом и русским анализатором

**Что сделать:**

1. На старте приложения проверять наличие индекса `books`. Если нет — создать с маппингом:

| Поле | Тип | Анализатор |
|---|---|---|
| `id` | `keyword` | — |
| `name` | `text` | `russian` |
| `author` | `text` | `russian` |
| `genre` | `keyword` | — |
| `releaseDate` | `date` (`yyyy-MM-dd`) | — |
| `reserved` | `keyword` (или `null`) | — |

2. Реализовать через `client.indices().create(...)` с указанием `mapping` и `settings`.
3. Класс `BookDocument` (POJO для (де)сериализации) — с теми же полями.

**Зачем:** Маппинг — это «схема таблицы» в мире Elasticsearch. Без него ES сам угадывает типы (это называется dynamic mapping), и угадывает плохо: текст превращается и в `text`, и в `keyword` одновременно, занимает в два раза больше места, а агрегации работают непредсказуемо.

Принципиальная разница, которую нужно запомнить:

- **`text`** — поле разбивается на токены, по нему работает полнотекстовый поиск (`match`). По `text` **нельзя** агрегировать и сортировать.
- **`keyword`** — поле хранится целиком, без разбиения. По нему работают точные сравнения (`term`), фасеты, сортировка. Полнотекстового поиска по `keyword` нет.

Поэтому `name` — `text` (по нему ищем), а `genre` — `keyword` (по нему фасетируем).

Анализатор `russian` делает три вещи: приводит к нижнему регистру, удаляет стоп-слова («и», «в», «на»), отрезает окончания (стемминг). После этого «Толстого», «Толстому», «Толстым» все превращаются в один токен «толст». Поэтому поиск «толстой» находит «Толстого».

**Подсказка:** Для русского анализатора достаточно встроенного `"analyzer": "russian"`. Никаких плагинов ставить не надо.

---

### 4. Класс `BookSearchService` — `match` по `name`/`author` + фасеты по `genre`

**Что сделать:**

1. Метод `BookSearchResult search(String query, String genreFilter, int page, int size)`.
2. Запрос — `multi_match` по полям `name` и `author`. Если `genreFilter` не null — обернуть в `bool` с `filter` на `term`.
3. Добавить агрегацию `terms` по `genre` (фасет: «сколько книг каждого жанра в результатах»).
4. Маппить ответ в DTO: список `BookDto` + список `{genre, count}` фасетов + общее число найденных.

**Зачем:** Это первая встреча с Query DSL вживую. Через год работы в индустрии ты запрос на DSL прочитаешь быстрее, чем JPQL. Привыкай.

`multi_match` с двумя полями — это OR по полям, с весами по умолчанию 1.0. Можно бустить (`name^2` — совпадение в названии важнее, чем в авторе). Это уже на твой вкус.

**Ловушка:** Когда добавляешь `filter` (а не `must`), ES не считает его в скоринг — это правильное поведение для жанра как фильтра. Если положишь жанр в `must` — релевантность поедет.

---

### 5. Эндпоинт `GET /books/search` через расширение OpenAPI-спеки

**Что сделать:**

1. Открыть `library-api/src/main/resources/openapi/library-api.yaml`. Добавить эндпоинт:

```yaml
/books/search:
  get:
    summary: Полнотекстовый поиск по каталогу
    parameters:
      - name: q
        in: query
        required: true
        schema: { type: string, minLength: 2 }
      - name: genre
        in: query
        schema: { type: string }
      - name: page
        in: query
        schema: { type: integer, default: 0 }
      - name: size
        in: query
        schema: { type: integer, default: 20 }
    responses:
      "200":
        description: Результаты поиска
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/BookSearchResultDto"
```

2. Добавить схему `BookSearchResultDto` в `components/schemas` — с полями `content`, `totalHits`, `facets` (массив `{genre, count}`).
3. Перегенерировать (`./gradlew :library-api:openApiGenerate`) — в сгенерированном `BooksApi` появится метод `searchBooks(...)`. Реализовать его в `LibraryController`, делегируя в `BookSearchService`.

**Зачем:** Контракт-фёрст применяется и здесь — поиск это часть HTTP-API, его место в спеке. Если просто добавить `@GetMapping("/books/search")` в контроллере — клиент об этом не узнает, а Swagger UI не покажет. Один источник правды для всего HTTP — спека.

---

### 6. Синхронизация: `ApplicationEventPublisher` на `save`/`update`/`delete`

**Что сделать:**

1. В `LibraryService` после `save`/`update`/`delete`/`reserve`/`return` публиковать `BookChangedEvent` через `ApplicationEventPublisher`. Событие содержит `id` и тип изменения (`UPSERT`/`DELETE`).
2. Создать `BookIndexUpdater` с методом, помеченным `@TransactionalEventListener(phase = AFTER_COMMIT)`. Он читает книгу из БД (или удаляет из индекса при `DELETE`) и пишет в ES.
3. Покрыть тестом: создать книгу через сервис → подождать индексации → найти через `BookSearchService`.

**Зачем:** Это первый шаг к решению **проблемы двух хранилищ**. Источник правды — Postgres. Изменение в правде должно отразиться в read-модели.

Почему именно `AFTER_COMMIT`, а не `BEFORE_COMMIT` или просто `@EventListener`? Потому что если транзакция в Postgres откатилась, событие в ES публиковать **нельзя** — иначе индекс будет содержать данные, которых в БД нет. `AFTER_COMMIT` гарантирует, что событие сработает только после успешного коммита.

**Ловушка:** Этот подход (`AFTER_COMMIT`) **всё равно неидеален**: если приложение упадёт **между** коммитом и обновлением индекса — индекс отстанет от БД, и без переиндексации сам не догонит. Это та же проблема, что и с Kafka в Части C, и решается она тем же паттерном — outbox. Пока запомни эту трещину; в Части C мы её закроем по-настоящему.

---

### 7. Полная переиндексация: `POST /admin/reindex`

**Что сделать:**

1. Эндпоинт `POST /admin/reindex` (опиши в спеке отдельным тегом `admin`).
2. Реализация: удалить индекс, создать заново с маппингом, прочитать всё из БД пачками, залить в ES через `bulk` API.
3. Защитить `Spring Security` — доступен только под ролью `ADMIN`.

**Зачем:** Любая read-модель рано или поздно расходится с правдой. Изменился маппинг — нужно пересоздать индекс. Произошла авария — нужно восстановить. Способ восстановиться **из источника правды** должен быть всегда. Это не «фича на потом», это **обязательная** возможность для любой системы с read-моделью.

**Подсказка:** `bulk` API в ES Java Client — это `BulkRequest` с серией `BulkOperation`. Не пиши документы по одному в цикле — это в десятки раз медленнее.

---

### 8. Интеграционный тест `BookSearchIT`

**Что сделать:**

1. Тест от `BaseElasticsearchIntegrationTest` + Testcontainers Postgres.
2. Сценарии:
   - Создать книгу «Война и мир» / Толстой через `LibraryService` → подождать (`Awaitility`) появления в индексе → запрос `q=войн` → возвращает книгу.
   - Поиск с опечаткой `q=войан` → пока **не находит** (fuzzy ещё не добавлен — это упражнение «дальше можно копать»).
   - Поиск с фильтром `genre=novel` → возвращает только нужные.
   - Удалить книгу → подождать → `q=...` больше не возвращает её.

**Зачем:** Реальный ES за реальным контейнером — единственный способ проверить, что маппинг, анализатор, синхронизация и поиск работают вместе. Юнит-тесты с моком клиента ловят синтаксис, но не ловят **поведение**.

**Подсказка:** ES — eventually consistent: между записью и появлением в результатах поиска — секунда-две (зависит от `refresh_interval`). Не делай `Thread.sleep(5000)` — используй `Awaitility.await().until(...)`. В тестах можно ускорить, вызывая `client.indices().refresh(...)` руками после записи.

---

## Часть B: Redis — кеш и распределённая блокировка

### Почему Redis

Redis — это in-memory key-value store с богатой системой типов (строки, списки, sets, hashes, sorted sets, streams). Спросишь «зачем мне ещё одно хранилище» — отвечу: Redis в реальном приложении играет **минимум три роли**:

1. **Кеш** — ускоряет горячие чтения, разгружает основную БД.
2. **Координация** — распределённые блокировки, семафоры, rate-limiting (когда у тебя несколько инстансов сервиса, и им нужно «договариваться»).
3. **Транзиентное хранилище** — сессии, временные токены, очереди задач (когда персистентность БД избыточна).

Redis — почти всегда «сбоку», а не «вместо БД». Постгрес остаётся источником правды; Redis ускоряет и координирует.

В этой части мы решим две конкретные боли:

- **Карточка книги** — самый частый запрос, бьёт в БД зря (роль кеша).
- **Параллельная бронь** — две одновременные брони одной книги ломают бизнес-инвариант (роль координации).

---

### 9. Поднять Redis в `docker-compose` и подготовить Testcontainers

**Что сделать:**

1. В `docker-compose.yml`:
```yaml
redis:
  image: redis:7-alpine
  ports:
    - "6379:6379"
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 5s
    retries: 5
```
2. Добавить `testImplementation("com.redis:testcontainers-redis")` (или `org.testcontainers:redis`).
3. Создать `BaseRedisIntegrationTest` с `@Container RedisContainer` и `@DynamicPropertySource` на `spring.data.redis.host`/`spring.data.redis.port`.

**Зачем:** Redis под Testcontainers стартует за секунду — практически бесплатно, тесты остаются быстрыми. Это образец для всех будущих интеграционных тестов: каждое внешнее хранилище должно поднимать своё в тесте, без моков.

---

### 10. `@EnableCaching` + Lettuce + `RedisCacheManager` с TTL

**Что сделать:**

1. Зависимость: `implementation("org.springframework.boot:spring-boot-starter-data-redis")`.
2. Конфиг `CacheConfig`:
```kotlin
@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): CacheManager {
        val config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeValuesWith(
                SerializationPair.fromSerializer(GenericJackson2JsonRedisSerializer())
            )
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build()
    }
}
```
3. В `application.properties`:
```
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

**Зачем:** Spring Cache — это **абстракция**: код пишется в терминах `@Cacheable`/`@CacheEvict`, а конкретный backend (Redis, Caffeine, EhCache) меняется одной зависимостью. На практике это полезно: локально можно держать Caffeine для скорости, в проде — Redis для шаринга между инстансами. Принципиально важно научиться отделять **кешируется ли** от **где кешируется**.

JSON-сериализация (`GenericJackson2JsonRedisSerializer`) вместо JDK — потому что JDK-сериализация хрупкая: добавишь поле в DTO, и все старые значения в кеше превратятся в `InvalidClassException`. JSON это терпит.

---

### 11. `@Cacheable("books")` на `getBookById`, `@CacheEvict` на mutating ops

**Что сделать:**

1. На `LibraryService.getBookById(Long id)` — `@Cacheable("books")`. Ключ по умолчанию — `id`.
2. На `update`, `delete`, `reserve`, `return` — `@CacheEvict(value = "books", key = "#id")`.
3. Создание новой книги (`save`) — кеш не трогать (новой книги в кеше всё равно нет).
4. Все аннотации применяются на **публичные методы**, вызываемые **извне бина** — иначе Spring AOP не сработает (это базовая ловушка Spring AOP, помнишь её из Part 1).

**Зачем:** Это первая инвалидация кеша в твоей жизни. Cache invalidation — одна из «двух главных проблем computer science» (вторая — naming). Кажется простым: «обновил — почисти». На практике сразу всплывает:

- `@CacheEvict` срабатывает **после** возврата метода. Если транзакция откатилась — что произошло раньше: эвикт или откат? (ответ: эвикт **уже** случился, потому что Spring Cache не интегрируется с транзакциями БД по умолчанию). Это нормально для текущей задачи: stale кеш быстро уйдёт по TTL.
- Если параллельный читатель прочитал кеш **до** эвикта, а пишущий уже обновил БД — читатель получит stale данные. На реальном проекте это решают либо через TTL, либо через cache-aside с явной инвалидацией, либо через write-through. Каждый вариант — компромисс.

**Ловушка:** Не пиши `@Cacheable` на методы, которые возвращают `null` — Spring закеширует `null`, и следующий запрос с тем же id даст `null`, даже если книга появилась. Используй `unless = "#result == null"`.

---

### 12. Тест: первый `GET` идёт в БД, второй — нет

**Что сделать:**

1. Тест от `BaseRedisIntegrationTest` + Testcontainers Postgres.
2. Через `@SpyBean BooksRepository` отслеживать вызовы `findById`.
3. Сценарий: создать книгу → почистить spy → `getBookById(id)` дважды → `verify(repo, times(1)).findById(id)`.
4. Дополнительно: вызвать `update(...)` → `getBookById(id)` → `verify(repo, times(2)).findById(id)` (после update кеш должен быть инвалидирован).

**Зачем:** Кеш без теста — это «надеюсь, работает». Spy на репозитории показывает **в коде**, что кеш реально срезает походы в БД. Это ровно то, что ты хочешь увидеть в проде через метрики (см. задание 16).

---

### 13. Подключить Redisson

**Что сделать:**

1. Зависимость: `implementation("org.redisson:redisson-spring-boot-starter:3.27.x")`.
2. Конфиг `RedissonClient` (single-server mode, `redis://localhost:6379`).
3. Убедиться, что `RedissonClient` инжектится и `redissonClient.getKeys().count()` работает.

**Зачем:** Redisson — это не «ещё один Redis-клиент». Это библиотека высокоуровневых **распределённых примитивов**: `RLock` (distributed lock), `RSemaphore`, `RAtomicLong`, distributed maps и другие. Реализованы по правильным алгоритмам, проверены боем, поддерживают auto-renewal, watchdog, fail-over.

Можно ли сделать lock на «голом» Redis через `SET NX EX`? Можно. Будет ли он правильным? Скорее всего нет — нужно учесть кучу edge cases (разблокировка не своим клиентом, утечка локов при fail-over, watchdog). Redisson это уже сделал. Не пиши свой Redlock — это путь читать Мартина Клеппманна и спорить с Антирезом.

---

### 14. `RLock` на `reserve(id, user)` с ключом `lock:book:{id}`

**Что сделать:**

1. В `LibraryService.reserve(...)` обернуть критическую секцию в `RLock`:

```java
RLock lock = redisson.getLock("lock:book:" + id);
boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
if (!acquired) {
    throw new BookReservationConflictException(id);
}
try {
    // существующая логика: прочитать книгу, проверить reserved, обновить
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

2. Параметры: `waitTime = 5s` — сколько ждать освобождения, `leaseTime = 30s` — на сколько максимум удерживается. Если процесс умрёт с захваченным локом — Redis сам отпустит через `leaseTime`.
3. Обработать `InterruptedException` корректно (восстановить флаг прерывания).

**Зачем:** Это самый практичный пример того, **почему `synchronized` — не решение**. На одной JVM `synchronized` работает. Запусти два инстанса `library-app` за nginx — `synchronized` блокирует только внутри одной JVM, между процессами координации нет. Двое одновременно успешно проходят `synchronized`-блок, и обе брони проходят.

`RLock` живёт в Redis — то есть в общем для всех инстансов месте. Поэтому он работает в распределённой среде. Это фундаментальное различие, которое нужно прочувствовать.

**Ловушка:** `tryLock(5, 30, ...)` — не путай порядок аргументов. `waitTime` потом `leaseTime`. Если `waitTime > leaseTime` — это бессмысленно (поток ждёт дольше, чем кто-то может удерживать). Если `leaseTime` слишком мал и критическая секция не успевает — лок отпустится во время работы, второй поток зайдёт, и инвариант сломается. Подбирай `leaseTime` с запасом 3–5× от ожидаемой работы.

---

### 15. Тест на параллелизм: 50 потоков → 1 успех, 49 × 409

**Что сделать:**

1. Тест от `BaseRedisIntegrationTest`.
2. Создать книгу. Открыть `ExecutorService.newFixedThreadPool(50)`.
3. `CountDownLatch startLatch(1)` — все потоки ждут, пока тест не отпустит. После этого — одновременная атака.
4. Каждый поток делает `reserve(bookId, "user-N")`. Считаем успехи (вернувшие OK) и `BookReservedException`.
5. Assert: ровно `1` успех, `49` исключений.
6. Запустить тест **без** `RLock` — он должен падать (показать на красном тесте, в чём проблема).
7. Добавить `RLock` — тест зелёный.

**Зачем:** Это самый ценный тест в этой части. Большинство джунов **никогда** не пишут тестов на параллелизм, потому что они интуитивно сложные. Если ты освоишь паттерн «`CountDownLatch` + `ExecutorService` + assert на количество успехов» — это сразу +1 уровень в концурренси на собесе.

И главное — этот тест **виден глазами**: красный без блокировки, зелёный с блокировкой. Ты сам собственноручно увидишь, как distributed lock спасает инвариант.

**Подсказка:** Используй `Future<Boolean>` для каждого потока, потом собери их. Не забывай про timeout — если что-то заклинило, тест должен падать за разумное время, а не висеть.

---

### 16. Метрики кеша через actuator

**Что сделать:**

1. В `application.properties` включить:
```
management.endpoints.web.exposure.include=health,metrics,caches
```
2. Добавить `RedisCacheManager` в `MeterBinder` — Spring Boot делает это автоматически, если в classpath есть Micrometer (он уже там через actuator).
3. Подёргать `/actuator/metrics/cache.gets?tag=cache:books&tag=result:hit` и `result:miss` — увидеть счётчики.
4. Опционально: запустить локально, побить нагрузку через `ab` или `wrk` на `GET /books/{id}`, посмотреть hit ratio.

**Зачем:** Кеш без наблюдаемости — это слепое пятно. Hit ratio < 50% означает, что TTL слишком короткий, или ключи плохо подобраны, или кеш вообще не работает. Без метрик ты этого не увидишь. Привычка «всё, что добавил в систему, должно быть видно в метриках» — это уже senior-уровень мышления.

---

## Часть C: Kafka — события и outbox-паттерн

### Почему просто `kafkaTemplate.send` — это баг

К этому моменту у нас есть `library-app` с поиском, кешем и блокировками. Сюжет: появляется второй сервис — `library-notifier`, — который должен реагировать на бронирование (в реальности — слать email/push/sms; в учебной задаче — просто логировать). Возникает вопрос: **как** доставлять событие из `library-app` в `library-notifier`.

Можно сделать HTTP-вызов: `library-app` дёргает `library-notifier` REST-методом. Это плохо по двум причинам:
- **Жёсткая связность.** `library-app` теперь обязан знать, где находится notifier, какой у него протокол, и обрабатывать его ошибки. Появится третий потребитель уведомлений — придётся менять `library-app`.
- **Хрупкость.** Notifier лёг — `library-app` либо тоже падает, либо событие теряется. Связь синхронная.

Правильный путь — **шина событий**. `library-app` публикует факт «книга забронирована» в Kafka. Notifier (и любой другой потребитель) подписан на этот топик и читает в своём темпе. Сервисы развязаны.

Но как только пытаешься написать «в лоб» — натыкаешься на главную инженерную проблему распределённых систем.

```java
@Transactional
public void reserve(Long id, String user) {
    Book book = repo.findById(id).orElseThrow();
    book.setReserved(user);
    repo.save(book);                                    // commit Postgres
    kafkaTemplate.send("books", new BookReservedEvent(...)); // отправка
}
```

Этот код **сломан**. Четыре сценария, каждый из которых ломает целостность:

1. **Postgres закоммитился, Kafka недоступна.** Бронь в БД есть, события нет. Notifier никогда не узнает. Уведомление не уйдёт.
2. **Kafka приняла, Postgres откатился** (например, на коммите упало уникальное ограничение, или сеть моргнула в момент `commit`). Событие есть, брони нет. Notifier шлёт уведомление о брони, которой не существует.
3. **Postgres закоммитился, Kafka приняла, но `ack` не дошёл из-за сетевой ошибки.** `kafkaTemplate.send` ретраит — сообщение публикуется дважды. Дубликат.
4. **Под нагрузкой `kafkaTemplate.send` блокирует поток**, удерживая БД-соединение в транзакции дольше, чем нужно. Деградация на ровном месте.

Это не теоретические проблемы. Это то, что ловят в проде, и потом неделю разбирают логи. Решение существует и стандартизировано — **outbox-паттерн**.

**Идея outbox:** записать «нужно опубликовать событие» в **ту же транзакцию**, что и бизнес-данные. Не публиковать в Kafka из бизнес-метода вообще. Отдельный фоновый процесс читает таблицу `outbox`, публикует, помечает отправленным. Гарантия: либо «и в БД, и в outbox» закоммитилось, либо ничего. Дубликаты при ретраях ловит **идемпотентный консьюмер**.

В этой части мы пройдём через весь цикл: сначала напишем сломанную версию, чтобы прочувствовать проблему. Потом починим её outbox'ом. Потом добавим идемпотентность и DLQ.

---

### 17. Создать модуль `library-events`

**Что сделать:**

1. В `settings.gradle.kts` добавить `include("library-events")`.
2. Создать каталог `library-events/` с `build.gradle.kts`:
```kotlin
plugins { `java-library` }
dependencies {
    api("com.fasterxml.jackson.core:jackson-annotations")
}
```
3. Никаких Spring-зависимостей. Чистые POJO.
4. Создать классы (records):
```java
public sealed interface LibraryEvent permits BookReservedEvent, BookReturnedEvent {
    UUID eventId();
    Instant occurredAt();
    Long bookId();
}

public record BookReservedEvent(UUID eventId, Instant occurredAt, Long bookId, String user) implements LibraryEvent {}
public record BookReturnedEvent(UUID eventId, Instant occurredAt, Long bookId, String user) implements LibraryEvent {}
```
5. `library-app` и `library-notifier` подключают `library-events` как `implementation(project(":library-events"))`.

**Зачем:** Это та же история, что и `library-api` в Part 2: **контракт нужен потребителю**. Если положить event-классы внутрь `library-app` — `library-notifier` будет вынужден тащить весь сервер ради двух POJO. Плюс: версионирование контракта событий — отдельная тема (Schema Registry, Avro), и наличие выделенного модуля — первый шаг к ней.

Sealed interface + records — современная Java-way выразить closed set вариантов событий. Удобно для `switch` по типам в консьюмере.

**Подсказка:** Не клади сюда логику. Только данные и (минимум) валидация. Это контракт, а не библиотека.

---

### 18. Создать модуль `library-notifier`

**Что сделать:**

1. `settings.gradle.kts`: `include("library-notifier")`.
2. `library-notifier/build.gradle.kts`:
```kotlin
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}
dependencies {
    implementation(project(":library-events"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.kafka:spring-kafka")
}
```
3. Класс `LibraryNotifierApplication` с `@SpringBootApplication`.
4. `application.properties`:
```
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=library-notifier
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=ru.athena.library_demo.events
server.port=8081
```
5. Заглушка-консьюмер с `@KafkaListener("books.events")`, который пока просто логирует входящее.

**Зачем:** Минимальный сервис намеренно — чтобы было видно, что Kafka-консьюмер ничего не требует, кроме самого Spring Kafka и контракта событий. Никакого web-сервера, никакого JPA, никакого JPA. Это именно «маленький отдельный процесс» в духе микросервисов.

---

### 19. Поднять Kafka в `docker-compose` + `kafka-ui`

**Что сделать:**

1. В `docker-compose.yml` (KRaft mode, без Zookeeper):

```yaml
kafka:
  image: bitnami/kafka:3.7
  ports: ["9092:9092"]
  environment:
    - KAFKA_CFG_NODE_ID=0
    - KAFKA_CFG_PROCESS_ROLES=controller,broker
    - KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093
    - KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092
    - KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER
    - KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=0@kafka:9093
    - KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
  healthcheck:
    test: ["CMD-SHELL", "kafka-broker-api-versions.sh --bootstrap-server localhost:9092"]
    interval: 10s
    timeout: 5s
    retries: 10

kafka-ui:
  image: provectuslabs/kafka-ui:latest
  ports: ["8090:8080"]
  environment:
    - KAFKA_CLUSTERS_0_NAME=local
    - KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=kafka:9092
  depends_on: { kafka: { condition: service_healthy } }
```

2. Открыть `http://localhost:8090` — UI должен показать кластер.
3. Создать топик `books.events` с 3 партициями (через UI или `kafka-topics.sh`).

**Зачем:** KRaft mode — современный режим Kafka без Zookeeper (deprecated с 3.5). В проде сейчас разворачивают именно KRaft. Освой сразу.

`kafka-ui` от Provectus — must-have для отладки. Показывает топики, партиции, оффсеты, сообщения, consumer groups. На любом проекте с Kafka он или его аналог стоит. Без него отлаживать «куда делось сообщение» — пытка.

**Подсказка:** В KRaft нужно следить, чтобы `node.id` был уникален в кластере. Для одного брокера — `0` нормально.

---

### 20. Наивная реализация: `kafkaTemplate.send` внутри `@Transactional` (намеренно сломано)

**Что сделать:**

1. В `library-app/build.gradle.kts`: `implementation("org.springframework.kafka:spring-kafka")`.
2. Конфиг `KafkaProducerConfig` с JSON-сериализацией.
3. В `LibraryService.reserve(...)`: после `repo.save(book)` вызвать `kafkaTemplate.send("books.events", new BookReservedEvent(...))`. Аналогично для `return`.
4. Запустить руками: поднять `docker-compose`, забронировать книгу, увидеть событие в `kafka-ui` и в логе notifier'а.

**Зачем:** Это **намеренно сломанный** шаг. Сейчас всё работает на счастливом пути. Задача следующего шага — найти, где он ломается.

Не пропускай это задание. Без него outbox в задании 22 будет восприниматься как «решение в поисках проблемы». А он — решение **очень конкретной** проблемы, и эту проблему нужно сначала прочувствовать.

---

### 21. Тест, ловящий проблему

**Что сделать:**

1. Интеграционный тест с Testcontainers: Postgres + Kafka.
2. Сценарий:
   - Поднять контейнеры, забронировать книгу — проверить, что событие пришло (счастливый путь работает).
   - **Остановить Kafka контейнер.**
   - Забронировать вторую книгу.
   - Проверить: бронь в БД **есть**, событие в Kafka **отсутствует** (после рестарта брокера ничего не догнало).
3. Тест должен **падать** на этом ассерте — он показывает баг.

**Зачем:** Этот тест — твой материальный доказательство, что наивная реализация ломается. Без него ты можешь поверить мне на слово, и забыть. С ним — ты собственноручно увидел красное и знаешь, **что именно** сломано.

**Подсказка:** Останавливать Kafka можно через Testcontainers API: `kafka.stop()`. Старт обратно — `kafka.start()`, но гарантировать, что брокер «запомнил» что-то отправленное во время остановки, нельзя — он не получал сообщений вообще.

**Альтернатива:** если возиться с остановкой контейнера сложно — можно подменить bootstrap-servers на несуществующий хост в момент бронирования. Эффект тот же.

---

### 22. Рефакторинг: таблица `outbox` + Flyway-миграция

**Что сделать:**

1. Flyway-миграция `Vx__create_outbox.sql`:
```sql
CREATE TABLE outbox (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(64) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    payload         TEXT NOT NULL,         -- JSON
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    status          VARCHAR(16) NOT NULL,  -- PENDING, SENT, FAILED
    sent_at         TIMESTAMP NULL
);
CREATE INDEX outbox_pending_idx ON outbox(status, created_at) WHERE status = 'PENDING';
```
2. Entity `OutboxRecord` + `OutboxRepository` с методом `findTop100ByStatusOrderByCreatedAt(Status.PENDING)`.
3. Сервис `OutboxWriter`: метод `save(LibraryEvent event)` — сериализует в JSON и пишет запись со статусом `PENDING`.
4. В `LibraryService.reserve(...)` **убрать** `kafkaTemplate.send`. Вместо него вызвать `outboxWriter.save(event)` **внутри той же транзакции**, что и `repo.save(book)`.

**Зачем:** Это и есть outbox. Запись в `outbox` и запись в `books` — в одной транзакции Postgres. Atomicity гарантирует БД: либо обе строки появились, либо ни одной. Никакой Kafka в бизнес-методе.

**Ловушка:** Не пропускай частичный индекс `WHERE status = 'PENDING'`. Иначе при миллионе обработанных записей publisher будет каждые секунду пробегать всю таблицу.

---

### 23. Фоновый publisher: `@Scheduled` + Kafka

**Что сделать:**

1. Класс `OutboxPublisher`:
```java
@Component
class OutboxPublisher {
    @Scheduled(fixedDelayString = "${outbox.poll-interval:1000}")
    public void publishPending() {
        var batch = outboxRepo.findTop100ByStatusOrderByCreatedAt(PENDING);
        for (var record : batch) {
            try {
                kafkaTemplate.send("books.events", record.aggregateId(), record.payload()).get();
                record.markSent();
                outboxRepo.save(record);
            } catch (Exception e) {
                // лог, оставить PENDING — на следующей итерации перепошлёт
            }
        }
    }
}
```
2. В `LibraryDemoApplication`: `@EnableScheduling`.
3. Опционально: упомянуть **ShedLock** — если у тебя несколько инстансов `library-app`, без него все будут читать outbox параллельно и слать дубликаты. Не реализуй, просто отметь в комментарии.

**Зачем:** Это и есть relay из outbox-паттерна. Бизнес-методы пишут в outbox, фон сливает в Kafka. Сетевые ошибки — переживаем (запись остаётся `PENDING`, повторим). Брокер недоступен — переживаем (повторим, когда вернётся). Дубликаты при ретраях — **возможны**, и это окей: их ловит идемпотентный консьюмер (задание 25).

**Ловушка:** `kafkaTemplate.send(...).get()` — блокирующий, чтобы дождаться `ack`. Без `.get()` мы можем пометить `SENT` до подтверждения брокера и потерять сообщение, если producer не успел отправить до краша. С `.get()` теряем в пропускной способности, но это правильный trade-off — outbox не про throughput, а про надёжность.

---

### 24. Повтор теста из #21 — теперь зелёный

**Что сделать:**

1. Тот же сценарий: остановить Kafka, забронировать, дождаться публикации.
2. Стартовать Kafka обратно.
3. **Подождать** (`Awaitility`) — publisher должен догнать события и notifier их получить.
4. Тест зелёный.

**Зачем:** Замыкание цикла «увидел проблему → решил → проверил, что решение работает». Это самый дидактически ценный момент во всей третьей части.

---

### 25. Идемпотентный консьюмер: таблица `processed_events`

**Что сделать:**

1. В `library-notifier` — Flyway-миграция (или просто in-memory через H2 для простоты, но лучше Postgres для консистентности):
```sql
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```
2. В `@KafkaListener`:
```java
@KafkaListener(topics = "books.events")
public void handle(LibraryEvent event) {
    if (processedRepo.existsById(event.eventId())) {
        log.info("duplicate event {}, skipping", event.eventId());
        return;
    }
    processedRepo.save(new ProcessedEvent(event.eventId()));
    // обработка: лог "уведомление пользователю user о книге bookId"
}
```
3. Тест: послать одно и то же событие дважды → обработано один раз.

**Зачем:** Outbox + `kafkaTemplate.send(...).get()` даёт **at-least-once**. Это значит, что иногда сообщение приходит дважды (network blip между `send` и `ack`, ребаланс consumer group). Это нормально — гарантировать exactly-once без транзакций Kafka сложно и дорого. Гораздо проще сделать консьюмер **идемпотентным**: каждое событие имеет уникальный `eventId`, дубликаты игнорируются.

Это и есть промышленная истина: **at-least-once + идемпотентность = exactly-once на уровне эффекта.**

**Ловушка:** Запись в `processed_events` и собственно обработка должны быть в **одной транзакции** (или обработка должна быть идемпотентна сама по себе). Иначе: успели записать `processed_events`, но упали до отправки уведомления — событие потеряется по «соображениям дедупликации».

---

### 26. DLQ для ядовитых сообщений

**Что сделать:**

1. В `library-notifier` — конфиг error handler:
```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template);
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
}
```
2. По умолчанию Spring Kafka пошлёт ядовитое сообщение в топик `<original>.DLT` (например, `books.events.DLT`).
3. Тест: отправить в `books.events` невалидный JSON → проверить, что после 3 попыток сообщение оказалось в `books.events.DLT`, а основной консьюмер продолжает работать (не зацикливается).

**Зачем:** Без DLQ ядовитое сообщение (десериализация падает) блокирует всю партицию: консьюмер падает на нём, ребаланс возвращает его обратно, бесконечный цикл. Это классический способ положить production. DLQ переносит проблемное сообщение в отдельный топик, основной поток продолжает работать, разработчик потом разбирается с DLT-топиком отдельно.

**Подсказка:** На реальном проекте смотри в DLT-топик регулярно (метрика `kafka.consumer.records-consumed-total{topic="*.DLT"}` > 0 — повод для алерта).

---

### 27. E2E-тест через Testcontainers `Network`

**Что сделать:**

1. Тест с общей сетью контейнеров:
```java
@Container static Network net = Network.newNetwork();
@Container static PostgreSQLContainer<?> postgres = ...withNetwork(net);
@Container static KafkaContainer kafka = new KafkaContainer(...).withNetwork(net);
```
2. Поднять `library-app` (через `@SpringBootTest(webEnvironment = RANDOM_PORT)`) и `library-notifier` (через отдельный `SpringApplication.run` с указанием порта) в одном тесте.
3. Сценарий:
   - HTTP-вызов на `library-app`: `POST /books/{id}/reserve`.
   - Ожидание (`Awaitility`): запись `processed_events` в notifier появилась.
4. Дополнительно: остановить `kafka.stop()`, сделать `reserve`, стартовать `kafka.start()`, проверить что событие догнало.

**Зачем:** Это **истинный** интеграционный тест — два сервиса, четыре контейнера, реальная сеть. Если он проходит — система действительно работает end-to-end. Не существует более убедительной проверки.

**Ловушка:** Запуск двух Spring-контекстов в одном JVM — занятие нетривиальное. Если попадёшь в classloader-конфликт — это ожидаемо. Альтернатива: собрать `library-notifier` в Docker-образ и запустить через `GenericContainer` (это сложнее, но чище). Выбирай по вкусу — на учебной задаче оба варианта приемлемы.

---

## Бонус (необязательно)

### 28. Schema Registry + Avro: версионирование контракта событий

**Что сделать:** Поднять Confluent Schema Registry. Заменить JSON-сериализацию на Avro. Описать схему `BookReservedEvent.avsc`. Опубликовать в registry. Включить совместимость `BACKWARD`. Попробовать сломать совместимость (удалить поле) — registry должен отказать.

**Зачем:** В JSON ничто не мешает тебе переименовать поле `user` → `userName` — продюсер обновится, консьюмер сломается на десериализации в рантайме. Schema Registry + Avro делают контракт **проверяемым на этапе публикации**: новая схема не пройдёт, если несовместима со старой по выбранному правилу.

Это та же эволюция, что произошла с REST → OpenAPI: «договор на словах» → «договор, который проверяет компилятор/registry».

---

### 29. Debezium / CDC: альтернатива outbox

**Что сделать:** Прочитать про Debezium. Понять, чем отличается outbox-паттерн от чтения WAL Postgres напрямую. Опционально — поднять Debezium Connect, настроить коннектор на таблицу `books`.

**Зачем:** Outbox — application-level. Ты пишешь код, который пишет в `outbox`. Debezium — infrastructure-level. Он читает Postgres write-ahead log и публикует **любые** изменения в Kafka, без участия приложения. Преимущество: ноль инвазивности (приложение не знает о Kafka вообще). Недостаток: новый компонент в инфраструктуре, требующий поддержки.

В реальных проектах встречаются оба подхода. Outbox — когда удобно явно описывать события (доменно-значимые факты). CDC — когда нужно «вынести вообще все изменения» в потоковую обработку.

---

## Итог: что ты освоишь

| Навык | Где применяется |
|---|---|
| Полнотекстовый поиск с Elasticsearch (Java Client + Query DSL) | Любой проект с поиском по каталогу/документам |
| Маппинг ES, разница `text`/`keyword`, русский анализатор | Любой ES-проект на русскоязычной аудитории |
| Синхронизация двух хранилищ (Postgres + ES) через application events | Каждая система с read-моделью |
| Полная переиндексация для восстановления read-модели | Любой проект с CQRS / read-моделями |
| Spring Cache + Redis с осмысленными TTL и инвалидацией | Любой бэкенд с горячими чтениями |
| Распределённая блокировка через Redisson (RLock) | Любая многоинстансовая система с критическими секциями |
| Понимание, почему `synchronized` не работает в распределённой среде | Фундаментальное знание для любого распределённого софта |
| Тесты на параллелизм через `ExecutorService` + `CountDownLatch` | Тестирование любого concurrency-кода |
| Метрики кеша через actuator + Micrometer | Production-наблюдаемость в любом сервисе |
| Kafka producer / consumer, JSON-сериализация, consumer groups | Любой проект с шиной событий |
| **Outbox-паттерн** — атомарная публикация с бизнес-транзакцией | Любой сервис, который публикует события |
| **Идемпотентный консьюмер** через event-id и dedup-таблицу | Каждый Kafka-консьюмер в проде |
| **DLQ** для ядовитых сообщений | Любой Kafka-консьюмер на нагрузке |
| Multi-container E2E через Testcontainers `Network` | Тестирование микросервисных взаимодействий |
| docker-compose с 4+ backing-services и 2+ приложениями | Локальная разработка любого микросервисного проекта |

Эти навыки — не про конкретные библиотеки. Они про **классы проблем**, которые повторяются в каждом распределённом проекте, и про стандартные паттерны, которыми эти проблемы решаются. Освоив их здесь, ты их узнаешь везде — на следующей работе они будут называться так же.

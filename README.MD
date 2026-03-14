# Задание: Library Service (Spring Boot)

Собрать REST-сервис "Библиотека" с книгами и бронированием.

## Функционал

### CRUD для книг

- `POST /books`
- `GET /books/{id}`
- `GET /books?author=&genre=&yearFrom=&yearTo=`
- `PUT /books/{id}`
- `DELETE /books/{id}`

### Бронирование

- `POST /books/{id}/reserve` — кто-то бронирует
- `POST /books/{id}/return` — возврат

**Правила:**
- Нельзя бронировать уже забронированную книгу.
- Нельзя удалить забронированную книгу.

## Технические требования

- Spring Boot 3 + Java 17
- Spring Web, Spring Data JPA, Validation
- БД: PostgreSQL (или H2 для упрощения)
- Слои: `controller` -> `service` -> `repository`
- DTO + mapper (не отдавать entity наружу)
- Глобальная обработка ошибок (`@ControllerAdvice`)
- Логирование основных операций

## Обязательные best practices

- `@Transactional` на сервисном слое
- Пагинация для списка книг (`page`, `size`)
- Сортировка (`sort=title,asc`)
- Валидация входных данных (`@NotBlank`, `@Size`, `@Positive`)
- Правильные HTTP статусы (`201`, `200`, `204`, `400`, `404`, `409`)

## Тесты

- Unit-тесты сервисов (JUnit5 + Mockito), минимум 10
- Интеграционные тесты контроллера (`@SpringBootTest` / `MockMvc`), минимум 5
- Проверить конфликт бронирования (`409 Conflict`)

## Плюс за advanced

- Spring Security + роль `LIBRARIAN` для create/update/delete
- Flyway миграции
- Actuator (`health`, `metrics`)
- Docker Compose (app + postgres)

## Что сдать

- Git-репозиторий
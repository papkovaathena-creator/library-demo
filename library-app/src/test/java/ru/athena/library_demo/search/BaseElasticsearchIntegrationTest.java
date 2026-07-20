package ru.athena.library_demo.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.io.IOException;

@SpringBootTest
@ActiveProfiles("postgres")
public abstract class BaseElasticsearchIntegrationTest {

    static final ElasticsearchContainer elasticsearchContainer;
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        elasticsearchContainer = new ElasticsearchContainer(
                "docker.elastic.co/elasticsearch/elasticsearch:8.13.0")
                .withEnv("xpack.security.enabled", "false");
        POSTGRES = new PostgreSQLContainer<>("postgres:18.3");
        elasticsearchContainer.start();
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris",
                () -> "http://" + elasticsearchContainer.getHttpHostAddress());
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected ElasticsearchClient client;

    @BeforeEach
    void clearBooksIndex() throws IOException {
        if (client.indices().exists(e -> e.index("books")).value()) {
            client.deleteByQuery(d -> d.index("books").query(q -> q.matchAll(m -> m)));
            client.indices().refresh(r -> r.index("books"));
        }
    }

    protected void refresh() throws IOException {
        client.indices().refresh(r -> r.index("books"));
    }
}

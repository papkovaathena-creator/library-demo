package ru.athena.library_demo.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

@SpringBootTest
@ActiveProfiles("hsqldb")
public abstract class BaseElasticsearchIntegrationTest {


    @Container
    static final ElasticsearchContainer elasticsearchContainer;

    static {
        elasticsearchContainer = new ElasticsearchContainer(
                "docker.elastic.co/elasticsearch/elasticsearch:8.13.0")
                .withEnv("xpack.security.enabled", "false");
        elasticsearchContainer.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris",
                () -> "http://" + elasticsearchContainer.getHttpHostAddress());
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

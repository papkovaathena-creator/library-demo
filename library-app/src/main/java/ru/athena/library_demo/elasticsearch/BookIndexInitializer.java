package ru.athena.library_demo.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import ru.athena.library_demo.service.LibraryService;

import java.io.IOException;

@Component
public class BookIndexInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(LibraryService.class);
    private final ElasticsearchClient client;

    @Autowired
    public BookIndexInitializer(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("BookIndexInitializer started -- checking for index.");
        try {
            if (client.indices().exists(d -> d.index("books")).value()) {
                log.info("Index \"books\" exists.");
            } else {
                log.info("Index \"books\" doesn't exist.");
                // This feels awkward. Might be better to create it from JSON file?
                client.indices().create(
                        createIndexBuilder -> createIndexBuilder
                                .index("books")
                                .mappings(m -> m
                                        .properties("id", p->p.keyword(k->k))
                                        .properties("name",p->p
                                                .text(t->t.analyzer("russian")))
                                        .properties("author",p->p
                                                .text(t->t.analyzer("russian")))
                                        .properties("genre",p->p.keyword(k->k))
                                        .properties("releaseDate",p->p.date(d->d
                                                .format("yyyy-MM-dd")))
                                        .properties("reserved",p->p.keyword(k->k)))
                );
                log.info("Index \"books\" created.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

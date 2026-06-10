package ru.athena.library_demo.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import ru.athena.library_demo.persistence.entity.Book;
import ru.athena.library_demo.persistence.repository.BooksRepository;
import ru.athena.library_demo.service.LibraryService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class BookIndexInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(LibraryService.class);
    private final ElasticsearchClient client;
    private final BooksRepository booksRepository;

    @Autowired
    public BookIndexInitializer(ElasticsearchClient client, BooksRepository booksRepository) {
        this.client = client;
        this.booksRepository = booksRepository;
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

                List<BookDocument> bookDocuments = pullBooksFromRepository();

                BulkRequest.Builder br = new BulkRequest.Builder();

                for (BookDocument bookDocument : bookDocuments) {
                    br.operations(op -> op
                            .index(idx -> idx
                                    .index("books")
                                    .id(bookDocument.getId().toString())
                                    .document(bookDocument)));
                }

                BulkResponse bulkResponse = client.bulk(br.build());

                log.info("Books have been indexed.");

                SearchResponse<BookDocument> searchResponse = client.search(s -> s
                        .index("books")
                        .query(q -> q
                                .match(t -> t
                                        .field("genre")
                                        .query("Epic")))
                        ,BookDocument.class);

                SearchRequest.Builder srb = new SearchRequest.Builder();
                srb.index("books");
                srb.query(q -> q
                        .match(t -> t
                                .field("genre")
                                .query("Epic")));

                log.info(srb.build().toString());

                log.info(searchResponse.toString());
                log.info("Amount of results - " + searchResponse.hits().total().value());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<BookDocument> pullBooksFromRepository() {
        List<Book> books = booksRepository.findAll();
        return books.stream().map(this::map).collect(Collectors.toList());
    }

    private BookDocument map(Book book) {
        return new BookDocument(book.getId(),book.getName(),book.getAuthor(),book.getGenre(),book.getReleaseDate().toString(),book.getReserved());
    }
}

package ru.athena.library_demo.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ru.athena.library_demo.persistence.entity.Book;
import ru.athena.library_demo.persistence.repository.BooksRepository;
import ru.athena.library_demo.service.LibraryService;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class BookIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(LibraryService.class);
    public static final String BOOKS_INDEX = "books";
    private final ElasticsearchClient client;
    private final BooksRepository booksRepository;

    @Autowired
    public BookIndexInitializer(ElasticsearchClient client, BooksRepository booksRepository) {
        this.client = client;
        this.booksRepository = booksRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("BookIndexInitializer started -- checking for index.");
        try {
            if (client.indices().exists(d -> d.index(BOOKS_INDEX)).value()) {
                log.info("Index {} exists.", BOOKS_INDEX);
            } else {
                log.info("Index {} doesn't exist.", BOOKS_INDEX);
                try (InputStream mapping = new ClassPathResource("elasticsearch/books-index.json").getInputStream()) {
                    client.indices().create(c -> c.index(BOOKS_INDEX).withJson(mapping));
                }
                log.info("Index {} created.", BOOKS_INDEX);

                List<BookDocument> bookDocuments = pullBooksFromRepository();

                BulkRequest.Builder br = new BulkRequest.Builder();

                for (BookDocument bookDocument : bookDocuments) {
                    br.operations(op -> op
                            .index(idx -> idx
                                    .index(BOOKS_INDEX)
                                    .id(bookDocument.getId().toString())
                                    .document(bookDocument)));
                }

                BulkResponse bulkResponse = client.bulk(br.build());

                log.info("Books have been indexed.");
            }
        } catch (IOException e) {
            log.warn("Wasn't able to create index '{}' on startup (ES unavailable?): {}",
                    BOOKS_INDEX, e.getMessage());
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

package ru.athena.library_demo.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.athena.library_demo.persistence.entity.Book;
import ru.athena.library_demo.persistence.repository.BooksRepository;

import java.io.IOException;

@Component
public class BookIndexUpdater {

    private static final Logger log = LoggerFactory.getLogger(BookIndexUpdater.class);
    private final ElasticsearchClient client;
    private final BooksRepository repository;

    public BookIndexUpdater(ElasticsearchClient client, BooksRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookChanged(BookChangedEvent event) {
        try {
            log.info("Updating index {}", event.getBookId());
            String id = String.valueOf(event.getBookId());
            if (event.getType() == BookChangedEvent.ChangeType.DELETE) {
                client.delete(d -> d.index(BookIndexInitializer.BOOKS_INDEX).id(id));
                return;
            }
            repository.findById(event.getBookId()).ifPresent(book -> indexBook(id, book));
        } catch (Exception e) {
            log.warn("Was unable to update index {}: {}", event.getBookId(), e.getMessage());
        }
    }

    private void indexBook(String id, Book book) {
        BookDocument doc = new BookDocument(book.getId(),
                book.getName(),
                book.getAuthor(),
                book.getGenre(),
                book.getReleaseDate() != null ? book.getReleaseDate().toString() : null,
                book.getReserved());
        try {
            client.index(i -> i.index(BookIndexInitializer.BOOKS_INDEX)
                    .id(id).document(doc));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

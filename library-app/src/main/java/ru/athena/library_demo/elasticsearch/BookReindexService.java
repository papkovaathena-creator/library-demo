package ru.athena.library_demo.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import ru.athena.library_demo.persistence.entity.Book;
import ru.athena.library_demo.persistence.repository.BooksRepository;

import java.io.IOException;
import java.io.InputStream;

import static ru.athena.library_demo.elasticsearch.BookIndexInitializer.BOOKS_INDEX;

@Component
public class BookReindexService {

    private static final int BATCH_SIZE = 500;
    private static final Logger log = LoggerFactory.getLogger(BookReindexService.class);

    private final ElasticsearchClient client;
    private final BooksRepository booksRepository;

    public BookReindexService(ElasticsearchClient client, BooksRepository booksRepository) {
        this.client = client;
        this.booksRepository = booksRepository;
    }

    public void reindex() {
        try {
            recreateIndex();
            int pageNumber = 0;
            Page<Book> page;
            do {
                page = booksRepository.findAll(PageRequest.of(pageNumber, BATCH_SIZE));
                if (page.hasContent()) {
                    bulkIndex(page);
                }
                pageNumber++;
            } while (page.hasNext());
            log.info("Reindexing complete.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void bulkIndex(Page<Book> page) throws IOException {
        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (Book book : page) {
            BookDocument doc = toDocument(book);
            String id = String.valueOf(book.getId());
            bulk.operations(op -> op.index(idx -> idx
                    .index(BookIndexInitializer.BOOKS_INDEX)
                    .id(id)
                    .document(doc)));
        }
        client.bulk(bulk.build());
    }

    private BookDocument toDocument(Book book) {
        BookDocument doc = new BookDocument();
        doc.setId(book.getId());
        doc.setName(book.getName());
        doc.setAuthor(book.getAuthor());
        doc.setGenre(book.getGenre());
        doc.setReleaseDate(book.getReleaseDate() != null ? book.getReleaseDate().toString() : null);
        doc.setReservedBy(book.getReserved());
        return doc;
    }
    private void recreateIndex() throws IOException {
        if (client.indices().exists(d -> d.index(BOOKS_INDEX)).value()) {
            client.indices().delete(d->d.index(BOOKS_INDEX));
        }
        try (InputStream mapping = new ClassPathResource("elasticsearch/books-index.json").getInputStream()) {
            client.indices().create(c -> c.index(BOOKS_INDEX).withJson(mapping));
        }
    }
}

package ru.athena.library_demo.search;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import ru.athena.library_demo.api.generated.model.BookDto;
import ru.athena.library_demo.elasticsearch.BookDocument;
import ru.athena.library_demo.elasticsearch.BookSearchService;
import ru.athena.library_demo.persistence.repository.BooksRepository;
import ru.athena.library_demo.service.LibraryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDate;

public class BookSearchTests extends BaseElasticsearchIntegrationTest{

    @Autowired
    LibraryService libraryService;
    @Autowired
    BookSearchService searchService;

    @Test
    void BookSearchService_search_shouldFindABook(){
        BookDto book = new BookDto("Les Trois Mousquetaires");
        book.setAuthor("Alexandre Dumas");
        book.setGenre("Adventure");
        book.setReleaseDate(LocalDate.of(1845, 1, 1).toString());
        long retrievedId = libraryService.saveBook(book).getId();

        await().atMost(Duration.ofSeconds(10L)).untilAsserted(() ->
        {
            refresh();
            assertThat(searchService.search("Trois",null,0,20).getTotalHits()).isEqualTo(1L);
        });


        assertThat(searchService.search("Quatre",null,0,20).getTotalHits()).isEqualTo(0L);

        assertThat(searchService.search("Trois","Adventure",0,20).getTotalHits()).isEqualTo(1L);
        assertThat(searchService.search("Trois","Play",0,20).getTotalHits()).isEqualTo(0L);

        libraryService.deleteBook(retrievedId);
        await().atMost(Duration.ofSeconds(10L)).untilAsserted(() ->
        {
            refresh();
            assertThat(searchService.search("Trois",null,0,20).getTotalHits()).isEqualTo(0L);
        });
    }


}

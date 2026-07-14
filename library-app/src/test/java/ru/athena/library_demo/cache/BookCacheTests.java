package ru.athena.library_demo.cache;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import ru.athena.library_demo.api.generated.model.BookDto;
import ru.athena.library_demo.persistence.entity.Book;
import ru.athena.library_demo.persistence.repository.BooksRepository;
import ru.athena.library_demo.service.LibraryService;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;

public class BookCacheTests extends BaseRedisIntegrationTest{

    @Autowired
    LibraryService libraryService;
    @MockitoSpyBean
    BooksRepository booksRepository;

    @Test
    void Cache_BookInCacheAfterReadingFromRepository(){
        long savedBookId = booksRepository.save(new Book(null, "Cached book","Author","Genre", LocalDate.now(),null)).getId();
        Cache booksCache = cacheManager.getCache("books");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(()->{
            libraryService.findById(savedBookId);
            assertThat(booksCache.get(savedBookId)).as("after reading").isNotNull();
        });

        libraryService.deleteBook(savedBookId);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(()->{
            assertThat(booksCache.get(savedBookId)).as("data mutated and evicted from cache").isNull();
        });
    }

    @Test
    void Cache_ShouldAvoidRepositoryCallsWhenCacheHit(){
        long savedBookId = booksRepository.save(new Book(null, "Cached book","Author","Genre", LocalDate.now(),null)).getId();

        libraryService.findById(savedBookId);
        libraryService.findById(savedBookId);
        Mockito.verify(booksRepository,Mockito.timeout(5000L).times(1)).findById(savedBookId);

        BookDto dto = new BookDto("Non-cached book");
        dto.setAuthor("Author");
        dto.setGenre("Genre");
        dto.setReleaseDate(LocalDate.now().toString());
        libraryService.putBook(dto,savedBookId);
        Mockito.clearInvocations(booksRepository);
        libraryService.findById(savedBookId);
        Mockito.verify(booksRepository,Mockito.timeout(10000L).times(1)).findById(savedBookId);
    }

}

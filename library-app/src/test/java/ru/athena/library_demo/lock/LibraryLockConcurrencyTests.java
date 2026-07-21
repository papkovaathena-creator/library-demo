package ru.athena.library_demo.lock;

import ch.qos.logback.core.util.ExecutorServiceUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.athena.library_demo.api.generated.model.BookDto;
import ru.athena.library_demo.cache.BaseRedisIntegrationTest;
import ru.athena.library_demo.exceptions.BookReservedException;
import ru.athena.library_demo.persistence.entity.Book;
import ru.athena.library_demo.service.LibraryService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

public class LibraryLockConcurrencyTests extends BaseRedisIntegrationTest {

    @Autowired
    LibraryService libraryService;
    @Autowired
    ReservationService reservationService;

    private final int THREAD_QUANTITY = 50;

    @Test
    void onlyOneOutOfFiftyThreadsSucceedsAtReserving() throws Exception{
        BookDto book = new BookDto("Process");
        book.setAuthor("Kafka");
        book.setGenre("Comedy");
        book.setReleaseDate(LocalDate.now().toString());
        BookDto savedBook = libraryService.saveBook(book);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_QUANTITY);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < THREAD_QUANTITY; i++) {
            final String user = "user-"+i;
            futures.add(executor.submit(()->{
                latch.await();
                try {
                    reservationService.reserve(savedBook.getId(), user);
                    return Boolean.TRUE;
                } catch (BookReservedException e) {
                    return Boolean.FALSE;
                }
            }));
        }
        latch.countDown();

        int successes = 0;
        for (Future<Boolean> f : futures){
            if (f.get(30, TimeUnit.SECONDS)) successes++;
        }

        assertThat(successes).isEqualTo(1);
    }

}

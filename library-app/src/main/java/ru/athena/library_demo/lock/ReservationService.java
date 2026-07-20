package ru.athena.library_demo.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import ru.athena.library_demo.api.generated.model.BookDto;
import ru.athena.library_demo.exceptions.BookReservedException;
import ru.athena.library_demo.service.LibraryService;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class ReservationService {

    private final RedissonClient redisson;
    private final LibraryService libraryService;

    public ReservationService(@Lazy RedissonClient redisson, LibraryService libraryService) {
        this.redisson = redisson;
        this.libraryService = libraryService;
    }

    public Optional<BookDto> reserve(Long id, String user) {
        RLock lock = redisson.getLock("lock:book:" + id);
        boolean acquired;
        try {
            acquired = lock.tryLock(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BookReservedException("Reservation interrupted while expecting a lock.");
        }
        if (!acquired) {
            throw new BookReservedException("Couldn't acquire a lock to reserve the book - " + id);
        }
        try {
            return libraryService.reserveBook(id, user);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

package ru.athena.library_demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.athena.library_demo.api.dto.BookMapper;
import ru.athena.library_demo.api.generated.model.BookDto;
import ru.athena.library_demo.elasticsearch.BookChangedEvent;
import ru.athena.library_demo.exceptions.BookReservedException;
import ru.athena.library_demo.persistence.entity.Book;
import ru.athena.library_demo.persistence.repository.BooksRepository;
import ru.athena.library_demo.persistence.repository.specifications.BookSpecifications;

import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class LibraryService {

    private static final Logger log = LoggerFactory.getLogger(LibraryService.class);

    private final BooksRepository booksRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public LibraryService(BooksRepository booksRepository, ApplicationEventPublisher applicationEventPublisher) {
        this.booksRepository = booksRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Cacheable("books")
    public Optional<BookDto> findById(Long id) {
        Optional<Book> bookOptional = booksRepository.findById(id);
        return BookMapper.map(bookOptional.orElse(null));
    }

    public BookDto saveBook(BookDto book) {
        log.info("Creating a book - {} by {}.", book.getName(), book.getAuthor());
        BookDto bookDto = BookMapper.map(booksRepository.save(BookMapper.reverseMap(book))).orElse(null);
        applicationEventPublisher.publishEvent(new BookChangedEvent(this, bookDto.getId(), BookChangedEvent.ChangeType.UPSERT));
        return bookDto;
    }

    @CacheEvict(value = "books", key = "#id")
    public Optional<BookDto> reserveBook(Long id, String reserverName) throws BookReservedException {
        log.info("Attempting to reserve a book {}.", id);
        Optional<Book> bookOptional = booksRepository.findById(id);
        if (bookOptional.isEmpty()) {
            log.error("Found no book with id - {}.", id);
            throw new NoSuchElementException("No such book in the library.");
        }
        Book book = bookOptional.get();
        if (book.getReserved() != null) {
            log.error("The book {} by {}(id - {}) is already reserved.", book.getName(), book.getAuthor(), book.getId());
            throw new BookReservedException("This book is already reserved.");
        }
        book.setReserved(reserverName);
        Book persisted = booksRepository.save(book);
        applicationEventPublisher.publishEvent(new BookChangedEvent(this, persisted.getId(), BookChangedEvent.ChangeType.UPSERT));
        log.info("The book {} by {}(id - {}) is successfully reserved.", book.getName(), book.getAuthor(), book.getId());
        return BookMapper.map(persisted);
    }

    @CacheEvict(value = "books", key = "#id")
    public Optional<BookDto> returnBook(Long id, String reserverName) throws BookReservedException {
        log.info("Attempting to return a book with id {}.", id);
        Optional<Book> bookOptional = booksRepository.findById(id);
        if (bookOptional.isEmpty()) {
            log.error("Found no book with id {}.", id);
            throw new NoSuchElementException("No such book in the library.");
        }
        Book book = bookOptional.get();
        if (book.getReserved() != null & !reserverName.equals(book.getReserved())) {
            log.error("The book {} by {}(id - {}) is reserved by {}.", book.getName(), book.getAuthor(), book.getId(), reserverName);
            throw new BookReservedException("This book is reserved by " + book.getReserved() + ".");
        }
        book.setReserved(null);
        Book persisted = booksRepository.save(book);
        applicationEventPublisher.publishEvent(new BookChangedEvent(this, persisted.getId(), BookChangedEvent.ChangeType.UPSERT));
        log.info("The book {} by {}(id - {}) is successfully returned.", book.getName(), book.getAuthor(), book.getId());
        return BookMapper.map(persisted);
    }

    @CacheEvict(value = "books", key = "#requestedId")
    public void putBook(BookDto bookUpdate, Long requestedId) {
        log.info("Creating or updating a book - {} by {}.", bookUpdate.getName(), bookUpdate.getAuthor());
        Optional<BookDto> book = this.findById(requestedId);
        BookDto updatedBook = new BookDto(bookUpdate.getName());
        updatedBook.setId(book.map(BookDto::getId).orElse(null));
        updatedBook.setAuthor(bookUpdate.getAuthor());
        updatedBook.setGenre(bookUpdate.getGenre());
        updatedBook.setReleaseDate(bookUpdate.getReleaseDate());
        updatedBook.setReservedBy(book.map(BookDto::getReservedBy).orElse(null));
        this.saveBook(updatedBook);
    }

    @CacheEvict(value = "books", key = "#id")
    public boolean deleteBook(Long id) throws BookReservedException {
        log.info("Attempting to delete a book with id {}.", id);
        Optional<String> reserved = booksRepository.findFirstReservedById(id);
        if (reserved.isEmpty()) {
            log.error("Found no book with id {}.", id);
            throw new NoSuchElementException("No such book in the library.");
        }
        if (!reserved.get().equals("NotReserved")) {
            log.error("The book with id {} is reserved.", id);
            throw new BookReservedException("This book has been reserved.");
        }
        booksRepository.deleteById(id);
        applicationEventPublisher.publishEvent(new BookChangedEvent(this, id, BookChangedEvent.ChangeType.DELETE));
        log.info("Book with id {} has been successfully deleted.", id);
        return true;
    }

//    public Page<BookDto> findAll(Map<String, String> searchCriteria, Pageable pageable){
//
//        String author = null;
//        String genre = null;
//        String yearFromS = null;
//        String yearToS = null;
//        if (searchCriteria != null) {
//            author = searchCriteria.get("author");
//            genre = searchCriteria.get("genre");
//            yearFromS = searchCriteria.get("yearFrom");
//            yearToS = searchCriteria.get("yearTo");
//        }
//        LocalDate yearFrom = yearFromS == null ? null : LocalDate.of(Integer.parseInt(yearFromS),1,1);
//        LocalDate yearTo = yearToS == null ? null : LocalDate.of(Integer.parseInt(yearToS),1,1);
//
//        Specification<Book> spec = Specification.unrestricted();
//        if (author != null) spec = spec.and(BookSpecifications.equalAuthor(author));
//        if (genre != null) spec = spec.and(BookSpecifications.equalGenre(genre));
//        if (yearFrom != null || yearTo != null) spec = spec.and(BookSpecifications.inYearSpan(yearFrom, yearTo));
//        Page<Book> books = booksRepository.findAll(spec, pageable);
//        return BookMapper.map(books);
//    }

    public Page<BookDto> findAll(String author, String genre, String yearFromS, String yearToS, Pageable pageable){
        LocalDate yearFrom = yearFromS == null ? null : LocalDate.of(Integer.parseInt(yearFromS),1,1);
        LocalDate yearTo = yearToS == null ? null : LocalDate.of(Integer.parseInt(yearToS),1,1);

        Specification<Book> spec = Specification.unrestricted();
        if (author != null) spec = spec.and(BookSpecifications.equalAuthor(author));
        if (genre != null) spec = spec.and(BookSpecifications.equalGenre(genre));
        if (yearFrom != null || yearTo != null) spec = spec.and(BookSpecifications.inYearSpan(yearFrom, yearTo));
        Page<Book> books = booksRepository.findAll(spec, pageable);
        return BookMapper.map(books);
    }

}

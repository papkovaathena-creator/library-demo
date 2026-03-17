package ru.athena.library_demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.athena.library_demo.api.dto.BookDto;
import ru.athena.library_demo.api.dto.BookMapper;
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

    @Autowired
    public LibraryService(BooksRepository booksRepository) {
        this.booksRepository = booksRepository;
    }

    public Optional<BookDto> findById(Long id) {
        Optional<Book> bookOptional = booksRepository.findById(id);
        return BookMapper.map(bookOptional.orElse(null));
    }

    public BookDto saveBook(BookDto book) {
        log.info("Creating a book - {} by {}.", book.getName(), book.getAuthor());
        return BookMapper.map(booksRepository.save(BookMapper.reverseMap(book))).orElse(null);
    }

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
        log.info("The book {} by {}(id - {}) is successfully reserved.", book.getName(), book.getAuthor(), book.getId());
        return BookMapper.map(booksRepository.save(book));
    }

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
            throw new BookReservedException("This book is reserved by " + reserverName + ".");
        }
        book.setReserved(null);
        log.info("The book {} by {}(id - {}) is successfully returned.", book.getName(), book.getAuthor(), book.getId());
        return BookMapper.map(booksRepository.save(book));
    }

    public void putBook(BookDto bookUpdate, Long requestedId) {
        log.info("Creating or updating a book - {} by {}.", bookUpdate.getName(), bookUpdate.getAuthor());
        Optional<BookDto> book = this.findById(requestedId);
        BookDto updatedBook = new BookDto(book.map(BookDto::getId).orElse(null), bookUpdate.getName(), bookUpdate.getAuthor(), bookUpdate.getGenre(), bookUpdate.getReleaseDate(), book.map(BookDto::getReservedBy).orElse(null));
        this.saveBook(updatedBook);
    }

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
        log.info("Book with id {} has been successfully deleted.", id);
        return true;
    }

    public Page<BookDto> findAll(Map<String, String> searchCriteria, Pageable pageable){

        String author = null;
        String genre = null;
        String yearFromS = null;
        String yearToS = null;
        if (searchCriteria != null) {
            author = searchCriteria.get("author");
            genre = searchCriteria.get("genre");
            yearFromS = searchCriteria.get("yearFrom");
            yearToS = searchCriteria.get("yearTo");
        }
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

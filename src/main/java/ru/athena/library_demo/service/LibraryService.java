package ru.athena.library_demo.service;

import org.springframework.beans.factory.BeanRegistry;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class LibraryService {

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
        return BookMapper.map(booksRepository.save(BookMapper.reverseMap(book))).orElse(null);
    }

    public Optional<BookDto> reserveBook(Long id) throws BookReservedException {
        Optional<Book> bookOptional = booksRepository.findById(id);
        if (bookOptional.isEmpty()) return Optional.empty();
        Book book = bookOptional.get();
        if (book.getReserved() != null) throw new BookReservedException();
        book.setReserved("Reserver");
        return BookMapper.map(booksRepository.save(book));
    }

    public Optional<BookDto> returnBook(Long id) {
        Optional<Book> bookOptional = booksRepository.findById(id);
        if (bookOptional.isEmpty()) return Optional.empty();
        Book book = bookOptional.get();
        book.setReserved(null);
        return BookMapper.map(booksRepository.save(book));
    }

    public void putBook(BookDto bookUpdate, Long requestedId) {
        Optional<BookDto> book = this.findById(requestedId);
        BookDto updatedBook = new BookDto(book.map(BookDto::getId).orElse(null), bookUpdate.getName(), bookUpdate.getAuthor(), bookUpdate.getGenre(), bookUpdate.getReleaseDate(), null);
        this.saveBook(updatedBook);
    }

    public boolean deleteBook(Long id) throws BookReservedException {
        Optional<String> reserved = booksRepository.findFirstReservedById(id);
        if (reserved.isPresent()) {
            if (!reserved.get().equals("NotReserved")) throw new BookReservedException();
            booksRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public List<BookDto> findAll(Map<String, String> searchCriteria){

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
        List<Book> books = booksRepository.findAll(spec);
        return BookMapper.map(books);
    }

}

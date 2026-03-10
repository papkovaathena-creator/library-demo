package ru.athena.library_demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.athena.library_demo.api.dto.BookDto;
import ru.athena.library_demo.api.dto.BookMapper;
import ru.athena.library_demo.exceptions.BookReservedException;
import ru.athena.library_demo.persistence.entity.Book;
import ru.athena.library_demo.persistence.repository.BooksRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
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

    public List<BookDto> findAll(String author, String genre, LocalDate yearFrom, LocalDate yearTo){
        StringBuilder filters = new StringBuilder();
        filters.append(author != null ? "Y" : "N");
        filters.append(genre != null ? "Y" : "N");
        filters.append(yearFrom != null ? "Y" : "N");
        filters.append(yearTo != null ? "Y" : "N");
        List<Book> books = new ArrayList<>();
        switch (filters.toString()){
            case "NNNN":
                books = booksRepository.findAll();
                break;
            case "YNNN":
                books = booksRepository.findByAuthor(author);
                break;
            case "NYNN":
                books = booksRepository.findByGenre(genre);
                break;
            case "NNYN":
                books = booksRepository.findByReleaseDateAfter(yearFrom);
                break;
            case "NNNY":
                books = booksRepository.findByReleaseDateBefore(yearTo);
                break;
            case "YYNN":
                books = booksRepository.findByAuthorAndGenre(author, genre);
                break;
            case "YNYN":
                books = booksRepository.findByAuthorAndReleaseDateAfter(author, yearFrom);
                break;
            case "YNNY":
                books = booksRepository.findByAuthorAndReleaseDateBefore(author, yearTo);
                break;
            case "NYYN":
                books = booksRepository.findByGenreAndReleaseDateAfter(genre, yearFrom);
                break;
            case "NYNY":
                books = booksRepository.findByGenreAndReleaseDateBefore(genre, yearTo);
                break;
            case "NNYY":
                books = booksRepository.findByReleaseDateBetween(yearFrom, yearTo);
                break;
            case "YYYN":
                books = booksRepository.findByAuthorAndGenreAndReleaseDateAfter(author, genre, yearFrom);
                break;
            case "YYNY":
                books = booksRepository.findByAuthorAndGenreAndReleaseDateBefore(author, genre, yearTo);
                break;
            case "YNYY":
                books = booksRepository.findByAuthorAndReleaseDateBetween(author, yearFrom, yearTo);
                break;
            case "NYYY":
                books = booksRepository.findByGenreAndReleaseDateBetween(genre, yearFrom, yearTo);
                break;
            case "YYYY":
                books = booksRepository.findByAuthorAndGenreAndReleaseDateBetween(author, genre, yearFrom, yearTo);
        }
        return BookMapper.map(books);
    }

}

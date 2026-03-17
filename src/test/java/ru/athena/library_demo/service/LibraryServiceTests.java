package ru.athena.library_demo.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import ru.athena.library_demo.api.dto.BookDto;
import ru.athena.library_demo.exceptions.BookReservedException;
import ru.athena.library_demo.persistence.entity.Book;
import ru.athena.library_demo.persistence.repository.BooksRepository;
import ru.athena.library_demo.persistence.repository.specifications.BookSpecifications;

import java.lang.reflect.Array;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LibraryServiceTests {

    @Mock
    private BooksRepository booksRepository;

    @InjectMocks
    private LibraryService libraryService;


    @Test
    public void LibraryService_SaveBook_ReturnsBookDto(){
        Book book = new Book(null, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);
        BookDto bookDto = new BookDto(null, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);

        when(booksRepository.save(Mockito.any(Book.class))).thenReturn(book);

        BookDto savedBook = libraryService.saveBook(bookDto);

        Assertions.assertThat(savedBook).isNotNull();
    }

    @Test
    public void LibraryService_PutBook_BookFoundNoException(){
        Book book = new Book(6L, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);
        BookDto bookDto = new BookDto(null, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);

        when(booksRepository.findById(6L)).thenReturn(Optional.ofNullable(book));
        when(booksRepository.save(Mockito.any(Book.class))).thenReturn(book);

        assertAll(()->libraryService.putBook(bookDto, 6L));
    }

    @Test
    public void LibraryService_PutBook_BookNotFoundNoException(){
        Book book = new Book(6L, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);
        BookDto bookDto = new BookDto(null, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);

        when(booksRepository.findById(6L)).thenReturn(Optional.empty());
        when(booksRepository.save(Mockito.any(Book.class))).thenReturn(book);

        assertAll(()->libraryService.putBook(bookDto, 6L));
    }

    @Test
    public void LibraryService_findById_ReturnsBookDto(){
        Book book = new Book(null, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);

        when(booksRepository.findById(6L)).thenReturn(Optional.ofNullable(book));

        Optional<BookDto> savedBook = libraryService.findById(6L);

        Assertions.assertThat(savedBook).isNotEmpty();
    }

    @Test
    public void LibraryService_DeleteBook_ReturnsTrue() throws BookReservedException {
        when(booksRepository.findFirstReservedById(6L)).thenReturn(Optional.of("NotReserved"));
        doNothing().when(booksRepository).deleteById(isA(Long.class));

        Assertions.assertThat(libraryService.deleteBook(6L)).isTrue();
    }

    @Test
    public void LibraryService_DeleteBook_CannotDeleteReservedBook() throws BookReservedException {
        when(booksRepository.findFirstReservedById(6L)).thenReturn(Optional.of("Reserver"));

        Assertions.assertThatThrownBy(() -> libraryService.deleteBook(6L)).isInstanceOf(BookReservedException.class);
    }

    @Test
    public void LibraryService_DeleteBook_CannotDeleteNonexistentBook() throws BookReservedException {
        when(booksRepository.findFirstReservedById(6L)).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> libraryService.deleteBook(6L)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void LibraryService_ReserveBook_BookIsReserved() throws BookReservedException {
        Long bookId = 6L;
        Book book = new Book(bookId, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);
        when(booksRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(booksRepository.save(Mockito.any(Book.class))).thenReturn(book);

        Optional<BookDto> bookDtoOptional = libraryService.reserveBook(bookId, "Tester");
        Assertions.assertThat(bookDtoOptional.get().getReservedBy()).isEqualTo("Tester");
    }

    @Test
    public void LibraryService_ReserveBook_CannotReserveReservedBook(){
        Long bookId = 6L;
        Book book = new Book(bookId, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), "Tester");
        when(booksRepository.findById(bookId)).thenReturn(Optional.of(book));
        Assertions.assertThatThrownBy(()->libraryService.reserveBook(bookId, "Tester")).isInstanceOf(BookReservedException.class);
    }

    @Test
    public void LibraryService_ReturnBook_BookIsReturned() {
        Long bookId = 6L;
        Book book = new Book(bookId, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), "Reserver");
        when(booksRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(booksRepository.save(Mockito.any(Book.class))).thenReturn(book);
        Optional<BookDto> bookDtoOptional = libraryService.returnBook(bookId);
        Assertions.assertThat(bookDtoOptional.get().getReservedBy()).isNull();
    }

    @Test
    public void LibraryServive_ReturnBook_NonReservedBookNoReaction() {
        Long bookId = 6L;
        Book book = new Book(bookId, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);
        when(booksRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(booksRepository.save(Mockito.any(Book.class))).thenReturn(book);
        Optional<BookDto> bookDtoOptional = libraryService.returnBook(bookId);
        Assertions.assertThat(bookDtoOptional.get().getReservedBy()).isNull();
    }

    @Test
    public void LibraryService_findAll_BooksAreReturned() {
        List<Book> books = List.of(
                new Book(6L, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null),
                new Book(7L, "Oedipus Rex", "Sophocles", "Tragedy", LocalDate.of(-429, 11, 24), null)
        );
        Page<Book> bookPage = new PageImpl<>(books);
        when(booksRepository.findAll(ArgumentMatchers.any(Specification.class),ArgumentMatchers.any(Pageable.class))).thenReturn(bookPage);
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("genre", "Tragedy");
        Page<BookDto> bookDtos = libraryService.findAll(paramMap, PageRequest.of(0,10));
        Assertions.assertThat(bookDtos.getSize()).isEqualTo(2);
    }

}

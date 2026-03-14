package ru.athena.library_demo.repository;


import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import ru.athena.library_demo.persistence.entity.Book;
import ru.athena.library_demo.persistence.repository.BooksRepository;
import ru.athena.library_demo.persistence.repository.specifications.BookSpecifications;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@DataJpaTest
@AutoConfigureTestDatabase(connection = EmbeddedDatabaseConnection.H2)
public class BooksRepositoryTests {

    @Autowired
    private BooksRepository booksRepository;

    @Test
    public void BooksRepository_SaveAll_ReturnsSavedBook() {
        Book book = new Book(null, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);
        Book savedBook = booksRepository.save(book);
        Assertions.assertThat(savedBook).isNotNull();
        Assertions.assertThat(savedBook.getId()).isGreaterThan(0L);
    }

    @Test
    public void BooksRepository_GetAll_ReturnsMoreThanOneBook(){
        Book book = new Book(null, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);
        Book book2 = new Book(null, "Pride and Prejudice", "Jane Austen", "Romance", LocalDate.of(1813, 1, 28), null);
        booksRepository.save(book);
        booksRepository.save(book2);
        List<Book> books = (List<Book>) booksRepository.findAll();
        Assertions.assertThat(books).isNotNull();
        Assertions.assertThat(books.size()).isEqualTo(7);
    }

    @Test
    public void BooksRepository_FindById_ReturnsBook() {
        Book book = new Book(null, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);
        Book savedBook = booksRepository.save(book);
        savedBook = booksRepository.findById(savedBook.getId()).orElse(null);
        Assertions.assertThat(savedBook).isNotNull();
        Assertions.assertThat(savedBook.getId()).isGreaterThan(0L);
    }

    @Test
    public void BooksRepository_UpdateBook_ReturnsUpdatedBook() {
        Book book = new Book(null, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);
        Book savedBook = booksRepository.save(book);
        savedBook.setName("Pride and Prejudice");
        savedBook = booksRepository.save(savedBook);
        Assertions.assertThat(savedBook).isNotNull();
        Assertions.assertThat(savedBook.getName()).isEqualTo("Pride and Prejudice");
    }

    @Test
    public void BooksRepository_DeleteBook_ReturnedBookIsEmpty() {
        Book book = new Book(null, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);
        Book savedBook = booksRepository.save(book);
        booksRepository.deleteById(savedBook.getId());
        Optional<Book> deletedBook = booksRepository.findById(savedBook.getId());
        Assertions.assertThat(deletedBook).isEmpty();
    }

    @Test
    public void BooksRepository_FindReservedById_FindsReserved() {
        Assertions.assertThat(booksRepository.findFirstReservedById(6L)).isEmpty();
        Book book = new Book(null, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);
        Book savedBook = booksRepository.save(book);
        Assertions.assertThat(booksRepository.findFirstReservedById(savedBook.getId())).isNotEmpty().contains("NotReserved");
        savedBook.setReserved("Reserver");
        savedBook = booksRepository.save(savedBook);
        Assertions.assertThat(booksRepository.findFirstReservedById(savedBook.getId())).isNotEmpty().contains("Reserver");
    }

    @Test
    public void BooksRepository_FindAll_FindsAll(){
        List<Book> books = booksRepository.findAll();
        Assertions.assertThat(books.size()).isEqualTo(5L);
    }

    @Test
    public void BooksRepository_FindByAuthor_FindsBooks(){
        List<Book> nonexistentBooks = booksRepository.findAll(BookSpecifications.equalAuthor("DoNotExist"));
        Assertions.assertThat(nonexistentBooks.size()).isEqualTo(0);
        List<Book> maupassantBooks = booksRepository.findAll(BookSpecifications.equalAuthor("Guy de Maupassant"));
        Assertions.assertThat(maupassantBooks.size()).isEqualTo(2);
    }

    @Test
    public void BooksRepository_FindByGenre_FindsBooks(){
        List<Book> nonexistentBooks = booksRepository.findAll(BookSpecifications.equalGenre("DoNotExist"));
        Assertions.assertThat(nonexistentBooks.size()).isEqualTo(0);
        List<Book> magicalRealism = booksRepository.findAll(BookSpecifications.equalGenre("Magical Realism"));
        Assertions.assertThat(magicalRealism.size()).isEqualTo(2);
    }

    @Test
    public void BooksRepository_FindByAuthorAndGenre_FindsBooks(){
        List<Book> nonexistentBooks = booksRepository.findAll(BookSpecifications.equalAuthor("DoNotExist")
                .and(BookSpecifications.equalGenre("DoNotExist")));
        Assertions.assertThat(nonexistentBooks.size()).isEqualTo(0);
        List<Book> horrorMaupassant = booksRepository.findAll(BookSpecifications.equalAuthor("Guy de Maupassant")
                .and(BookSpecifications.equalGenre("Horror")));
        Assertions.assertThat(horrorMaupassant.size()).isEqualTo(1);
    }

    @Test
    public void BooksRepository_FindByReleaseDateAfter_FindsBooks(){
        List<Book> books = booksRepository.findAll(BookSpecifications.inYearSpan(LocalDate.of(1900,1,1), null));
        Assertions.assertThat(books.size()).isEqualTo(2);
    }

    @Test
    public void BooksRepository_FindByReleaseDateBefore_FindsBooks(){
        List<Book> books = booksRepository.findAll(BookSpecifications.inYearSpan(null, LocalDate.of(1900,1,1)));
        Assertions.assertThat(books.size()).isEqualTo(3);
    }

    @Test
    public void BooksRepository_FindByReleaseDateBetween_FindsBooks(){
        List<Book> books = booksRepository.findAll(BookSpecifications.inYearSpan(LocalDate.of(1800,1,1), LocalDate.of(1900,1,1)));
        Assertions.assertThat(books.size()).isEqualTo(2);
    }

}

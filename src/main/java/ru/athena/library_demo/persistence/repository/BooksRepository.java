package ru.athena.library_demo.persistence.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import ru.athena.library_demo.persistence.entity.Book;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BooksRepository extends ListCrudRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    List<Book> findByAuthor(String author);
    List<Book> findByGenre(String genre);
    List<Book> findByAuthorAndGenre(String author, String genre);
    List<Book> findByReleaseDateAfter(LocalDate yearFrom);
    List<Book> findByReleaseDateBefore(LocalDate yearTo);
    List<Book> findByReleaseDateBetween(LocalDate yearFrom, LocalDate yearTo);
    List<Book> findByAuthorAndReleaseDateAfter(String author, LocalDate yearFrom);
    List<Book> findByAuthorAndReleaseDateBefore(String author, LocalDate yearTo);
    List<Book> findByAuthorAndReleaseDateBetween(String author, LocalDate yearFrom, LocalDate yearTo);
    List<Book> findByGenreAndReleaseDateAfter(String genre, LocalDate yearFrom);
    List<Book> findByGenreAndReleaseDateBefore(String genre, LocalDate yearTo);
    List<Book> findByGenreAndReleaseDateBetween(String genre, LocalDate yearFrom, LocalDate yearTo);
    List<Book> findByAuthorAndGenreAndReleaseDateAfter(String author, String genre, LocalDate yearFrom);
    List<Book> findByAuthorAndGenreAndReleaseDateBefore(String author, String genre, LocalDate yearTo);
    List<Book> findByAuthorAndGenreAndReleaseDateBetween(String author, String genre, LocalDate yearFrom, LocalDate yearTo);

    @Query("SELECT COALESCE(b.reserved, 'NotReserved') FROM Book b WHERE b.id = ?1")
    Optional<String> findFirstReservedById(Long id);

}

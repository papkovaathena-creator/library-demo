package ru.athena.library_demo.persistence.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import ru.athena.library_demo.persistence.entity.Book;

import java.util.Optional;

@Repository
public interface BooksRepository extends ListCrudRepository<Book, Long>, ListPagingAndSortingRepository<Book, Long>, JpaSpecificationExecutor<Book> {
    @Query("SELECT COALESCE(b.reserved, 'NotReserved') FROM Book b WHERE b.id = ?1")
    Optional<String> findFirstReservedById(Long id);

}

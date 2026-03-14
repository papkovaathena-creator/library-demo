package ru.athena.library_demo.persistence.repository.specifications;

import org.springframework.data.jpa.domain.Specification;
import ru.athena.library_demo.persistence.entity.Book;

import java.time.LocalDate;

public class BookSpecifications {

    public static Specification<Book> equalAuthor(String author) {
        if (author == null) return null;
        return ((root, query, criteriaBuilder)
                -> criteriaBuilder.equal(root.get("author"),author));
    }

    public static Specification<Book> equalGenre(String genre) {
        if (genre == null) return null;
        return ((root, query, criteriaBuilder)
                -> criteriaBuilder.equal(root.get("genre"),genre));
    }

    public static Specification<Book> inYearSpan(LocalDate yearFrom, LocalDate yearTo) {
        if (yearFrom == null && yearTo == null) return null;
        if (yearFrom == null) return ((root, query, criteriaBuilder)
                -> criteriaBuilder.lessThan(root.get("releaseDate"), yearTo));
        if (yearTo == null) return (((root, query, criteriaBuilder)
                -> criteriaBuilder.greaterThan(root.get("releaseDate"), yearFrom)));
        return ((root, query, criteriaBuilder)
                -> criteriaBuilder.between(root.get("releaseDate"),yearFrom, yearTo));
    }

}

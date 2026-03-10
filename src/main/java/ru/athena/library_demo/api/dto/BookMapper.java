package ru.athena.library_demo.api.dto;

import org.springframework.stereotype.Component;
import ru.athena.library_demo.persistence.entity.Book;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class BookMapper {

    public static Optional<BookDto> map(Book book) {
        return Optional.ofNullable(book).
                flatMap(b -> Optional.of(new BookDto(
                        b.getId(),
                        b.getName(),
                        b.getAuthor(),
                        b.getGenre(),
                        b.getReleaseDate(),
                        b.getReserved()
                )));
    }

    public static Book reverseMap(BookDto bookDto) {
        return new Book(bookDto.getId(), bookDto.getName(), bookDto.getAuthor(), bookDto.getGenre(), bookDto.getReleaseDate(), bookDto.getReservedBy());
    }

    public static List<BookDto> map(List<Book> books) {
        return books.stream()
                .map(BookMapper::map)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
}

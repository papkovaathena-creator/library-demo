package ru.athena.library_demo.api.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;
import ru.athena.library_demo.api.generated.model.BookDto;
import ru.athena.library_demo.persistence.entity.Book;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class BookMapper {

    public static Optional<BookDto> map(Book book) {
        return Optional.ofNullable(book).
                flatMap(b -> {
                    BookDto bookDto = new BookDto(b.getName());
                    bookDto.setId(b.getId());
                    bookDto.setGenre(b.getGenre());
                    bookDto.setAuthor(b.getAuthor());
                    bookDto.setReleaseDate(b.getReleaseDate().toString());
                    bookDto.setReservedBy(b.getReserved());
                    return Optional.of(bookDto);
                });
    }

    public static Book reverseMap(BookDto bookDto) {
        return new Book(bookDto.getId(), bookDto.getName(), bookDto.getAuthor(), bookDto.getGenre(), LocalDate.parse(bookDto.getReleaseDate()), bookDto.getReservedBy());
    }

    public static List<BookDto> map(List<Book> books) {
        return books.stream()
                .map(BookMapper::map)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public static Page<BookDto> map(Page<Book> books) {
        List<BookDto> bookList = books.stream()
                .map(BookMapper::map)
                .map(Optional::get)
                .toList();
        return new PageImpl<>(bookList);
    }
}

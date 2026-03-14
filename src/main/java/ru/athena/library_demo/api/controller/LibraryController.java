package ru.athena.library_demo.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import ru.athena.library_demo.api.dto.BookDto;
import ru.athena.library_demo.exceptions.BookReservedException;
import ru.athena.library_demo.persistence.entity.Book;
import ru.athena.library_demo.service.LibraryService;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/books")
public class LibraryController {

    private final LibraryService libraryService;

    @Autowired
    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }


    @GetMapping("/{requestedId}")
    private ResponseEntity<BookDto> findById(@PathVariable Long requestedId) {
        Optional<BookDto> book = libraryService.findById(requestedId);
        return book.map(ResponseEntity::ok).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Book not found."));
    }


    @PostMapping
    private ResponseEntity<BookDto> createBook(@RequestBody BookDto newBookRequest, UriComponentsBuilder ucb) {
        BookDto savedBook = libraryService.saveBook(newBookRequest);
        URI locationOfNewCashCard = ucb
                .path("books/{id}")
                .buildAndExpand(savedBook.getId())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).header("Location", locationOfNewCashCard.toString()).body(savedBook);
    }

    @PutMapping("/{requestedId}")
    private ResponseEntity<BookDto> putBook(@PathVariable Long requestedId, @RequestBody BookDto bookUpdate) {
        libraryService.putBook(bookUpdate, requestedId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{requestedId}")
    private ResponseEntity<Void> deleteBook(@PathVariable Long requestedId) {
        try {
            if (libraryService.deleteBook(requestedId)) {
                return ResponseEntity.noContent().build();
            }
        } catch (BookReservedException e) {
            throw new ResponseStatusException(CONFLICT, "Book is reserved.");
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{requestedId}/reserve")
    private ResponseEntity<BookDto> reserveBook(@PathVariable Long requestedId, UriComponentsBuilder ucb) {
        try {
            Optional<BookDto> savedBook = libraryService.reserveBook(requestedId);
            if (savedBook.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "Book not found.");
            URI locationOfNewCashCard = ucb
                    .path("books/{id}")
                    .buildAndExpand(savedBook.get().getId())
                    .toUri();
            return ResponseEntity.status(HttpStatus.CREATED).header("Location", locationOfNewCashCard.toString()).body(savedBook.get());
        } catch (BookReservedException e) {
            throw new ResponseStatusException(CONFLICT, "Book is already reserved.");
        }
    }

    @PostMapping("/{requestedId}/return")
    private ResponseEntity<BookDto> returnBook(@PathVariable Long requestedId, UriComponentsBuilder ucb) {
        Optional<BookDto> savedBook = libraryService.returnBook(requestedId);
        if (savedBook.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "Book not found.");
        URI locationOfNewCashCard = ucb
                .path("books/{id}")
                .buildAndExpand(savedBook.get().getId())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).header("Location", locationOfNewCashCard.toString()).body(savedBook.get());
    }

    @GetMapping
    private ResponseEntity<List<BookDto>> findBooks(@RequestParam(required = false) Map<String, String> searchCriteria)
    {
        List<BookDto> bookDtos = libraryService.findAll(searchCriteria);
        return ResponseEntity.ok(bookDtos);
    }
}

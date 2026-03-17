package ru.athena.library_demo.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import ru.athena.library_demo.api.dto.BookDto;
import ru.athena.library_demo.api.dto.SortDto;
import ru.athena.library_demo.api.dto.SortMapper;
import ru.athena.library_demo.exceptions.BookReservedException;
import ru.athena.library_demo.persistence.entity.Book;
import ru.athena.library_demo.service.LibraryService;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;

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
    public ResponseEntity<BookDto> findById(@PathVariable Long requestedId) {
        Optional<BookDto> book = libraryService.findById(requestedId);
        return book.map(ResponseEntity::ok).orElseThrow(() -> new NoSuchElementException("No such book in the library."));
    }


    @PostMapping
    // Throws 403 instead of 400?
    public ResponseEntity<BookDto> createBook(@Validated @RequestBody BookDto newBookRequest, UriComponentsBuilder ucb) {
        BookDto savedBook = libraryService.saveBook(newBookRequest);
        URI locationOfNewCashCard = ucb
                .path("books/{id}")
                .buildAndExpand(savedBook.getId())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).header("Location", locationOfNewCashCard.toString()).body(savedBook);
    }

    @PutMapping("/{requestedId}")
    public ResponseEntity<BookDto> putBook(@PathVariable Long requestedId, @RequestBody BookDto bookUpdate) {
        libraryService.putBook(bookUpdate, requestedId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{requestedId}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long requestedId) throws BookReservedException {
        libraryService.deleteBook(requestedId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{requestedId}/reserve")
    public ResponseEntity<BookDto> reserveBook(@PathVariable Long requestedId, UriComponentsBuilder ucb) throws BookReservedException {
        String reserverName = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<BookDto> savedBook = libraryService.reserveBook(requestedId, reserverName);
        URI locationOfNewCashCard = ucb
                .path("books/{id}")
                .buildAndExpand(savedBook.get().getId())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).header("Location", locationOfNewCashCard.toString()).body(savedBook.get());
    }

    @PostMapping("/{requestedId}/return")
    public ResponseEntity<BookDto> returnBook(@PathVariable Long requestedId, UriComponentsBuilder ucb) throws BookReservedException {
        String returnerName = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<BookDto> savedBook = libraryService.returnBook(requestedId, returnerName);
        URI locationOfNewCashCard = ucb
                .path("books/{id}")
                .buildAndExpand(savedBook.get().getId())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).header("Location", locationOfNewCashCard.toString()).body(savedBook.get());
    }

    @GetMapping
    public ResponseEntity<Page<BookDto>> findBooks(@RequestParam(required = false) Map<String, String> searchCriteria)
    {
        int pageNo = 0;
        int pageSize = 10;
        String sort = "[{\"field\":\"name\",\"direction\":\"asc\"}]";
        if (searchCriteria != null) {
            if (searchCriteria.get("pageNo") != null)
                pageNo = Integer.parseInt(searchCriteria.get("pageNo"));
            if (searchCriteria.get("pageSize") != null)
                pageSize = Integer.parseInt(searchCriteria.get("pageSize"));
            if (searchCriteria.get("sort") != null)
                sort = searchCriteria.get("sort");
        }

        List<SortDto> sortDtos = SortMapper.jsonStringToSortDto(sort);
        List<Sort.Order> orders = new ArrayList<>();

        if (sortDtos != null) {
            for(SortDto sortDto: sortDtos) {
                Sort.Direction direction = Objects.equals(sortDto.getDirection(), "desc")
                        ? Sort.Direction.DESC : Sort.Direction.ASC;
                orders.add(new Sort.Order(direction,sortDto.getField()));
            }
        }

        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by(orders));
        Page<BookDto> bookDtos = libraryService.findAll(searchCriteria, pageable);
        return ResponseEntity.ok(bookDtos);
    }
}

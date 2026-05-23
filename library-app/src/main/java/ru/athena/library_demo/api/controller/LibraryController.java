package ru.athena.library_demo.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import ru.athena.library_demo.api.dto.SortDto;
import ru.athena.library_demo.api.dto.SortMapper;
import ru.athena.library_demo.api.generated.DefaultApi;
import ru.athena.library_demo.api.generated.model.BookDto;
import ru.athena.library_demo.api.generated.model.BooksGet200Response;
import ru.athena.library_demo.service.LibraryService;

import java.net.URI;
import java.util.*;

@RestController
public class LibraryController implements DefaultApi {

    private final LibraryService libraryService;

    @Autowired
    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }


//    @GetMapping("/{requestedId}")
//    public ResponseEntity<BookDto> findById(@PathVariable Long requestedId) {
//        Optional<BookDto> book = libraryService.findById(requestedId);
//        return book.map(ResponseEntity::ok).orElseThrow(() -> new NoSuchElementException("No such book in the library."));
//    }

    @Override
    public ResponseEntity<BookDto> booksIdGet(Long id) {
        Optional<BookDto> book = libraryService.findById(id);
        return book.map(ResponseEntity::ok).orElseThrow(() -> new NoSuchElementException("No such book in the library."));
    }


//    @PostMapping
//    // Throws 403 instead of 400?
//    public ResponseEntity<BookDto> createBook(@Validated @RequestBody BookDto newBookRequest, UriComponentsBuilder ucb) {
//        BookDto savedBook = libraryService.saveBook(newBookRequest);
//        URI locationOfNewBook = ucb
//                .path("books/{id}")
//                .buildAndExpand(savedBook.getId())
//                .toUri();
//        return ResponseEntity.status(HttpStatus.CREATED).header("Location", locationOfNewBook.toString()).body(savedBook);
//    }

    @Override
    public ResponseEntity<BookDto> booksPost(BookDto bookDTO) {
        BookDto savedBook = libraryService.saveBook(bookDTO);
        UriComponentsBuilder ucb = UriComponentsBuilder.newInstance();
        URI locationOfNewBook = ucb
                .scheme("http")
                .host("localhost")
                .path("books/{id}")
                .buildAndExpand(savedBook.getId())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).header("Location", locationOfNewBook.toString()).body(savedBook);
    }

//    @PutMapping("/{requestedId}")
//    public ResponseEntity<BookDto> putBook(@PathVariable Long requestedId, @RequestBody BookDto bookUpdate) {
//        libraryService.putBook(bookUpdate, requestedId);
//        return ResponseEntity.noContent().build();
//    }


    @Override
    public ResponseEntity<Void> booksIdPut(Long id, BookDto bookDTO) {
        libraryService.putBook(bookDTO, id);
        return ResponseEntity.noContent().build();
    }


//    @DeleteMapping("/{requestedId}")
//    public ResponseEntity<Void> deleteBook(@PathVariable Long requestedId) throws BookReservedException {
//        libraryService.deleteBook(requestedId);
//        return ResponseEntity.noContent().build();
//    }

    @Override
    public ResponseEntity<Void> booksIdDelete(Long id) {
        libraryService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }

//    @PostMapping("/{requestedId}/reserve")
//    public ResponseEntity<BookDto> reserveBook(@PathVariable Long requestedId, UriComponentsBuilder ucb) throws BookReservedException {
//        String reserverName = SecurityContextHolder.getContext().getAuthentication().getName();
//        Optional<BookDto> savedBook = libraryService.reserveBook(requestedId, reserverName);
//        URI locationOfNewBook = ucb
//                .path("books/{id}")
//                .buildAndExpand(savedBook.get().getId())
//                .toUri();
//        return ResponseEntity.status(HttpStatus.CREATED).header("Location", locationOfNewBook.toString()).body(savedBook.get());
//    }


    @Override
    public ResponseEntity<BookDto> booksIdReservePost(Long id) {
        String reserverName = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<BookDto> savedBook = libraryService.reserveBook(id, reserverName);
        UriComponentsBuilder ucb = UriComponentsBuilder.newInstance();
        URI locationOfNewBook = ucb
                .scheme("http")
                .host("localhost")
                .path("books/{id}")
                .buildAndExpand(savedBook.get().getId())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).header("Location", locationOfNewBook.toString()).body(savedBook.get());
    }


//    @PostMapping("/{requestedId}/return")
//    public ResponseEntity<BookDto> returnBook(@PathVariable Long requestedId, UriComponentsBuilder ucb) throws BookReservedException {
//        String returnerName = SecurityContextHolder.getContext().getAuthentication().getName();
//        Optional<BookDto> savedBook = libraryService.returnBook(requestedId, returnerName);
//        URI locationOfNewBook = ucb
//                .path("books/{id}")
//                .buildAndExpand(savedBook.get().getId())
//                .toUri();
//        return ResponseEntity.status(HttpStatus.CREATED).header("Location", locationOfNewBook.toString()).body(savedBook.get());
//    }


    @Override
    public ResponseEntity<BookDto> booksIdReturnPost(Long id) {
        String returnerName = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<BookDto> savedBook = libraryService.returnBook(id, returnerName);
        UriComponentsBuilder ucb = UriComponentsBuilder.newInstance();
        URI locationOfNewBook = ucb
                .scheme("http")
                .host("localhost")
                .path("books/{id}")
                .buildAndExpand(savedBook.get().getId())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).header("Location", locationOfNewBook.toString()).body(savedBook.get());
    }


//    @GetMapping
//    public ResponseEntity<Page<BookDto>> findBooks(@RequestParam(required = false) Map<String, String> searchCriteria)
//    {
//        int pageNo = 0;
//        int pageSize = 10;
//        String sort = "[{\"field\":\"name\",\"direction\":\"asc\"}]";
//        if (searchCriteria != null) {
//            if (searchCriteria.get("pageNo") != null)
//                pageNo = Integer.parseInt(searchCriteria.get("pageNo"));
//            if (searchCriteria.get("pageSize") != null)
//                pageSize = Integer.parseInt(searchCriteria.get("pageSize"));
//            if (searchCriteria.get("sort") != null)
//                sort = searchCriteria.get("sort");
//        }
//
//        List<SortDto> sortDtos = SortMapper.jsonStringToSortDto(sort);
//        List<Sort.Order> orders = new ArrayList<>();
//
//        if (sortDtos != null) {
//            for(SortDto sortDto: sortDtos) {
//                Sort.Direction direction = Objects.equals(sortDto.getDirection(), "desc")
//                        ? Sort.Direction.DESC : Sort.Direction.ASC;
//                orders.add(new Sort.Order(direction,sortDto.getField()));
//            }
//        }
//
//        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by(orders));
//        Page<BookDto> bookDtos = libraryService.findAll(searchCriteria, pageable);
//        return ResponseEntity.ok(bookDtos);
//    }

    @Override
    public ResponseEntity<BooksGet200Response> booksGet(String genre, String author, String yearFrom, String yearTo, Long pageNo, Long pageSize, String sort) {
        List<SortDto> sortDtos = SortMapper.jsonStringToSortDto(sort);
        List<Sort.Order> orders = new ArrayList<>();

        if (sortDtos != null) {
            for(SortDto sortDto: sortDtos) {
                Sort.Direction direction = Objects.equals(sortDto.getDirection(), "desc")
                        ? Sort.Direction.DESC : Sort.Direction.ASC;
                orders.add(new Sort.Order(direction,sortDto.getField()));
            }
        }

        Pageable pageable = PageRequest.of(Math.toIntExact(pageNo), Math.toIntExact(pageSize), Sort.by(orders));
        Page<BookDto> bookDtos = libraryService.findAll(genre, author, yearFrom, yearTo, pageable);
        BooksGet200Response booksGet200Response = new BooksGet200Response();
        booksGet200Response.setTotalElements((int) bookDtos.getTotalElements());
        booksGet200Response.setTotalPages(bookDtos.getTotalPages());
        booksGet200Response.setContent(bookDtos.getContent());
        booksGet200Response.setNumber(bookDtos.getNumber());
        booksGet200Response.setSize(bookDtos.getSize());

        return ResponseEntity.ok(booksGet200Response);
    }

}

package ru.athena.library_demo.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.athena.library_demo.api.generated.AdminApi;
import ru.athena.library_demo.elasticsearch.BookReindexService;

@RestController
public class AdminController implements AdminApi {

    private final BookReindexService bookReindexService;

    public AdminController(BookReindexService bookReindexService) {
        this.bookReindexService = bookReindexService;
    }

    @Override
    public ResponseEntity<Void> reindex() {
        bookReindexService.reindex();
        return ResponseEntity.ok().build();
    }
}

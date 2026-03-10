package ru.athena.library_demo.api.dto;

import jakarta.persistence.Column;
import ru.athena.library_demo.persistence.entity.Book;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

public class BookDto {

    private Long id;
    private String name;
    private String author;
    private String genre;
    private LocalDate releaseDate;
    private String reservedBy;

    public BookDto() {
    }

    public BookDto(Long id, String name, String author, String genre, LocalDate releaseDate, String reservedBy) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.genre = genre;
        this.releaseDate = releaseDate;
        this.reservedBy = reservedBy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getReservedBy() {
        return reservedBy;
    }

    public void setReservedBy(String reservedBy) {
        this.reservedBy = reservedBy;
    }

}

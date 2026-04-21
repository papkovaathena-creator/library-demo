package ru.athena.library_demo.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "BOOKS")
public class Book {



    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "name", nullable = false)
    private String name;
    private String author;
    private String genre;
    @Column(name = "release_date")
    private LocalDate releaseDate;
    @Column(name = "reserved")
    private String reserved;

    public Book() {
    }

    public Book(Long id, String name, String author, String genre, LocalDate releaseDate, String reservedBy) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.genre = genre;
        this.releaseDate = releaseDate;
        this.reserved = reservedBy;
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

    public String getReserved() {
        return reserved;
    }

    public void setReserved(String reserved) {
        this.reserved = reserved;
    }
}

package ru.athena.library_demo.elasticsearch;

import java.time.LocalDate;

public class BookDocument {
    private Long id;
    private String name;
    private String author;
    private String genre;
    private String releaseDate;
    private String reservedBy;

    public BookDocument() {
    }

    public BookDocument(Long id, String name, String author, String genre, String releaseDate, String reservedBy) {
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

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getReservedBy() {
        return reservedBy;
    }

    public void setReservedBy(String reservedBy) {
        this.reservedBy = reservedBy;
    }

    @Override
    public String toString() {
        return "BookDocument{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", author='" + author + '\'' +
                ", genre='" + genre + '\'' +
                ", releaseDate='" + releaseDate + '\'' +
                ", reservedBy='" + reservedBy + '\'' +
                '}';
    }
}

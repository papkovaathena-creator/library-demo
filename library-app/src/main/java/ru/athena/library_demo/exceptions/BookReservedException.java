package ru.athena.library_demo.exceptions;

public class BookReservedException extends RuntimeException{

    public BookReservedException() {
    }

    public BookReservedException(String message) {
        super(message);
    }
}

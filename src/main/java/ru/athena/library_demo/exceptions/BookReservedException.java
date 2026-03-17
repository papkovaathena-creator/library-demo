package ru.athena.library_demo.exceptions;

public class BookReservedException extends Exception{

    public BookReservedException() {
    }

    public BookReservedException(String message) {
        super(message);
    }
}

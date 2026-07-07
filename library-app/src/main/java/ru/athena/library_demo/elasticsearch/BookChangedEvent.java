package ru.athena.library_demo.elasticsearch;

import org.springframework.context.ApplicationEvent;

public class BookChangedEvent extends ApplicationEvent {

    private final Long bookId;
    private final ChangeType type;

    public BookChangedEvent(Object source, Long bookId, ChangeType type) {
        super(source);
        this.bookId = bookId;
        this.type = type;
    }

    public Long getBookId() {
        return bookId;
    }

    public ChangeType getType() {
        return type;
    }

    public enum ChangeType { UPSERT, DELETE }
}

package ru.athena.library_demo.api.dto;

public class SortDto {

    private String field;
    private String direction;

    public SortDto(String field, String direction) {
        this.field = field;
        this.direction = direction;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}

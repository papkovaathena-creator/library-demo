package ru.athena.library_demo.api.dto;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class SortMapper {

    public static List<SortDto> jsonStringToSortDto(String jsonString) {
        try {
            ObjectMapper obj = new ObjectMapper();
            return obj.readValue(jsonString, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }

}

package ru.athena.library_demo.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

public class BookSearchService {

    private final ElasticsearchClient client;

    public BookSearchService(ElasticsearchClient client) {
        this.client = client;
    }

    public BookSearchResult search(String query, String genreFilter, int page, int size) {



        return new BookSearchResult();
    }
}

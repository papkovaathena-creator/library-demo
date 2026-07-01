package ru.athena.library_demo.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import org.springframework.stereotype.Service;
import ru.athena.library_demo.api.generated.model.BookDto;
import ru.athena.library_demo.api.generated.model.BookSearchResultDto;
import ru.athena.library_demo.api.generated.model.FacetDto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Service
public class BookSearchService {

    private final ElasticsearchClient client;
    private static final String GENRE_AGG = "genres";

    public BookSearchService(ElasticsearchClient client) {
        this.client = client;
    }

    public BookSearchResultDto search(String query, String genreFilter, int page, int size) {
        Query multiMatch =Query.of(q->q
                .multiMatch(mm->mm.query(query).fields("name^2","author")));

        Query finalQuery;

        if (genreFilter != null && !genreFilter.isBlank()) {
            finalQuery = Query.of(q -> q
                    .bool(b -> b
                            .must(multiMatch)
                            .filter(f -> f.term(t->t.field("genre").value(genreFilter)))));
        } else {
            finalQuery = multiMatch;
        }
        try {
            SearchResponse<BookDocument> response = client.search(s->s
                    .index(BookIndexInitializer.BOOKS_INDEX)
                    .from(page * size)
                    .size(size)
                    .query(finalQuery)
                    .aggregations(GENRE_AGG, a->a.terms(t->t.field("genre"))),
                    BookDocument.class);
            return toResult(response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BookSearchResultDto toResult(SearchResponse<BookDocument> response) {
        BookSearchResultDto result = new BookSearchResultDto();

        List<BookDto> content = response.hits().hits().stream()
                .map(hit -> toDto(hit.source()))
                .toList();
        result.setContent(content);

        long hits = response.hits().total() != null ? response.hits().total().value() : 0L;
        result.setTotalHits(hits);

        List<FacetDto> facets = response.aggregations().get(GENRE_AGG).sterms().buckets().array().stream()
                .map(bucket -> {
                    FacetDto facet = new FacetDto();
                    facet.setGenre(bucket.key().stringValue());
                    facet.setCount(bucket.docCount());
                    return facet;
                })
                .toList();
        result.setFacets(facets);

        return result;
    }

    private BookDto toDto(BookDocument source) {
        BookDto result = new BookDto(source.getName());
        result.setAuthor(source.getAuthor());
        result.setGenre(source.getGenre());
        result.setReleaseDate(source.getReleaseDate());
        result.setReservedBy(source.getReservedBy());
        return result;
    }
}

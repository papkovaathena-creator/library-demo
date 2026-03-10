package ru.athena.library_demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.context.WebApplicationContext;
import ru.athena.library_demo.api.dto.BookDto;
import ru.athena.library_demo.persistence.entity.Book;

import java.net.URI;
import java.time.LocalDate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LibraryRESTAPITests {

    RestTestClient client;

    @BeforeEach
    void setUp(WebApplicationContext context) {  // Inject the configuration
        // Create the `RestTestClient`
        client = RestTestClient.bindToApplicationContext(context).build();
    }

    @Test
    void shouldReturnABookWhenDataIsPresent() {
        EntityExchangeResult<BookDto> result = client.get().uri("/books/1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(BookDto.class)
                .returnResult();

        BookDto book = result.getResponseBody();
        assertThat(book.getId()).isEqualTo(1);
        assertThat(book.getName()).isEqualTo("Master and Margarita");
    }

    @Test
    void shouldNotReturnABookWhenDataIsAbsent() {
        client.get().uri("/books/1000")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldCreateANewBook() {
        BookDto newBook = new BookDto(null, "Paradise Lost", "John Milton", "Epic", LocalDate.of(1667,1,1), null);
        URI locationOfNewBook = client.post().uri("/books")
                .body(newBook)
                .exchange()
                .expectStatus().isCreated()
                .expectBody().returnResult()
                .getResponseHeaders().getLocation();


        client.get().uri(locationOfNewBook)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(Book.class);
    }

    @Test
    void shouldUpdateAnExistingBook() {
        BookDto newBook = new BookDto(null, "Paradise Lost", "John Milton", "Epic", LocalDate.of(1667,1,1), null);
        client.put().uri("/books/1")
                .body(newBook)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();


        EntityExchangeResult<BookDto> result = client.get().uri("/books/1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(BookDto.class)
                .returnResult();

        BookDto book = result.getResponseBody();
        assertThat(book.getId()).isEqualTo(1);
        assertThat(book.getName()).isEqualTo("Paradise Lost");
    }

    @Test
    void shouldCreateANewBookWithPUT() {
        BookDto newBook = new BookDto(null, "Paradise Lost", "John Milton", "Epic", LocalDate.of(1667,1,1), null);
        client.put().uri("/books/7")
                .body(newBook)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();


        EntityExchangeResult<BookDto> result = client.get().uri("/books/7")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(BookDto.class)
                .returnResult();

        BookDto book = result.getResponseBody();
        assertThat(book.getId()).isEqualTo(7);
        assertThat(book.getName()).isEqualTo("Paradise Lost");
    }

    @Test
    void shouldDeleteAnExistingBook() {
        client.delete().uri("/books/1")
                .exchange()
                .expectStatus().isNoContent();

        client.get().uri("/books/1")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldNotDeleteANonexistingBook() {
        client.delete().uri("/books/1000")
                .exchange()
                .expectStatus().isNotFound();
    }

}

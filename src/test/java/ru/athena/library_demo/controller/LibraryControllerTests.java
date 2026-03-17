package ru.athena.library_demo.controller;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import ru.athena.library_demo.api.controller.LibraryController;
import ru.athena.library_demo.api.dto.BookDto;
import ru.athena.library_demo.exceptions.BookReservedException;
import ru.athena.library_demo.service.LibraryService;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;


@WebMvcTest(controllers = LibraryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
public class LibraryControllerTests {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private LibraryService libraryService;
    @Autowired
    private ObjectMapper objectMapper;

    private BookDto bookDto;

    @BeforeEach
    public void init(){
        bookDto = new BookDto(null, "Wuthering Heights", "Emily Bronte", "Tragedy", LocalDate.of(1847, 11, 24), null);
    }

    @Test
    public void LibraryController_CreateBook_LocationHeaderPresent() throws Exception{
        given(libraryService.saveBook(ArgumentMatchers.any())).willAnswer(invocation -> {
            BookDto bookDto1 = invocation.getArgument(0);
            bookDto1.setId(6L);
            return bookDto1;
        });

        ResultActions response = mockMvc.perform(post("/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bookDto)));

        response.andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.header().string("Location","http://localhost/books/6"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name", CoreMatchers.is(bookDto.getName())));
    }

    @Test
    public void LibraryController_FindById_ReturnsBookDto() throws Exception {
        long bookId = 6L;
        given(libraryService.findById(ArgumentMatchers.anyLong())).willAnswer(
                (invocation) -> {
                    bookDto.setId(invocation.getArgument(0));
                    return Optional.ofNullable(bookDto);
                }
        );

        ResultActions response = mockMvc.perform(get("/books/" + bookId)
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name", CoreMatchers.is(bookDto.getName())));
    }

    @Test
    public void LibraryController_PutBook_NoException() throws Exception {
        long bookId = 6L;
        doNothing().when(libraryService).putBook(ArgumentMatchers.any(), ArgumentMatchers.anyLong());

        ResultActions response = mockMvc.perform(put("/books/" + bookId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bookDto)));

        response.andExpect(MockMvcResultMatchers.status().isNoContent());
    }

    @Test
    public void LibraryController_DeleteBook_NoException() throws Exception {
        long bookId = 6L;
        when(libraryService.deleteBook(ArgumentMatchers.anyLong())).thenReturn(true);

        ResultActions response = mockMvc.perform(delete("/books/" + bookId)
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(MockMvcResultMatchers.status().isNoContent());
    }

    @Test
    public void LibraryController_DeleteBook_CannotDeleteReservedBook() throws Exception {
        long bookId = 6L;
        when(libraryService.deleteBook(ArgumentMatchers.anyLong())).thenThrow(new BookReservedException("This book has been reserved."));

        ResultActions response = mockMvc.perform(delete("/books/" + bookId)
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
    public void LibraryController_ReserveBook_BookReserved() throws Exception {
        long bookId = 6L;
        bookDto.setId(bookId);

        Authentication authentication = Mockito.mock(Authentication.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        given(SecurityContextHolder.getContext().getAuthentication().getName())
                .willAnswer(invocation -> "Tester");

        given(libraryService.reserveBook(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString())).willAnswer(invocation -> {
            bookDto.setReservedBy("Tester");
            return Optional.of(bookDto);
        });

        ResultActions response = mockMvc.perform(post("/books/" + bookId + "/reserve")
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.header().string("Location","http://localhost/books/" + bookId))
                .andExpect(MockMvcResultMatchers.jsonPath("$.reservedBy", CoreMatchers.is("Tester")));
    }

    @Test
    public void LibraryController_ReserveBook_BookNotFound() throws Exception {
        long bookId = 6L;
        bookDto.setId(bookId);

        Authentication authentication = Mockito.mock(Authentication.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        given(SecurityContextHolder.getContext().getAuthentication().getName())
                .willAnswer(invocation -> "Tester");

        given(libraryService.reserveBook(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString()))
                .willAnswer(invocation -> Optional.empty());

        ResultActions response = mockMvc.perform(post("/books/" + bookId + "/reserve")
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    public void LibraryController_ReserveBook_BookWasAlreadyReserved() throws Exception {
        long bookId = 6L;
        bookDto.setId(bookId);

        Authentication authentication = Mockito.mock(Authentication.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        given(SecurityContextHolder.getContext().getAuthentication().getName())
                .willAnswer(invocation -> "Tester");

        when(libraryService.reserveBook(bookId, "Tester")).thenThrow(new BookReservedException());

        ResultActions response = mockMvc.perform(post("/books/" + bookId + "/reserve")
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
    public void LibraryController_ReturnBook_BookReturned() throws Exception {
        long bookId = 6L;
        bookDto.setId(bookId);
        bookDto.setReservedBy("Reserver");

        Authentication authentication = Mockito.mock(Authentication.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        given(SecurityContextHolder.getContext().getAuthentication().getName())
                .willAnswer(invocation -> "Tester");

        given(libraryService.returnBook(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString())).willAnswer(invocation -> {
            bookDto.setReservedBy(null);
            return Optional.of(bookDto);
        });

        ResultActions response = mockMvc.perform(post("/books/" + bookId + "/return")
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.header().string("Location","http://localhost/books/" + bookId))
                .andExpect(MockMvcResultMatchers.jsonPath("$.reservedBy", CoreMatchers.nullValue()));
    }

    @Test
    public void LibraryController_ReturnBook_BookNotFound() throws Exception {
        long bookId = 6L;
        bookDto.setId(bookId);

        Authentication authentication = Mockito.mock(Authentication.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        given(SecurityContextHolder.getContext().getAuthentication().getName())
                .willAnswer(invocation -> "Tester");

        given(libraryService.returnBook(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString()))
                .willAnswer(invocation -> Optional.empty());

        ResultActions response = mockMvc.perform(post("/books/" + bookId + "/return")
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    public void LibraryController_ReturnBook_BookReservedByAnother() throws Exception {
        long bookId = 6L;
        bookDto.setId(bookId);
        bookDto.setReservedBy("Admin");

        Authentication authentication = Mockito.mock(Authentication.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        given(SecurityContextHolder.getContext().getAuthentication().getName())
                .willAnswer(invocation -> "Admin");

        given(libraryService.returnBook(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString()))
                .willThrow(new BookReservedException());

        ResultActions response = mockMvc.perform(post("/books/" + bookId + "/return")
                .contentType(MediaType.APPLICATION_JSON));

        response.andExpect(MockMvcResultMatchers.status().isConflict());
    }
}

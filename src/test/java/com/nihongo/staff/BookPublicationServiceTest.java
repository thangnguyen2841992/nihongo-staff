package com.nihongo.staff;

import com.nihongo.staff.model.*;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.BookPublicationService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookPublicationServiceTest {
    IBookRepository books = mock(IBookRepository.class);
    ILessonsRepository lessons = mock(ILessonsRepository.class);
    IExerciseKeywordRepository exercises = mock(IExerciseKeywordRepository.class);
    BookPublicationService service = new BookPublicationService(books, lessons, exercises);

    @Test void draftNeedsACompleteLessonBeforeReviewAndAdminPublication() {
        Books book = new Books(); book.setBookId(1L); book.setBookName("TRY N3");
        book.setPublicationStatus(PublicationStatus.DRAFT);
        when(books.findById(1L)).thenReturn(Optional.of(book));
        when(lessons.findByBook_BookId(1L)).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.submit(1L));

        Lessons lesson = new Lessons(); lesson.setLessonId(2L); lesson.setName("Bài 1"); lesson.setReading("本文");
        when(lessons.findByBook_BookId(1L)).thenReturn(List.of(lesson));
        when(exercises.findByLessons_LessonId(2L)).thenReturn(List.of());
        assertEquals(PublicationStatus.IN_REVIEW, service.submit(1L).status());
        assertEquals(PublicationStatus.PUBLISHED, service.publish(1L).status());
        assertEquals(PublicationStatus.PUBLISHED, book.getPublicationStatus());
        assertEquals(PublicationStatus.DRAFT, service.returnToDraft(1L).status());
    }
}

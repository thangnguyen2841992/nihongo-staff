package com.nihongo.staff.controller;

import com.nihongo.staff.model.dto.BookResponse;
import com.nihongo.staff.repository.ILessonsRepository;
import com.nihongo.staff.service.IStaffService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicSyllabusControllerTest {
    @Test void showsOnlyPublishedBooksWithLessonCountsAndCachesTheAnswer() {
        var staff = mock(IStaffService.class);
        var lessons = mock(ILessonsRepository.class);
        var published = new BookResponse(); published.setBookId(1L); published.setBookName("Minna N5"); published.setPublicationStatus("PUBLISHED");
        var draft = new BookResponse(); draft.setBookId(2L); draft.setBookName("Bản nháp"); draft.setPublicationStatus("DRAFT");
        when(staff.getBooksByLevel(5L)).thenReturn(List.of(published, draft));
        when(lessons.countByBooks(anyCollection())).thenReturn(List.<Object[]>of(new Object[]{1L, 25L}));
        var controller = new PublicSyllabusController(staff, lessons);

        var body = controller.syllabus(5L).getBody();
        assertEquals(List.of(new PublicSyllabusController.SyllabusBook(1L, "Minna N5", 25)), body);
        verify(lessons).countByBooks(List.of(1L));
        controller.syllabus(5L);
        verify(staff, times(1)).getBooksByLevel(5L);
        assertTrue(controller.syllabus(5L).getHeaders().getCacheControl().contains("public"));
    }
}

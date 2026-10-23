package com.nihongo.staff.controller;

import com.nihongo.staff.model.ExerciseKeywordDTO;
import com.nihongo.staff.security.ContentAccess;
import com.nihongo.staff.service.BookAiSolutionService;
import com.nihongo.staff.service.ExerciseGradingService;
import com.nihongo.staff.service.IStaffService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LearnerExerciseCopyTest {
    @Test void hidingAnswersForALearnerDoesNotChangeTheCachedRows() {
        var service = mock(IStaffService.class);
        var cached = ExerciseKeywordDTO.builder().exerciseKeywordId(1L).contentNihongo("問題").correctAnswer("A").lessonId(5L).build();
        when(service.getAllExcercisesKeywordOfLesson(5L)).thenReturn(List.of(cached));
        var access = mock(ContentAccess.class);
        when(access.manager(any())).thenReturn(false);
        var controller = new StaffRestController(service, mock(ExerciseGradingService.class), mock(BookAiSolutionService.class), access);
        var first = controller.getAllExercisesKeywordOfLesson(5L, null).getBody();
        assertNull(first.get(0).getCorrectAnswer());
        assertEquals("問題", first.get(0).getContentNihongo());
        assertEquals("A", cached.getCorrectAnswer(), "the shared cached row keeps its answer for grading and staff");
        assertNotSame(cached, first.get(0));
    }
}

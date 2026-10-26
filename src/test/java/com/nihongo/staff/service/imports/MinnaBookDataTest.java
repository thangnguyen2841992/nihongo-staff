package com.nihongo.staff.service.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MinnaBookDataTest {
    @Test void preparedLessonsCoverTwentySixToFiftyAndMatchAudioByLessonNumber() throws Exception {
        var book = new MinnaBookData(new ObjectMapper()).book();
        assertEquals("5d0ce9b33bab69e0f6b81d019f12594d95be4ee6a629085410e33fd3687e0008", book.sourceSha256());
        assertEquals(IntStream.rangeClosed(26, 50).boxed().toList(),
                book.lessons().stream().map(l -> LessonTrackMatcher.lessonNumber(l.name())).toList());
        int previousLast = 0;
        for (var lesson : book.lessons()) {
            assertTrue(lesson.firstPage() > previousLast, lesson.name());
            previousLast = lesson.lastPage();
            assertTrue(lesson.description().length() <= 255, lesson.name());
            assertTrue(lesson.reading().contains("<h4>会話"), lesson.name());
            for (var grammar : lesson.grammars()) {
                assertTrue(grammar.description().startsWith("<p>Cấu trúc: "), grammar.title());
                assertFalse(grammar.examples().isEmpty(), grammar.title());
                grammar.examples().forEach(e -> assertFalse(e.nihongo().isBlank() || e.vietnamese().isBlank(), grammar.title()));
            }
        }
    }
}

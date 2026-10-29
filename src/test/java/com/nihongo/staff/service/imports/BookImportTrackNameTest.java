package com.nihongo.staff.service.imports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookImportTrackNameTest {
    @Test void exerciseAudioMatchesTheWholeTrackName() {
        assertEquals("練習 B1", BookImportService.trackName("練習 B1 · Luyện tập B1"));
        assertEquals("問題 2", BookImportService.trackName("問題 2 · Bài tập nghe 2"));
        assertEquals("練習 A1", BookImportService.trackName(" 練習 A1 "));
    }
}

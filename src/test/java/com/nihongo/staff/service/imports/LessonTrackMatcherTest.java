package com.nihongo.staff.service.imports;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LessonTrackMatcherTest {
    private static BookImportContent.Lesson lesson(String name) {
        return new BookImportContent.Lesson(name, "", 1, 1, "", "", List.of(), List.of());
    }
    private static BookImportContent.Asset asset(String id, String name) {
        return new BookImportContent.Asset(id, name, "audio/mpeg", "mp3", 1000);
    }

    @Test void matchesMinnaFilesToLessonsByNumberAndOrdersThemByTrack() {
        var lessons = List.of(lesson("第26課 見に行ってもいいですか"), lesson("Bài 27"), lesson("Lời nói đầu"));
        var audio = List.of(
                asset("b", "02 第26課 文型 Bunkei.mp3"),
                asset("a", "01 第26課 ことば Kotoba.mp3"),
                asset("c", "04 第26課 練習 Renshu A1.mp3"),
                asset("d", "20 第27課 ことば Kotoba.mp3"),
                asset("x", "99 第49課 例文 Reibun.mp3"),
                asset("y", "cover.mp3"));
        var tracks = LessonTrackMatcher.assign(lessons, audio);
        assertEquals(List.of("a", "b", "c"), tracks.get(0).stream().map(LessonTrackMatcher.Track::assetId).toList());
        assertEquals(List.of("ことば · Từ vựng", "文型 · Mẫu câu", "練習 A1 · Luyện tập A1"),
                tracks.get(0).stream().map(LessonTrackMatcher.Track::label).toList());
        assertEquals(List.of("d"), tracks.get(1).stream().map(LessonTrackMatcher.Track::assetId).toList());
        assertNull(tracks.get(2));
    }

    @Test void readsLessonNumbersInDifferentNamingStyles() {
        assertEquals(31, LessonTrackMatcher.lessonNumber("第３１課"));
        assertEquals(40, LessonTrackMatcher.lessonNumber("Bài 40: Tôi không biết"));
        assertEquals(26, LessonTrackMatcher.lessonNumber("26. Mẫu câu"));
        assertNull(LessonTrackMatcher.lessonNumber("Phụ lục"));
        assertEquals("例文 · Câu ví dụ", LessonTrackMatcher.label("例文 Reibun"));
        assertEquals("練習 C3 · Luyện tập C3", LessonTrackMatcher.label("練習 Renshu C3"));
    }
}

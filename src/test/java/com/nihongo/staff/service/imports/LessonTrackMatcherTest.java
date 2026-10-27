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
        assertEquals("問題 2 · Bài tập nghe 2", LessonTrackMatcher.label("問題 2"));
    }

    @Test void spreadsANumberedTextbookCdOverTheLessonsThreeTracksEach() {
        var lessons = new java.util.ArrayList<BookImportContent.Lesson>();
        for (int n = 26; n <= 50; n++) lessons.add(lesson("第" + n + "課"));
        var audio = new java.util.ArrayList<BookImportContent.Asset>();
        for (int n = 75; n >= 1; n--) audio.add(asset("t" + n, String.format("minna_shokyu_2_%03d.mp3", n)));
        var tracks = LessonTrackMatcher.assignDisc(lessons, audio);
        assertEquals(25, tracks.size());
        assertEquals(List.of("t4", "t5", "t6"), tracks.get(1).stream().map(LessonTrackMatcher.Track::assetId).toList());
        assertEquals(List.of("会話 · Hội thoại", "問題 1 · Bài tập nghe 1", "問題 2 · Bài tập nghe 2"),
                tracks.get(1).stream().map(LessonTrackMatcher.Track::label).toList());
        assertEquals(List.of("t73", "t74", "t75"), tracks.get(24).stream().map(LessonTrackMatcher.Track::assetId).toList());
        // More tracks than the book has lessons, or files named by lesson: not a CD of this book.
        audio.add(asset("t76", "minna_shokyu_2_076.mp3"));
        assertTrue(LessonTrackMatcher.assignDisc(lessons, audio).isEmpty());
        assertTrue(LessonTrackMatcher.assignDisc(lessons, List.of(asset("a", "01 第26課 ことば.mp3"))).isEmpty());
    }

    @Test void ordersTheBookPartsTheWayTheLessonTeachesThem() {
        var labels = new java.util.ArrayList<>(List.of("問題 1 · Bài tập nghe 1", "練習 A1 · Luyện tập A1", "会話 · Hội thoại", "ことば · Từ vựng", "例文 · Câu ví dụ"));
        labels.sort(java.util.Comparator.comparingInt(LessonTrackMatcher::rank));
        assertEquals(List.of("ことば · Từ vựng", "例文 · Câu ví dụ", "会話 · Hội thoại", "練習 A1 · Luyện tập A1", "問題 1 · Bài tập nghe 1"), labels);
    }
}

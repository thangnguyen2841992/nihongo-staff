package com.nihongo.staff.service.imports;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Attaches uploaded audio files to lessons by their names, e.g. "04 第26課 練習 Renshu A1.mp3" goes to the lesson
 * named "第26課 …" / "Bài 26 …" as the track "練習 A1 · Luyện tập A1", ordered by the leading CD track number.
 * Files without a lesson number (第N課) are left for the editor to assign by hand.
 */
public final class LessonTrackMatcher {
    private LessonTrackMatcher() {}

    public record Track(String assetId, String label, int order) {}

    private static final Pattern FILE = Pattern.compile("^\\s*(\\d{1,4})?[\\s._-]*第\\s*(\\d{1,3})\\s*課\\s*(.*?)\\s*(?:\\.[A-Za-z0-9]{2,4})?$");
    private static final Pattern LESSON = Pattern.compile("第\\s*(\\d{1,3})\\s*課|(?i:bài|lesson|unit|課)\\s*(\\d{1,3})|^\\s*(\\d{1,3})(?:[.:\\s)]|$)");
    private static final Pattern PRACTICE = Pattern.compile("(?i)練習\\s*(?:renshu)?\\s*([ABC])\\s*-?\\s*(\\d{0,2})");

    static String normalize(String text) {
        return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFKC);
    }

    /** Lesson number written in a lesson name ("第26課", "Bài 26", "26. …"), or null. */
    static Integer lessonNumber(String name) {
        Matcher m = LESSON.matcher(normalize(name));
        if (!m.find()) return null;
        for (int group = 1; group <= m.groupCount(); group++) if (m.group(group) != null) return Integer.parseInt(m.group(group));
        return null;
    }

    /** Readable label for the part after "第N課" in a file name. */
    static String label(String rest) {
        String text = normalize(rest).trim();
        Matcher practice = PRACTICE.matcher(text);
        if (practice.find()) {
            String part = practice.group(1).toUpperCase(Locale.ROOT) + practice.group(2);
            return "練習 " + part + " · Luyện tập " + part;
        }
        if (text.contains("ことば") || text.toLowerCase(Locale.ROOT).contains("kotoba")) return "ことば · Từ vựng";
        if (text.contains("文型") || text.toLowerCase(Locale.ROOT).contains("bunkei")) return "文型 · Mẫu câu";
        if (text.contains("例文") || text.toLowerCase(Locale.ROOT).contains("reibun")) return "例文 · Câu ví dụ";
        if (text.contains("会話") || text.toLowerCase(Locale.ROOT).contains("kaiwa")) return "会話 · Hội thoại";
        if (text.contains("問題") || text.toLowerCase(Locale.ROOT).contains("mondai")) return "問題 · Bài tập nghe";
        if (text.contains("読み物") || text.toLowerCase(Locale.ROOT).contains("yomimono")) return "読み物 · Bài đọc";
        return text.isBlank() ? "File nghe" : text.substring(0, Math.min(text.length(), 110));
    }

    /** Tracks per lesson index (lessons without a number in their name get none). */
    public static Map<Integer, List<Track>> assign(List<BookImportContent.Lesson> lessons, List<BookImportContent.Asset> audio) {
        Map<Integer, Integer> lessonByNumber = new HashMap<>();
        for (int index = 0; index < lessons.size(); index++) {
            Integer number = lessonNumber(lessons.get(index).name());
            if (number != null) lessonByNumber.putIfAbsent(number, index);
        }
        Map<Integer, List<Track>> result = new TreeMap<>();
        for (int position = 0; position < audio.size(); position++) {
            var asset = audio.get(position);
            Matcher m = FILE.matcher(normalize(asset.name()));
            if (!m.matches()) continue;
            Integer lesson = lessonByNumber.get(Integer.parseInt(m.group(2)));
            if (lesson == null) continue;
            int order = m.group(1) == null ? 10_000 + position : Integer.parseInt(m.group(1));
            result.computeIfAbsent(lesson, ignored -> new ArrayList<>()).add(new Track(asset.id(), label(m.group(3)), order));
        }
        result.values().forEach(tracks -> tracks.sort(Comparator.comparingInt(Track::order)));
        return result;
    }
}

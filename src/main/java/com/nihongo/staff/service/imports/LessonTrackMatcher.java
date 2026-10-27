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
    private static final Pattern TRACK_NUMBER = Pattern.compile("(\\d{1,3})\\s*$");
    private static final Pattern EXTENSION = Pattern.compile("\\.[A-Za-z0-9]{2,4}$");
    private static final Pattern LISTENING = Pattern.compile("(?:問題|(?i:mondai))\\s*(\\d{1,2})");
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
        if (text.contains("問題") || text.toLowerCase(Locale.ROOT).contains("mondai")) {
            Matcher number = LISTENING.matcher(text);
            return number.find() ? "問題 " + number.group(1) + " · Bài tập nghe " + number.group(1) : "問題 · Bài tập nghe";
        }
        if (text.contains("読み物") || text.toLowerCase(Locale.ROOT).contains("yomimono")) return "読み物 · Bài đọc";
        return text.isBlank() ? "File nghe" : text.substring(0, Math.min(text.length(), 110));
    }

    /** Display order of a lesson's parts: ことば, 文型, 例文, 会話, 練習 A/B/C, 問題, others. */
    public static int rank(String label) {
        String text = normalize(label);
        if (text.startsWith("ことば")) return 0;
        if (text.startsWith("文型")) return 1;
        if (text.startsWith("例文")) return 2;
        if (text.startsWith("会話")) return 3;
        if (text.startsWith("練習")) return 4;
        if (text.startsWith("問題")) return 5;
        return 6;
    }

    /**
     * Minna no Nihongo's textbook CD: files carry only a track number ("minna_shokyu_2_004.mp3", "CD04.mp3") and every
     * lesson has three consecutive tracks, 会話 then 問題 1 and 問題 2, starting with the book's first lesson (track 1 is
     * 会話 of 第26課 in 初級II). Used only when every file has a track number and they fit the book's lessons exactly.
     */
    public static Map<Integer, List<Track>> assignDisc(List<BookImportContent.Lesson> lessons, List<BookImportContent.Asset> audio) {
        var numbered = new TreeMap<Integer, Integer>();
        for (int index = 0; index < lessons.size(); index++) {
            Integer number = lessonNumber(lessons.get(index).name());
            if (number != null) numbered.putIfAbsent(number, index);
        }
        Map<Integer, List<Track>> result = new TreeMap<>();
        if (numbered.isEmpty() || audio.isEmpty() || numbered.lastKey() - numbered.firstKey() + 1 != numbered.size()) return result;
        var tracks = new TreeMap<Integer, BookImportContent.Asset>();
        for (var asset : audio) {
            String name = normalize(asset.name());
            if (FILE.matcher(name).matches()) return Map.of();
            Matcher m = TRACK_NUMBER.matcher(EXTENSION.matcher(name).replaceFirst(""));
            if (!m.find() || tracks.put(Integer.parseInt(m.group(1)), asset) != null) return Map.of();
        }
        if (tracks.firstKey() < 1 || tracks.lastKey() > numbered.size() * 3) return Map.of();
        var lessonIndexes = new ArrayList<>(numbered.values());
        for (var entry : tracks.entrySet()) {
            int track = entry.getKey() - 1, lesson = lessonIndexes.get(track / 3), part = track % 3;
            String label = part == 0 ? "会話 · Hội thoại" : "問題 " + part + " · Bài tập nghe " + part;
            result.computeIfAbsent(lesson, ignored -> new ArrayList<>()).add(new Track(entry.getValue().id(), label, entry.getKey()));
        }
        return result;
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

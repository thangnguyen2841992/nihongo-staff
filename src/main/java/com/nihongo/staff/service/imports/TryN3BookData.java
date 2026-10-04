package com.nihongo.staff.service.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;

@Component
public class TryN3BookData {
    private final BookData book;
    private final ObjectMapper mapper;
    public TryN3BookData(ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        try (var stream = new ClassPathResource("imports/try-n3/book.json").getInputStream()) {
            book = mapper.readValue(stream, BookData.class);
        }
        if (book.schemaVersion() != 1 || book.chapters().size() != 11 || book.pageCount() != 209
                || book.chapters().stream().mapToInt(ChapterIndex::lessonCount).sum() != 21
                || book.chapters().stream().mapToInt(ChapterIndex::grammarCount).sum() != 113)
            throw new IOException("Danh mục sách TRY N3 không hợp lệ");
    }
    public BookData book() { return book; }
    public ChapterIndex chapter(int number) {
        return book.chapters().stream().filter(c -> c.number() == number).findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }
    public Object draft(int number) throws IOException {
        chapter(number);
        try (var stream = new ClassPathResource("imports/try-n3/drafts/chapter-" + number + ".json").getInputStream()) {
            return mapper.readTree(stream);
        }
    }
    public TryN3ChapterData.Chapter reviewedChapter(int number) throws IOException {
        if (!chapter(number).reviewed()) return null;
        try (var stream = new ClassPathResource("imports/try-n3/chapter-" + number + ".json").getInputStream()) {
            return mapper.readValue(stream, TryN3ChapterData.Chapter.class);
        }
    }
    public record BookData(int schemaVersion, String sourceKey, String bookName, String levelName, String typeName,
                           String sourceSha256, int pageCount, int lessonCount, int grammarCount, List<Integer> answerPdfPages, List<ChapterIndex> chapters) {}
    public record ChapterIndex(int number, String title, boolean reviewed, int lessonCount, int grammarCount,
                               List<Integer> sourcePdfPages, List<String> lessonNames) {}
}

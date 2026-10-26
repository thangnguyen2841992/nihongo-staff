package com.nihongo.staff.service.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Prepared content of Minna no Nihongo Shokyu II, 2nd edition (lessons 26-50): 文型, 会話 and grammar points with
 * Vietnamese explanations and translated examples. Used instead of Gemini when the uploaded PDF has exactly this
 * fingerprint; the listening files are matched per lesson by their 第N課 file names when the book is published.
 */
@Component
public class MinnaBookData {
    private final Book book;

    public MinnaBookData(ObjectMapper mapper) throws IOException {
        try (var stream = new ClassPathResource("imports/minna-2/book.json").getInputStream()) {
            book = mapper.readValue(stream, Book.class);
        }
        if (book.schemaVersion() != 1 || book.lessons().size() != 25 || book.pageCount() != 330
                || book.lessons().stream().anyMatch(l -> l.grammars().isEmpty() || l.firstPage() < 1 || l.lastPage() > book.pageCount()))
            throw new IOException("Dữ liệu Minna no Nihongo II không hợp lệ");
    }

    public Book book() { return book; }

    public record Book(int schemaVersion, String sourceKey, String bookName, String levelName, String sourceSha256,
                       int pageCount, List<BookImportContent.Lesson> lessons) {}
}

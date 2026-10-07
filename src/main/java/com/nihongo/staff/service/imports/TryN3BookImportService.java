package com.nihongo.staff.service.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.*;
import com.nihongo.staff.repository.*;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class TryN3BookImportService {
    private final TryN3BookData data;
    private final TryN3ChapterData trial;
    private final TryN3ImportService writer;
    private final BookContentImportRepository imports;
    private final BookImportDraftRepository drafts;
    private final IBookRepository books;
    private final ILessonsRepository lessons;
    private final ObjectMapper mapper;

    public String chapterKey(int number) {
        return number == 1 ? trial.chapter().sourceKey() : "try-n3-vietnamese:386895b5:chapter-" + number + ":v1";
    }
    @Transactional(readOnly = true)
    public Preview preview() {
        var root = imports.findById(data.book().sourceKey()).or(() -> imports.findById(chapterKey(1)));
        Long bookId = root.map(i -> i.getBook().getBookId()).orElse(null);
        var rows = data.book().chapters().stream().map(c -> {
            var marker = imports.findById(chapterKey(c.number()));
            boolean imported = marker.isPresent() && Objects.equals(marker.get().getBook().getBookId(), bookId);
            boolean reviewed = c.reviewed() || drafts.existsById(chapterKey(c.number()));
            return new ChapterStatus(c.number(), c.title(), c.lessonCount(), c.grammarCount(), c.sourcePdfPages(), reviewed, imported);
        }).toList();
        return new Preview(data.book().bookName(), bookId, rows.stream().allMatch(ChapterStatus::imported), rows, data.book().answerPdfPages());
    }
    @Transactional(readOnly = true)
    public Object draft(int number) throws Exception {
        data.chapter(number);
        if (number == 1) return trial.chapter();
        var saved = drafts.findById(chapterKey(number));
        if (saved.isPresent()) return mapper.readTree(saved.get().getPayload());
        var reviewed = data.reviewedChapter(number);
        return reviewed != null ? reviewed : data.draft(number);
    }
    @Transactional
    public Preview review(int number, Review request) throws Exception {
        data.chapter(number);
        if (!request.reviewed()) throw conflict("Hãy đối chiếu nội dung và đáp án trước khi duyệt.");
        if (number == 1 || imports.existsById(chapterKey(number))) throw conflict("Chương đã nhập; chỉnh nội dung tại trang quản lý sách.");
        validate(number, request.content());
        var record = drafts.findById(chapterKey(number)).orElseGet(BookImportDraft::new);
        record.setSourceKey(chapterKey(number)); record.setPayload(mapper.writeValueAsString(request.content()));
        record.setReviewedAt(LocalDateTime.now()); drafts.saveAndFlush(record);
        return preview();
    }
    @Transactional
    @CacheEvict(value = {"books", "exerciseTypes"}, allEntries = true)
    public Preview importChapter(int number) throws Exception {
        data.chapter(number);
        // Resolve and validate before any write. Unreviewed OCR cannot become a graded lesson.
        TryN3ChapterData.Chapter content;
        if (number == 1) content = trial.chapter();
        else {
            var saved = drafts.findById(chapterKey(number));
            content = saved.isPresent() ? mapper.readValue(saved.get().getPayload(), TryN3ChapterData.Chapter.class) : data.reviewedChapter(number);
            if (content == null) throw conflict("Chương " + number + " chưa được đối chiếu và duyệt.");
        }
        validate(number, content);
        if (number == 1) writer.importChapter();
        var root = imports.lockByKey(data.book().sourceKey());
        if (root.isEmpty()) {
            // Adopt the existing trial. Its edits, lesson IDs and existing course links are preserved.
            var first = imports.lockByKey(chapterKey(1)).orElseThrow(() -> conflict("Hãy nhập chương 1 trước."));
            var marker = new BookContentImport(); marker.setSourceKey(data.book().sourceKey()); marker.setBook(first.getBook());
            imports.saveAndFlush(marker); root = Optional.of(marker);
        }
        var book = root.get().getBook();
        if (book.getPublicationStatus() != PublicationStatus.DRAFT)
            throw conflict("Hãy chuyển sách về bản nháp trước khi nhập thêm chương.");
        if (imports.existsById(chapterKey(number))) return preview();
        for (var earlier : data.book().chapters()) {
            if (earlier.number() >= number) break;
            if (!imports.existsById(chapterKey(earlier.number()))) throw conflict("Hãy nhập theo thứ tự chương; chương " + earlier.number() + " chưa nhập.");
        }
        if (lessons.findByBook_BookId(book.getBookId()).stream().anyMatch(l -> content.lessons().stream().anyMatch(c -> c.name().equals(l.getName()))))
            throw conflict("Có bài học trùng tên chưa được đánh dấu import; cần đối chiếu trước.");
        var marker = new BookContentImport(); marker.setSourceKey(chapterKey(number)); marker.setBook(book);
        imports.saveAndFlush(marker);
        writer.appendChapter(book, content);
        imports.flush();
        if (data.book().chapters().stream().allMatch(c -> imports.existsById(chapterKey(c.number())))) {
            book.setBookName(data.book().bookName());
            book.setDescription("TRY! N3 - Tiếng Việt: 11 chương, 21 phần học, 113 mục ngữ pháp. Nội dung chữ và đáp án được đối chiếu theo sách.");
            books.save(book);
        }
        return preview();
    }

    public void validate(int number, TryN3ChapterData.Chapter c) {
        var index = data.chapter(number);
        if (c == null || !chapterKey(number).equals(c.sourceKey()) || !"N3".equals(c.levelName()) || !"Ngữ Pháp".equals(c.typeName())
                || c.lessons() == null || c.lessons().size() != index.lessonCount()
                || c.sourcePdfPages() == null || !c.sourcePdfPages().equals(index.sourcePdfPages()))
            throw invalid("Mã nguồn, trang nguồn hoặc số phần học không khớp chương.");
        Set<Integer> grammarNumbers = new HashSet<>(); Set<String> questionKeys = new HashSet<>();
        int firstGrammar = data.book().chapters().stream().filter(i -> i.number() < number).mapToInt(TryN3BookData.ChapterIndex::grammarCount).sum() + 1;
        int totalQuestions = 0;
        for (int part = 0; part < c.lessons().size(); part++) {
            var l = c.lessons().get(part);
            if (l == null || !index.lessonNames().get(part).equals(l.name()) || empty(l.reading()) || l.grammars() == null || l.exercises() == null)
                throw invalid("Thiếu bài đọc, ngữ pháp hoặc phần bài tập.");
            if (l.audioTrack() == null || !l.audioTrack().matches("(?:0[1-9]|[1-5][0-9]|6[0-4])")) throw invalid("Chưa gắn CD hợp lệ cho bài đọc.");
            for (var g : l.grammars()) {
                if (g == null || !grammarNumbers.add(g.number()) || g.number() < firstGrammar || g.number() >= firstGrammar + index.grammarCount()
                        || empty(g.title()) || empty(g.description()) || g.examples() == null || g.examples().isEmpty()
                        || !index.sourcePdfPages().contains(g.sourcePrintedPage() - 1)) throw invalid("Mục ngữ pháp thiếu nội dung, sai số thứ tự hoặc trang nguồn.");
                for (var e : g.examples()) if (e == null || empty(e.nihongo()) || empty(e.vietnamese())) throw invalid("Ví dụ phải có tiếng Nhật và bản dịch tiếng Việt.");
            }
            for (var q : l.exercises()) {
                if (q == null || q.sourceKey() == null || !questionKeys.add(q.sourceKey()) || empty(q.contentNihongo()) || empty(q.exerciseTypeName())
                        || q.correctAnswer() == null || !q.correctAnswer().matches("[ABCD]")) throw invalid("Câu hỏi trùng hoặc thiếu nội dung/đáp án.");
                var options = Arrays.asList(q.answerA(), q.answerB(), q.answerC(), q.answerD());
                if (options.stream().filter(o -> !empty(o)).count() < 2 || empty(options.get("ABCD".indexOf(q.correctAnswer()))))
                    throw invalid("Đáp án đúng phải trỏ tới lựa chọn có nội dung.");
                totalQuestions++;
            }
        }
        if (grammarNumbers.size() != index.grammarCount() || totalQuestions == 0) throw invalid("Chưa đủ ngữ pháp hoặc chưa có bài ôn tập tương tác.");
    }
    private boolean empty(String value) { return value == null || Jsoup.parse(value).text().isBlank(); }
    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    public record Review(boolean reviewed, TryN3ChapterData.Chapter content) {}
    public record ChapterStatus(int number, String title, int lessonCount, int grammarCount, List<Integer> sourcePdfPages, boolean reviewed, boolean imported) {}
    public record Preview(String bookName, Long bookId, boolean complete, List<ChapterStatus> chapters, List<Integer> answerPdfPages) {}
}

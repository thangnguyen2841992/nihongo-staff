package com.nihongo.staff.service;

import com.nihongo.staff.model.*;
import com.nihongo.staff.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;

@Service @RequiredArgsConstructor
public class BookPublicationService {
    private final IBookRepository books;
    private final ILessonsRepository lessons;
    private final IExerciseKeywordRepository exercises;

    public record Review(Long bookId, PublicationStatus status, List<String> errors, List<String> warnings) {}

    @Transactional(readOnly = true)
    public Review inspect(Long id) {
        Books book = book(id);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (book.getBookName() == null || book.getBookName().isBlank()) errors.add("Sách chưa có tên.");
        var chapters = lessons.findByBook_BookId(id);
        if (chapters.isEmpty()) errors.add("Sách cần ít nhất một bài học.");
        for (Lessons lesson : chapters) {
            if (lesson.getName() == null || lesson.getName().isBlank()) errors.add("Bài " + lesson.getLessonId() + " chưa có tên.");
            if (lesson.getReading() == null || lesson.getReading().isBlank()) warnings.add("Bài " + lesson.getLessonId() + " chưa có nội dung đọc.");
            var questions = exercises.findByLessons_LessonId(lesson.getLessonId());
            if (questions.isEmpty()) warnings.add("Bài " + lesson.getLessonId() + " chưa có bài tập.");
            for (ExersiceKeyword question : questions)
                if (question.getCorrectAnswer() == null || !question.getCorrectAnswer().matches("[ABCD]"))
                    errors.add("Câu hỏi " + question.getExerciseKeywordId() + " chưa có đáp án hợp lệ.");
        }
        return new Review(id, status(book), errors, warnings);
    }

    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public Review submit(Long id) {
        Books book = book(id);
        if (status(book) != PublicationStatus.DRAFT) throw conflict("Chỉ bản nháp mới được gửi duyệt.");
        Review report = inspect(id);
        if (!report.errors().isEmpty()) throw conflict(String.join(" ", report.errors()));
        book.setPublicationStatus(PublicationStatus.IN_REVIEW);
        return new Review(id, PublicationStatus.IN_REVIEW, report.errors(), report.warnings());
    }

    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public Review publish(Long id) {
        Books book = book(id);
        if (status(book) != PublicationStatus.IN_REVIEW) throw conflict("Sách chưa được gửi duyệt.");
        Review report = inspect(id);
        if (!report.errors().isEmpty()) throw conflict(String.join(" ", report.errors()));
        book.setPublicationStatus(PublicationStatus.PUBLISHED);
        return new Review(id, PublicationStatus.PUBLISHED, report.errors(), report.warnings());
    }

    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public Review returnToDraft(Long id) {
        Books book = book(id);
        book.setPublicationStatus(PublicationStatus.DRAFT);
        return inspect(id);
    }

    public boolean visible(Books book) { return status(book) == PublicationStatus.PUBLISHED; }
    public PublicationStatus status(Books book) { return book.getPublicationStatus() == null ? PublicationStatus.PUBLISHED : book.getPublicationStatus(); }
    private Books book(Long id) { return books.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sách")); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}

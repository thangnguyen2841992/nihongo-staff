package com.nihongo.staff.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nihongo.staff.repository.ILessonsRepository;
import com.nihongo.staff.service.IStaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a course contains, for visitors deciding whether to buy it: the published books of the course's level and how
 * many lessons each has. No lesson content is exposed. Cached briefly because every catalog visitor may ask.
 */
@RestController
@RequestMapping("/api/staff/public")
@RequiredArgsConstructor
public class PublicSyllabusController {
    public record SyllabusBook(Long bookId, String bookName, long lessons) {}

    private final IStaffService staff;
    private final ILessonsRepository lessons;
    private final Cache<Long, List<SyllabusBook>> syllabi = Caffeine.newBuilder()
            .maximumSize(100).expireAfterWrite(Duration.ofMinutes(5)).build();

    @GetMapping("/levels/{levelId}/syllabus")
    public ResponseEntity<List<SyllabusBook>> syllabus(@PathVariable Long levelId) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(syllabi.get(levelId, this::load));
    }

    List<SyllabusBook> load(Long levelId) {
        var books = staff.getBooksByLevel(levelId).stream()
                .filter(book -> "PUBLISHED".equals(book.getPublicationStatus())).toList();
        if (books.isEmpty()) return List.of();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : lessons.countByBooks(books.stream().map(book -> book.getBookId()).toList()))
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        return books.stream().map(book -> new SyllabusBook(book.getBookId(), book.getBookName(),
                counts.getOrDefault(book.getBookId(), 0L))).toList();
    }
}

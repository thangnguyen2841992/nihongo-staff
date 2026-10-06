package com.nihongo.staff.repository;

import com.nihongo.staff.model.Lessons;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ILessonsRepository extends JpaRepository<Lessons, Long> {
    @org.springframework.data.jpa.repository.Query("select new com.nihongo.staff.model.dto.ContentLocationResponse(l.book.bookId, l.book.level.levelId, l.name) from Lessons l where l.lessonId = :lessonId")
    java.util.Optional<com.nihongo.staff.model.dto.ContentLocationResponse> findLocationById(Long lessonId);
    @org.springframework.data.jpa.repository.Query("select l from Lessons l where l.book.bookId = :bookId order by l.lessonId")
    List<Lessons> findByBook_BookId(@org.springframework.data.repository.query.Param("bookId") Long bookId);
}

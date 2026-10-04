package com.nihongo.staff.repository;

import com.nihongo.staff.model.Lessons;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ILessonsRepository extends JpaRepository<Lessons, Long> {
    @org.springframework.data.jpa.repository.Query("select l from Lessons l where l.book.bookId = :bookId order by l.lessonId")
    List<Lessons> findByBook_BookId(@org.springframework.data.repository.query.Param("bookId") Long bookId);
}

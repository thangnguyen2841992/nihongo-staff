package com.nihongo.staff.repository;

import com.nihongo.staff.model.Grammar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IGrammarRepository extends JpaRepository<Grammar, Long> {
    List<Grammar> findByLessons_LessonId(Long lessonId);
}

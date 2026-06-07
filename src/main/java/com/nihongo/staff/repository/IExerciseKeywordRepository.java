package com.nihongo.staff.repository;

import com.nihongo.staff.model.ExersiceKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IExerciseKeywordRepository extends JpaRepository<ExersiceKeyword, Long> {
    List<ExersiceKeyword> findByLessons_LessonId(Long lessonId);
}

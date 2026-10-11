package com.nihongo.staff.repository;

import com.nihongo.staff.model.ExersiceKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IExerciseKeywordRepository extends JpaRepository<ExersiceKeyword, Long> {
    interface ReviewQuestion {
        Long getId();
        Long getLessonId();
        String getCorrectAnswer();
    }

    @org.springframework.data.jpa.repository.Query("select e.exerciseKeywordId as id, e.lessons.lessonId as lessonId, e.correctAnswer as correctAnswer from ExersiceKeyword e where e.lessons.book.bookId = :bookId order by e.exerciseKeywordId")
    List<ReviewQuestion> findReviewQuestions(Long bookId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"lessons","lessons.book","lessons.book.level","exerciseType"})
    @org.springframework.data.jpa.repository.Query("select e from ExersiceKeyword e where e.exerciseKeywordId in :ids")
    List<ExersiceKeyword> findForExam(java.util.Collection<Long> ids);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"lessons","lessons.book","lessons.book.level","exerciseType"})
    @org.springframework.data.jpa.repository.Query("select e from ExersiceKeyword e left join QuestionMetadata m on m.exerciseId=e.exerciseKeywordId where (:level is null or e.lessons.book.level.levelId=:level) and (:type is null or e.exerciseType.ExerciseTypeId=:type) and (:difficulty is null or coalesce(m.difficulty,'NORMAL')=:difficulty) and lower(cast(e.contentNihongo as String)) like :search order by e.exerciseKeywordId desc")
    org.springframework.data.domain.Page<ExersiceKeyword> bank(Long level,Long type,String difficulty,String search,org.springframework.data.domain.Pageable page);
    List<ExersiceKeyword> findByLessons_LessonId(Long lessonId);
}

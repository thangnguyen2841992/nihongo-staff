package com.nihongo.staff.repository;

import com.nihongo.staff.model.Example;
import com.nihongo.staff.model.Grammar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IExampleRepository extends JpaRepository<Example, Long> {
    List<Example> findByGrammar_GrammarId(Long grammarId);

    @Query("select e from Example e join fetch e.grammar g where g.lessons.lessonId = :lessonId order by g.grammarId, e.exampleId")
    List<Example> findByLessonIdWithGrammar(@Param("lessonId") Long lessonId);
}

package com.nihongo.staff.repository;

import com.nihongo.staff.model.Example;
import com.nihongo.staff.model.Grammar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IExampleRepository extends JpaRepository<Example, Long> {
    List<Example> findByGrammar_GrammarId(Long grammarId);
}

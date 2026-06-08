package com.nihongo.staff.repository;

import com.nihongo.staff.model.ExerciseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IExerciseTypeRepository extends JpaRepository<ExerciseType, Long> {
    boolean existsByName(String name);
}

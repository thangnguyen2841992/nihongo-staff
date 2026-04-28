package com.nihongo.staff.repository;

import com.nihongo.staff.model.Levels;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ILevelsRepository extends JpaRepository<Levels, Long> {
    boolean existsByLevelName(String levelName);

    Optional<Levels> findByLevelName(String levelName);
}

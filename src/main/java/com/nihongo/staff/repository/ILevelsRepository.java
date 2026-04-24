package com.nihongo.staff.repository;

import com.nihongo.staff.model.Levels;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ILevelsRepository extends JpaRepository<Levels, Long> {
    boolean existsByLevelName(String levelName);
}

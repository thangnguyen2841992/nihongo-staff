package com.nihongo.staff.repository;

import com.nihongo.staff.model.Lessons;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ILessonsRepository extends JpaRepository<Lessons, Integer> {
}

package com.nihongo.staff.repository;

import com.nihongo.staff.model.Example;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IExampleRepository extends JpaRepository<Example, Long> {
}

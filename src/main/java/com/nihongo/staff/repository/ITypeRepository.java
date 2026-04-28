package com.nihongo.staff.repository;

import com.nihongo.staff.model.Types;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ITypeRepository extends JpaRepository<Types, Long> {
    boolean existsByTypeName(String typeName);

    Optional<Types> findByTypeName(String typeName);
}

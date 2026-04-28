package com.nihongo.staff.repository;

import com.nihongo.staff.model.Images;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IImageRepository extends JpaRepository<Images, Integer> {
}

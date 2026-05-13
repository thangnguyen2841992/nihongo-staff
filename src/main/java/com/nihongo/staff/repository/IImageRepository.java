package com.nihongo.staff.repository;

import com.nihongo.staff.model.Images;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IImageRepository extends JpaRepository<Images, Long> {
    List<Images> findByBooks_BookId(Long bookId);

}

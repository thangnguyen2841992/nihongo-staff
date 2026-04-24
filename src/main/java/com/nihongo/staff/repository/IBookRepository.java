package com.nihongo.staff.repository;

import com.nihongo.staff.model.Books;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;



@Repository
public interface IBookRepository  extends JpaRepository<Books, Long> {
}

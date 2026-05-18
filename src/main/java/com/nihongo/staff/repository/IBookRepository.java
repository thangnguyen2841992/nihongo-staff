package com.nihongo.staff.repository;

import com.nihongo.staff.model.Books;
import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface IBookRepository  extends JpaRepository<Books, Long> {
    List<Books> findByLevel_LevelIdAndTypes_TypeId(
            Long levelId,
            Long typeId
    );
}

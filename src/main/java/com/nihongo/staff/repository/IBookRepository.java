package com.nihongo.staff.repository;

import com.nihongo.staff.model.Books;
import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface IBookRepository extends JpaRepository<Books, Long> {
    @Query("select new com.nihongo.staff.model.dto.ContentLocationResponse(b.bookId, b.level.levelId, b.bookName) from Books b where b.bookId = :bookId")
    java.util.Optional<com.nihongo.staff.model.dto.ContentLocationResponse> findLocationById(Long bookId);
    List<Books> findByLevel_LevelIdAndTypes_TypeId(
            Long levelId,
            Long typeId
    );

    @Query("""
            SELECT b
            FROM Books b
            JOIN FETCH b.level
            JOIN FETCH b.types
            """)
    List<Books> findAllWithRelations();

    List<Books> findByLevel_LevelId(Long levelId);


}

package com.nihongo.staff.repository;
import com.nihongo.staff.model.BookImportSession;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface BookImportSessionRepository extends JpaRepository<BookImportSession,String> {
    Optional<BookImportSession> findBySourceHash(String hash);
    List<BookImportSession> findTop50ByOrderByCreatedAtDesc();
    List<BookImportSession> findByStateIn(Collection<String> states);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from BookImportSession s where s.id=:id")
    Optional<BookImportSession> lock(@Param("id") String id);
}

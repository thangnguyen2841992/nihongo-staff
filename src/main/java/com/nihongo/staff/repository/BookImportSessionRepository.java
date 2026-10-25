package com.nihongo.staff.repository;
import com.nihongo.staff.model.BookImportSession;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface BookImportSessionRepository extends JpaRepository<BookImportSession,String> {
    Optional<BookImportSession> findBySourceHash(String hash);
    List<BookImportSession> findTop50ByOrderByCreatedAtDesc();
    /** Status columns only: listing and polling must not load the LONGTEXT draft, warnings or audio hints. */
    interface Row { String getId(); Long getVersion(); String getFileName(); String getState(); int getPageCount();
        int getProcessedPages(); String getMessage(); Long getImportedBookId(); String getAutoMode();
        int getAiProcessedPages(); boolean getAiCompleted(); }
    String ROW = "select s.id as id, s.version as version, s.fileName as fileName, s.state as state, s.pageCount as pageCount, "
            + "s.processedPages as processedPages, s.message as message, s.importedBookId as importedBookId, s.autoMode as autoMode, "
            + "s.aiProcessedPages as aiProcessedPages, s.aiCompleted as aiCompleted from BookImportSession s";
    @Query(ROW + " order by s.createdAt desc")
    List<Row> recent(org.springframework.data.domain.Pageable page);
    @Query(ROW + " where s.id=:id")
    Optional<Row> row(@Param("id") String id);
    @Query("select s.payload from BookImportSession s where s.id=:id")
    Optional<String> payload(@Param("id") String id);
    List<BookImportSession> findByStateIn(Collection<String> states);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from BookImportSession s where s.id=:id")
    Optional<BookImportSession> lock(@Param("id") String id);
}

package com.nihongo.staff.repository;

import com.nihongo.staff.model.BookContentImport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookContentImportRepository extends JpaRepository<BookContentImport, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from BookContentImport i where i.sourceKey = :key")
    java.util.Optional<BookContentImport> lockByKey(@org.springframework.data.repository.query.Param("key") String key);
}

package com.nihongo.staff.repository;
import com.nihongo.staff.model.BookImportPage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BookImportPageRepository extends JpaRepository<BookImportPage,String> {
    List<BookImportPage> findBySessionIdOrderByPageNumberAsc(String id);
    long countBySessionIdAndMethodNot(String sessionId, String method);
}

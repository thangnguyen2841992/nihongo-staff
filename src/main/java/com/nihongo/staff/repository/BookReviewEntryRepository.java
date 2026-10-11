package com.nihongo.staff.repository;
import com.nihongo.staff.model.BookReviewEntry;import org.springframework.data.jpa.repository.*;import org.springframework.data.domain.*;
public interface BookReviewEntryRepository extends JpaRepository<BookReviewEntry,Long> {
 Page<BookReviewEntry> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(java.time.LocalDateTime from,java.time.LocalDateTime to,Pageable page);
 Page<BookReviewEntry> findByBookIdOrderByCreatedAtDescIdDesc(Long bookId,Pageable page);
 Page<BookReviewEntry> findAllByOrderByCreatedAtDescIdDesc(Pageable page);
}

package com.nihongo.staff.repository;
import com.nihongo.staff.model.ContentIssue;import org.springframework.data.jpa.repository.*;import org.springframework.data.domain.*;import java.time.LocalDateTime;
public interface ContentIssueRepository extends JpaRepository<ContentIssue,Long> {
 long countByReporterAndCreatedAtAfter(String reporter,LocalDateTime since);
 @Query("select i from ContentIssue i where (:status is null or i.status=:status) and (:assignee is null or i.assignee=:assignee) order by i.createdAt desc,i.id desc")
 Page<ContentIssue> search(String status,String assignee,Pageable page);
 Page<ContentIssue> findByReporterOrderByCreatedAtDescIdDesc(String reporter,Pageable page);
}

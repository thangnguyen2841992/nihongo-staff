package com.nihongo.staff.repository;
import com.nihongo.staff.model.ContentRevision;import org.springframework.data.jpa.repository.*;import org.springframework.data.domain.*;
public interface ContentRevisionRepository extends JpaRepository<ContentRevision,Long> {
 interface AuditRow {Long getId();String getActor();String getAction();String getKind();Long getResourceId();Long getBookId();java.time.LocalDateTime getCreatedAt();}
 @Query("select r.id as id,r.actor as actor,r.action as action,r.kind as kind,r.resourceId as resourceId,r.bookId as bookId,r.createdAt as createdAt from ContentRevision r where r.createdAt>=:from and r.createdAt<:to order by r.createdAt desc,r.id desc") Page<AuditRow> export(java.time.LocalDateTime from,java.time.LocalDateTime to,Pageable page);
 Page<ContentRevision> findByKindAndResourceIdOrderByCreatedAtDescIdDesc(String kind,Long resourceId,Pageable page);
 Page<ContentRevision> findAllByOrderByCreatedAtDescIdDesc(Pageable page);
}

package com.nihongo.staff.repository;
import com.nihongo.staff.model.ContentOperationAudit;import org.springframework.data.jpa.repository.JpaRepository;import org.springframework.data.domain.*;
public interface ContentOperationAuditRepository extends JpaRepository<ContentOperationAudit,Long>{Page<ContentOperationAudit> findAllByOrderByCreatedAtDescIdDesc(Pageable page);Page<ContentOperationAudit> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(java.time.LocalDateTime from,java.time.LocalDateTime to,Pageable page);}

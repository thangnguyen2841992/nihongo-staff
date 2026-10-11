package com.nihongo.staff.repository;
import com.nihongo.staff.model.ExamTemplate;import org.springframework.data.jpa.repository.*;import org.springframework.data.domain.*;
public interface ExamTemplateRepository extends JpaRepository<ExamTemplate,Long> {
 Page<ExamTemplate> findAllByOrderByCreatedAtDescIdDesc(Pageable page);
}

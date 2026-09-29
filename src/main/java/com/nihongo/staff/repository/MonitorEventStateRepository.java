package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorEventState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MonitorEventStateRepository extends JpaRepository<MonitorEventState, Long> {
    List<MonitorEventState> findByRuleIdIn(List<Long> ids);
    void deleteByRuleId(Long id);
}

package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorEventRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MonitorEventRuleRepository extends JpaRepository<MonitorEventRule, Long> {
    List<MonitorEventRule> findByVpsIdAndMetricIdOrderByRuleIdAsc(Long vpsId, Long metricId);
    List<MonitorEventRule> findByVpsIdAndMetricIdAndEnabledTrue(Long vpsId, Long metricId);
    long countByVpsIdAndMetricId(Long vpsId, Long metricId);
}

package com.nihongo.staff.repository;
import com.nihongo.staff.model.monitoring.MonitorPerfBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface MonitorPerfBaselineRepository extends JpaRepository<MonitorPerfBaseline, Long> {
    Optional<MonitorPerfBaseline> findByVpsMetricIdAndObjectKey(Long vpsMetricId, String objectKey);
}

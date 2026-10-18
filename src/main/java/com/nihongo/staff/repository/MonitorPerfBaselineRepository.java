package com.nihongo.staff.repository;
import com.nihongo.staff.model.monitoring.MonitorPerfBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface MonitorPerfBaselineRepository extends JpaRepository<MonitorPerfBaseline, Long> {
    Optional<MonitorPerfBaseline> findByVpsMetricIdAndObjectKey(Long vpsMetricId, String objectKey);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from MonitorPerfBaseline b where b.vpsMetricId = :metricId and b.objectKey in :keys")
    java.util.List<MonitorPerfBaseline> findKeysForUpdate(Long metricId, java.util.Collection<String> keys);
}

package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorVpsMetric;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDateTime;
import java.util.*;

public interface MonitorVpsMetricRepository extends JpaRepository<MonitorVpsMetric, Long> {
    boolean existsByVps_VpsIdAndMetric_MetricId(Long vpsId, Long metricId);
    @EntityGraph(attributePaths = {"vps", "metric"})
    List<MonitorVpsMetric> findByVps_VpsIdOrderByMetric_MetricNameAsc(Long vpsId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MonitorVpsMetric a where a.vpsMetricId = :id")
    Optional<MonitorVpsMetric> lockById(Long id);
    @Query("select a.vpsMetricId from MonitorVpsMetric a where a.enabled = true and a.metric.enabled = true and (a.nextCollectionAt is null or a.nextCollectionAt <= :now) and (a.leaseUntil is null or a.leaseUntil <= :now) order by a.nextCollectionAt asc, a.vpsMetricId asc")
    List<Long> findDue(LocalDateTime now, Pageable page);
}

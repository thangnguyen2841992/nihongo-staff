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
    @EntityGraph(attributePaths = {"vps", "metric"})
    Optional<MonitorVpsMetric> findByVps_VpsIdAndMetric_MetricCode(Long vpsId, String metricCode);
    @EntityGraph(attributePaths = {"vps", "metric"})
    List<MonitorVpsMetric> findByMetric_MetricId(Long metricId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MonitorVpsMetric a where a.vpsMetricId = :id")
    Optional<MonitorVpsMetric> lockById(Long id);
    interface Due { Long getId(); Long getVpsId(); }
    @Query("select a.vpsMetricId as id, a.vps.vpsId as vpsId from MonitorVpsMetric a where a.enabled = true and a.metric.enabled = true and (a.nextCollectionAt is null or a.nextCollectionAt <= :now) and (a.leaseUntil is null or a.leaseUntil <= :now) order by a.nextCollectionAt asc, a.vpsMetricId asc")
    List<Due> findDue(LocalDateTime now, Pageable page);
    @Query("select a.vps.vpsId from MonitorVpsMetric a where a.vpsMetricId = :id")
    Optional<Long> findVpsId(Long id);
    // No join in the locking query: on MariaDB a joined FOR UPDATE would also lock the shared metric/VPS rows.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MonitorVpsMetric a where a.vps.vpsId = :vpsId and a.enabled = true and (a.nextCollectionAt is null or a.nextCollectionAt <= :now) and (a.leaseUntil is null or a.leaseUntil <= :now) order by a.vpsMetricId")
    List<MonitorVpsMetric> lockDueForVps(Long vpsId, LocalDateTime now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MonitorVpsMetric a where a.vpsMetricId in :ids order by a.vpsMetricId")
    List<MonitorVpsMetric> lockAllById(Collection<Long> ids);
    // Earliest moment any enabled assignment becomes claimable (later of next collection and lease expiry).
    @Query("select min(case when a.leaseUntil is null then a.nextCollectionAt when a.nextCollectionAt is null then a.leaseUntil "
            + "when a.leaseUntil > a.nextCollectionAt then a.leaseUntil else a.nextCollectionAt end) "
            + "from MonitorVpsMetric a where a.enabled = true and a.metric.enabled = true")
    LocalDateTime findNextDue();
}

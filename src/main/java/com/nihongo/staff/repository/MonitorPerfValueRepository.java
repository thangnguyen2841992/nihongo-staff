package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorPerfValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MonitorPerfValueRepository
        extends JpaRepository<MonitorPerfValue, Long> {
    java.util.Optional<MonitorPerfValue> findFirstByVpsIdAndMetricIdAndObjectIdOrderByCollectedAtDesc(Long vpsId, Long metricId, Long objectId);
    // Each object uses the existing (vps, metric, object, collected_at) index. Avoid scanning
    // or transferring its entire history, and resolve same-timestamp samples deterministically.
    @org.springframework.data.jpa.repository.Query(value = """
            select latest.* from monitor_object o
            join monitor_perf_value latest on latest.value_id = (
                select v.value_id from monitor_perf_value v
                where v.vps_id = :vpsId and v.metric_id = :metricId and v.object_id = o.object_id
                order by v.collected_at desc, v.value_id desc limit 1
            )
            where o.vps_id = :vpsId and o.object_id in :objectIds
            """, nativeQuery = true)
    java.util.List<MonitorPerfValue> findLatestForObjects(Long vpsId, Long metricId, java.util.Collection<Long> objectIds);
    java.util.List<MonitorPerfValue> findByVpsIdAndMetricIdAndObjectIdAndCollectedAtBetweenOrderByCollectedAtAsc(Long vpsId, Long metricId, Long objectId, java.time.LocalDateTime start, java.time.LocalDateTime end);
    interface Bucket { Double getTimestamp(); Double getValue(); }
    @org.springframework.data.jpa.repository.Query(value = "select floor(timestampdiff(SECOND, '1970-01-01 00:00:00', collected_at) / :step) * :step as timestamp, avg(value) as value from monitor_perf_value where vps_id = :vpsId and metric_id = :metricId and (object_id = :objectId or (:objectId is null and object_id is null)) and collected_at >= :start and collected_at <= :end group by timestamp order by timestamp", nativeQuery = true)
    java.util.List<Bucket> history(Long vpsId, Long metricId, Long objectId, java.time.LocalDateTime start, java.time.LocalDateTime end, int step);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "delete from monitor_perf_value where collected_at < :cutoff limit 10000", nativeQuery = true)
    int deleteExpired(java.time.LocalDateTime cutoff);
}

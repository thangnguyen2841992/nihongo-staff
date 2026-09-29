package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.time.LocalDateTime;

public interface MonitorEventRepository extends JpaRepository<MonitorEvent, Long> {
    @Query("select e from MonitorEvent e where e.vpsId = :vpsId and e.metricId = :metricId and (:beforeId is null or e.eventId < :beforeId) order by e.eventId desc")
    List<MonitorEvent> history(Long vpsId, Long metricId, Long beforeId, Pageable page);
    @Query(value = """
            select e.* from monitor_event e where e.vps_id = :vpsId
            and (:metricId is null or e.metric_id = :metricId)
            and (:fromTime is null or e.collected_at >= :fromTime)
            and (:toTime is null or e.collected_at <= :toTime)
            and (:severity is null or e.severity = :severity or (:severity = 'MINOR' and e.severity = 'INFO'))
            and (:beforeTime is null or e.collected_at < :beforeTime or (e.collected_at = :beforeTime and e.event_id < :beforeId))
            order by e.collected_at desc, e.event_id desc
            """, nativeQuery = true)
    List<MonitorEvent> search(Long vpsId, Long metricId, LocalDateTime fromTime, LocalDateTime toTime,
                              String severity, LocalDateTime beforeTime, Long beforeId, Pageable page);
}

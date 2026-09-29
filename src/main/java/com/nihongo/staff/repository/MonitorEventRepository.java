package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface MonitorEventRepository extends JpaRepository<MonitorEvent, Long> {
    @Query("select e from MonitorEvent e where e.vpsId = :vpsId and e.metricId = :metricId and (:beforeId is null or e.eventId < :beforeId) order by e.eventId desc")
    List<MonitorEvent> history(Long vpsId, Long metricId, Long beforeId, Pageable page);
}

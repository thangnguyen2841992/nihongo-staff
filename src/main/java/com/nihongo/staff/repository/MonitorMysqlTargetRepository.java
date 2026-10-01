package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorMysqlTarget;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitorMysqlTargetRepository extends JpaRepository<MonitorMysqlTarget, Long> {
}

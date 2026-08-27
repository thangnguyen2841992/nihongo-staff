package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorPerfValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MonitorPerfValueRepository
        extends JpaRepository<MonitorPerfValue, Long> {
}

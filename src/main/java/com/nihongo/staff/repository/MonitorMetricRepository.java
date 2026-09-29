package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MonitorMetricRepository
        extends JpaRepository<MonitorMetric, Long> {

    Optional<MonitorMetric> findByMetricCode(String metricCode);

    boolean existsByMetricCode(String metricCode);



}

package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorKafkaTarget;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitorKafkaTargetRepository extends JpaRepository<MonitorKafkaTarget, Long> {
    boolean existsByBootstrapServers(String bootstrapServers);
}

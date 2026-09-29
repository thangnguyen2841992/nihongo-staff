package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorPrometheusTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonitorPrometheusTargetRepository  extends JpaRepository<MonitorPrometheusTarget, Long> {



    Optional<MonitorPrometheusTarget> findByVps_VpsIdAndJobName(
            Long vpsId,
            String jobName
    );

    List<MonitorPrometheusTarget> findByEnabledTrue();

}

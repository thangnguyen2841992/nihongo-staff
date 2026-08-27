package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorVps;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MonitorVpsRepository extends JpaRepository<MonitorVps, Long> {

    Optional<MonitorVps> findByHostname(String hostname);

    Optional<MonitorVps> findByIpAddress(String ipAddress);

    boolean existsByHostname(String hostname);

    boolean existsByIpAddress(String ipAddress);

    boolean existsByIpAddressAndAgentPort(String ipAddress, Integer agentPort);
}

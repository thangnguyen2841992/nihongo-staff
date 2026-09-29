package com.nihongo.staff.repository;

import com.nihongo.staff.model.monitoring.MonitorVps;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MonitorVpsRepository extends JpaRepository<MonitorVps, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from MonitorVps v where v.vpsId = :id")
    Optional<MonitorVps> lockById(Long id);



    boolean existsByHostname(String hostname);


    boolean existsByIpAddressAndAgentPort(String ipAddress, Integer agentPort);
}

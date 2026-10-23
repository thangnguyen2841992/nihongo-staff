package com.nihongo.staff.repository;
import com.nihongo.staff.model.monitoring.MonitorCheckTarget;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MonitorCheckTargetRepository extends JpaRepository<MonitorCheckTarget, Long> {
    boolean existsByTargetKey(String targetKey);
    interface Connection {
        Long getVpsId(); String getName(); com.nihongo.staff.model.monitoring.ExporterType getType();
        String getHost(); Integer getPort(); String getUrl(); int getStatusMin(); int getStatusMax();
        com.nihongo.staff.model.monitoring.VpsStatus getStatus(); java.time.LocalDateTime getLastSeenAt();
    }
    String CONNECTION = "select t.vpsId as vpsId, s.hostname as name, s.exporterType as type, "
            + "s.ipAddress as host, s.agentPort as port, t.url as url, t.statusMin as statusMin, t.statusMax as statusMax, "
            + "s.status as status, s.lastSeenAt as lastSeenAt from MonitorCheckTarget t join MonitorVps s on s.vpsId = t.vpsId";
    @org.springframework.data.jpa.repository.Query(CONNECTION + " where t.vpsId = :id")
    java.util.Optional<Connection> connection(long id);
    @org.springframework.data.jpa.repository.Query(CONNECTION + " where s.exporterType = :type order by t.vpsId desc")
    org.springframework.data.domain.Slice<Connection> page(com.nihongo.staff.model.monitoring.ExporterType type,
                                                          org.springframework.data.domain.Pageable pageable);
}

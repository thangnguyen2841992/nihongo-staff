package com.nihongo.staff.model.monitoring.dto;

import com.nihongo.staff.model.monitoring.VpsStatus;
import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.ExporterType;
import com.nihongo.staff.model.monitoring.MonitorKafkaTarget;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MonitorVpsResponse {

    private Long vpsId;

    private String hostname;

    private String ipAddress;

    private Integer agentPort;

    private ExporterType exporterType;

    private String osType;

    private String osVersion;

    private String architecture;

    private VpsStatus status;

    private LocalDateTime lastSeenAt;

    private String bootstrapServers;

    private String securityProtocol;

    private String saslMechanism;

    public static MonitorVpsResponse from(MonitorVps vps) {
        return MonitorVpsResponse.builder().vpsId(vps.getVpsId()).hostname(vps.getHostname())
                .ipAddress(vps.getIpAddress()).agentPort(vps.getAgentPort())
                .exporterType(ExporterType.forVps(vps)).osType(vps.getOsType())
                .osVersion(vps.getOsVersion()).architecture(vps.getArchitecture())
                .status(vps.getStatus()).lastSeenAt(vps.getLastSeenAt()).build();
    }

    public static MonitorVpsResponse fromKafka(MonitorVps vps, MonitorKafkaTarget target) {
        return MonitorVpsResponse.builder().vpsId(vps.getVpsId()).hostname(vps.getHostname())
                .ipAddress(vps.getIpAddress()).agentPort(vps.getAgentPort())
                .exporterType(ExporterType.KAFKA_ADMIN).osType(vps.getOsType())
                .osVersion(vps.getOsVersion()).architecture(vps.getArchitecture())
                .status(vps.getStatus()).lastSeenAt(vps.getLastSeenAt())
                .bootstrapServers(target.getBootstrapServers()).securityProtocol(target.getSecurityProtocol())
                .saslMechanism(target.getSaslMechanism()).build();
    }
}

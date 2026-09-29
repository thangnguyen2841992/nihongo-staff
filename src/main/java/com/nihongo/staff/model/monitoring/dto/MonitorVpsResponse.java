package com.nihongo.staff.model.monitoring.dto;

import com.nihongo.staff.model.monitoring.VpsStatus;
import com.nihongo.staff.model.monitoring.MonitorVps;
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

    private String osType;

    private String osVersion;

    private String architecture;

    private VpsStatus status;

    private LocalDateTime lastSeenAt;

    public static MonitorVpsResponse from(MonitorVps vps) {
        return MonitorVpsResponse.builder().vpsId(vps.getVpsId()).hostname(vps.getHostname())
                .ipAddress(vps.getIpAddress()).agentPort(vps.getAgentPort()).osType(vps.getOsType())
                .osVersion(vps.getOsVersion()).architecture(vps.getArchitecture())
                .status(vps.getStatus()).lastSeenAt(vps.getLastSeenAt()).build();
    }
}

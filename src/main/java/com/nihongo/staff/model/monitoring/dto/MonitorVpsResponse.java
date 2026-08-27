package com.nihongo.staff.model.monitoring.dto;

import com.nihongo.staff.model.monitoring.VpsStatus;
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
}

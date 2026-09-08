package com.nihongo.staff.model.monitoring.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterMonitorVpsRequest {
    private String ipAddress;
    private Integer agentPort;
    private String hostname;
    private String osType;
    private String osVersion;
    private String architecture;
}
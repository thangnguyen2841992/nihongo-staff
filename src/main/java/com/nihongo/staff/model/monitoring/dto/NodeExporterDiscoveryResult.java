package com.nihongo.staff.model.monitoring.dto;

import com.nihongo.staff.model.monitoring.ExporterType;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class NodeExporterDiscoveryResult {

    private boolean installed;

    private String ipAddress;

    private Integer port;

    private String hostname;

    private String osType;

    private String osVersion;

    private String architecture;

    private ExporterType exporterType;

    private String nodeExporterVersion;

    private String message;

}

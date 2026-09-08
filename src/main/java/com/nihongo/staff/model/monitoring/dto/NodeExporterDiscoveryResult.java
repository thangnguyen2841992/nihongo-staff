package com.nihongo.staff.model.monitoring.dto;

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

    private String nodeExporterVersion;

    private String message;

    public static NodeExporterDiscoveryResult failed(
            String ipAddress,
            String message
    ) {
        return NodeExporterDiscoveryResult.builder()
                .installed(false)
                .ipAddress(ipAddress)
                .port(9100)
                .message(message)
                .build();
    }


    public static NodeExporterDiscoveryResult success(
            String ipAddress,
            String hostname,
            String osType,
            String osVersion,
            String architecture,
            String nodeExporterVersion
    ) {
        return NodeExporterDiscoveryResult.builder()
                .installed(true)
                .ipAddress(ipAddress)
                .port(9100)
                .hostname(hostname)
                .osType(osType)
                .osVersion(osVersion)
                .architecture(architecture)
                .nodeExporterVersion(nodeExporterVersion)
                .message("Node Exporter detected successfully")
                .build();
    }
}
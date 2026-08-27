package com.nihongo.staff.model.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeExporterInfo {

    private String hostname;

    private String osType;

    private String osVersion;

    private String architecture;
}
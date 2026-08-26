package com.nihongo.staff.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class MonitoringResponse {
    private double cpuUsage;
    private double memoryUsage;
    private double diskUsage;
    private long uptime;
}

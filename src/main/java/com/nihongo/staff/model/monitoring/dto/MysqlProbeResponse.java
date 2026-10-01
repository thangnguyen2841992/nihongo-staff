package com.nihongo.staff.model.monitoring.dto;

public record MysqlProbeResponse(String version, Double uptimeSeconds,
                                 Double threadsConnected, Double threadsRunning) {
}

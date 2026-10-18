package com.nihongo.staff.model.monitoring.dto;
public record CheckTargetRequest(String name, String type, String host, Integer port, String url,
                                 Integer statusMin, Integer statusMax) {}

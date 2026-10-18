package com.nihongo.staff.model.monitoring.dto;

public record KafkaProbeResponse(String clusterId, Integer brokerCount, Integer controllerId,
                                 Integer topicCount, Integer partitionCount, Integer consumerGroupCount) {}

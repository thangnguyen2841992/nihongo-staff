package com.nihongo.staff.model.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KafkaTargetRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 1000) String bootstrapServers,
        @NotBlank @Size(max = 20) String securityProtocol,
        @Size(max = 30) String saslMechanism,
        @Size(max = 256) String username,
        @Size(max = 512) String password
) {}

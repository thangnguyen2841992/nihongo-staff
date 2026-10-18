package com.nihongo.staff.model.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KafkaCredentialsUpdateRequest(
        @NotBlank @Size(max = 256) String username,
        @NotBlank @Size(max = 512) String password
) {}

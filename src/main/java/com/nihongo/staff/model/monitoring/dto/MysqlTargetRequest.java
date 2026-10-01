package com.nihongo.staff.model.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MysqlTargetRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 45) String host,
        Integer port,
        @NotBlank @Size(max = 128) String username,
        @NotBlank @Size(max = 256) String password,
        String sslMode
) {
}

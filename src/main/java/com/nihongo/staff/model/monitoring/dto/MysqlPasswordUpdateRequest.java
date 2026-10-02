package com.nihongo.staff.model.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MysqlPasswordUpdateRequest(@NotBlank @Size(max = 256) String password) {
}

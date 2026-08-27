package com.nihongo.staff.model.monitoring.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MonitorVpsRequest {

    @NotBlank
    private String ipAddress;

    @Min(1)
    @Max(65535)
    private Integer agentPort = 9100;
}

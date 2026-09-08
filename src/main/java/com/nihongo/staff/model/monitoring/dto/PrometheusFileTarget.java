package com.nihongo.staff.model.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PrometheusFileTarget {

    private List<String> targets;

    private Map<String, String> labels;
}
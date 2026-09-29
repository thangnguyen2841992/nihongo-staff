package com.nihongo.staff.controller;

import com.nihongo.staff.service.monitor.collection.VpsPerformanceService;
import com.nihongo.staff.service.monitor.metric.VpsMetricConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class VpsPerformanceController {
    private final VpsPerformanceService service;
    private final VpsMetricConfigService configuration;
    @GetMapping("/vps-metrics")
    public List<VpsMetricConfigService.Metric> metrics() { return configuration.metrics(); }
    @GetMapping("/vps/{vpsId}/metric-configs")
    public List<VpsMetricConfigService.Config> configs(@PathVariable long vpsId) { return configuration.configs(vpsId); }
    @PutMapping("/vps/{vpsId}/metrics/{code}/config")
    public VpsMetricConfigService.Config updateVps(@PathVariable long vpsId, @PathVariable String code, @RequestBody VpsMetricConfigService.Update update) {
        return configuration.updateVps(vpsId, code, update);
    }
    @PutMapping("/vps-metrics/{metricId}/config")
    public void updateMetric(@PathVariable long metricId, @RequestBody VpsMetricConfigService.Update update) { configuration.updateMetric(metricId, update); }
    @GetMapping("/vps/{vpsId}/performance")
    public VpsPerformanceService.Performance performance(@PathVariable long vpsId, @RequestParam String metric,
                                                          @RequestParam(required = false) Integer hours,
                                                          @RequestParam(required = false) Integer minutes,
                                                          @RequestParam(required = false) String objectKey) {
        return service.read(vpsId, metric, hours, objectKey, minutes);
    }
}

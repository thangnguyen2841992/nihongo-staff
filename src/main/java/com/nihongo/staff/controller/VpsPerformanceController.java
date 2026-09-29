package com.nihongo.staff.controller;

import com.nihongo.staff.service.monitor.collection.VpsPerformanceService;
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
    @GetMapping("/vps-metrics")
    public List<VpsPerformanceService.Metric> metrics() { return service.metrics(); }
    @GetMapping("/vps/{vpsId}/metric-configs")
    public List<VpsPerformanceService.Config> configs(@PathVariable long vpsId) { return service.configs(vpsId); }
    @PutMapping("/vps/{vpsId}/metrics/{code}/config")
    public VpsPerformanceService.Config updateVps(@PathVariable long vpsId, @PathVariable String code, @RequestBody VpsPerformanceService.Update update) {
        return service.updateVps(vpsId, code, update);
    }
    @PutMapping("/vps-metrics/{metricId}/config")
    public void updateMetric(@PathVariable long metricId, @RequestBody VpsPerformanceService.Update update) { service.updateMetric(metricId, update); }
    @GetMapping("/vps/{vpsId}/performance")
    public VpsPerformanceService.Performance performance(@PathVariable long vpsId, @RequestParam String metric,
                                                          @RequestParam(required = false) Integer hours,
                                                          @RequestParam(required = false) String objectKey) {
        return service.read(vpsId, metric, hours, objectKey);
    }
}

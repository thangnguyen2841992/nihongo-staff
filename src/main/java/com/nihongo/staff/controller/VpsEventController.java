package com.nihongo.staff.controller;

import com.nihongo.staff.model.monitoring.MonitorEventRule;
import com.nihongo.staff.service.monitor.event.MonitorEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/staff/vps/{vpsId}/events")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class VpsEventController {
    private final MonitorEventService service;
    @GetMapping
    public MonitorEventService.Page search(@PathVariable long vpsId,
            @RequestParam(required = false) String metric,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) MonitorEventRule.Severity severity,
            @RequestParam(required = false) Long beforeId) {
        return service.search(vpsId, metric, from, to, severity, beforeId);
    }
}

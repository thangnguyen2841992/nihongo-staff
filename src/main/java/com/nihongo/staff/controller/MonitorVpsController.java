package com.nihongo.staff.controller;

import com.nihongo.staff.model.monitoring.dto.*;
import com.nihongo.staff.service.monitor.vps.IMonitorVpsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff/vps")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class MonitorVpsController {
    private final IMonitorVpsService service;

    @GetMapping
    public List<MonitorVpsResponse> listVps() {
        return service.listVps();
    }

    @PostMapping("/discovery")
    public NodeExporterDiscoveryResult discover(@Valid @RequestBody MonitorVpsRequest request) {
        return service.discover(request);
    }

    @PostMapping("/register")
    public MonitorVpsResponse register(@Valid @RequestBody RegisterMonitorVpsRequest request) {
        return MonitorVpsResponse.from(service.registerVps(request));
    }
}

package com.nihongo.staff.controller;

import com.nihongo.staff.model.monitoring.dto.MonitorVpsResponse;
import com.nihongo.staff.model.monitoring.dto.MysqlProbeResponse;
import com.nihongo.staff.model.monitoring.dto.MysqlTargetRequest;
import com.nihongo.staff.service.monitor.mysql.MysqlTargetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/staff/mysql-targets")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class MysqlTargetController {
    private final MysqlTargetService service;

    @GetMapping
    public List<MonitorVpsResponse> list() { return service.list(); }

    @PostMapping("/probe")
    public MysqlProbeResponse probe(@Valid @RequestBody MysqlTargetRequest request) {
        return service.probe(request);
    }

    @PostMapping
    public MonitorVpsResponse register(@Valid @RequestBody MysqlTargetRequest request) {
        return service.register(request);
    }
}

package com.nihongo.staff.controller;

import com.nihongo.staff.model.monitoring.dto.MonitorVpsResponse;
import com.nihongo.staff.model.monitoring.dto.MysqlProbeResponse;
import com.nihongo.staff.model.monitoring.dto.MysqlPasswordUpdateRequest;
import com.nihongo.staff.model.monitoring.dto.MysqlTargetRequest;
import com.nihongo.staff.service.monitor.mysql.MysqlTargetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

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

    @PutMapping("/{vpsId}/credentials")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replacePassword(@PathVariable long vpsId,
                                @Valid @RequestBody MysqlPasswordUpdateRequest request) {
        service.replacePassword(vpsId, request.password());
    }
}

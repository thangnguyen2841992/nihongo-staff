package com.nihongo.staff.controller;

import com.nihongo.staff.model.monitoring.dto.KafkaCredentialsUpdateRequest;
import com.nihongo.staff.model.monitoring.dto.KafkaProbeResponse;
import com.nihongo.staff.model.monitoring.dto.KafkaTargetRequest;
import com.nihongo.staff.model.monitoring.dto.MonitorVpsResponse;
import com.nihongo.staff.service.monitor.kafka.KafkaTargetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/staff/kafka-targets")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class KafkaTargetController {
    private final KafkaTargetService service;

    @GetMapping public List<MonitorVpsResponse> list() { return service.list(); }
    @PostMapping("/probe") public KafkaProbeResponse probe(@Valid @RequestBody KafkaTargetRequest request) {
        return service.probe(request);
    }
    @PostMapping public MonitorVpsResponse register(@Valid @RequestBody KafkaTargetRequest request) {
        return service.register(request);
    }
    @PutMapping("/{vpsId}/credentials")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceCredentials(@PathVariable long vpsId,
                                   @Valid @RequestBody KafkaCredentialsUpdateRequest request) {
        service.replaceCredentials(vpsId, request);
    }
}

package com.nihongo.staff.controller;
import com.nihongo.staff.model.monitoring.dto.CheckTargetRequest;
import com.nihongo.staff.service.monitor.check.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/staff/check-targets") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class CheckTargetController {
    private final CheckTargetService service;
    @GetMapping("/page") public CheckTargetService.Page page(@RequestParam String type, @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return service.page(type, page, size);
    }
    @PostMapping("/probe") public CheckMetricSource.Probe probe(@RequestBody CheckTargetRequest request) {
        try { return service.probe(request); }
        catch (CheckMetricSource.ProbeUnavailableException busy) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, busy.getMessage());
        }
    }
    @PostMapping public CheckTargetService.Target register(@RequestBody CheckTargetRequest request) {
        try { return service.register(request); }
        catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Tên hoặc địa chỉ kiểm tra này đã được đăng ký.");
        }
    }
}

package com.nihongo.staff.controller;

import com.nihongo.staff.service.monitor.event.MonitorEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/staff/vps/{vpsId}/metrics/{code}")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class MonitorEventController {
    private final MonitorEventService service;
    @GetMapping("/event-rules")
    public List<MonitorEventService.Rule> rules(@PathVariable long vpsId, @PathVariable String code) { return service.rules(vpsId, code); }
    @PostMapping("/event-rules") @ResponseStatus(HttpStatus.CREATED)
    public MonitorEventService.Rule create(@PathVariable long vpsId, @PathVariable String code, @RequestBody MonitorEventService.Input input) { return service.save(vpsId, code, null, input); }
    @PutMapping("/event-rules/{ruleId}")
    public MonitorEventService.Rule update(@PathVariable long vpsId, @PathVariable String code, @PathVariable long ruleId, @RequestBody MonitorEventService.Input input) { return service.save(vpsId, code, ruleId, input); }
    @DeleteMapping("/event-rules/{ruleId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long vpsId, @PathVariable String code, @PathVariable long ruleId) { service.delete(vpsId, code, ruleId); }
    @GetMapping("/events")
    public List<MonitorEventService.Event> events(@PathVariable long vpsId, @PathVariable String code, @RequestParam(required = false) Long beforeId) { return service.events(vpsId, code, beforeId); }
}

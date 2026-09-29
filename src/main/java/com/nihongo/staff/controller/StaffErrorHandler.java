package com.nihongo.staff.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {StaffRestController.class, MonitorVpsController.class, VpsPerformanceController.class, MonitorEventController.class, VpsEventController.class})
public class StaffErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message",
                exception.getReason() == null ? "Không thể thực hiện yêu cầu." : exception.getReason()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException exception) {
        String detail = exception.getMostSpecificCause().getMessage();
        String message = detail != null && detail.contains("uk_monitor_vps_hostname")
                ? "VPS với hostname này đã được đăng ký. Vui lòng kiểm tra danh sách VPS."
                : "Dữ liệu xung đột với bản ghi hiện có. Vui lòng kiểm tra lại thông tin.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", message));
    }
}

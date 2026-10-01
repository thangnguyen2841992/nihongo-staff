package com.nihongo.staff.service.monitor.vps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.monitoring.MonitorPrometheusTarget;
import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.ExporterType;
import com.nihongo.staff.model.monitoring.dto.PrometheusFileTarget;
import com.nihongo.staff.repository.MonitorPrometheusTargetRepository;
import com.nihongo.staff.repository.MonitorVpsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PrometheusTargetService {
    private final MonitorPrometheusTargetRepository targetRepository;
    private final MonitorVpsRepository vpsRepository;
    private final ObjectMapper objectMapper;

    @Value("${monitoring.prometheus-targets-file}")
    private String prometheusTargetsFile;

    @Value("${monitoring.prometheus-windows-targets-file}")
    private String prometheusWindowsTargetsFile;

    public MonitorPrometheusTarget registerTarget(Long vpsId) {
        MonitorVps vps = vpsRepository.findById(vpsId)
                .orElseThrow(() -> new RuntimeException("VPS not found: " + vpsId));
        String jobName = ExporterType.forVps(vps) == ExporterType.WINDOWS_EXPORTER ? "windows_vps" : "node";
        String target = vps.getIpAddress() + ":" + vps.getAgentPort();

        MonitorPrometheusTarget targetEntity = targetRepository.findByVps_VpsIdAndJobName(vpsId, jobName)
                .orElseGet(MonitorPrometheusTarget::new);
        targetEntity.setVps(vps);
        targetEntity.setJobName(jobName);
        targetEntity.setTarget(target);
        targetEntity.setEnabled(true);

        MonitorPrometheusTarget saved = targetRepository.save(targetEntity);
        syncAfterCommit();
        return saved;
    }

    @Transactional(readOnly = true)
    public synchronized void sync() {
        var targets = targetRepository.findByEnabledTrue();
        writeFile(prometheusTargetsFile, targets.stream().filter(t -> !"windows_vps".equals(t.getJobName()))
                .map(this::convert).toList());
        writeFile(prometheusWindowsTargetsFile, targets.stream().filter(t -> "windows_vps".equals(t.getJobName()))
                .map(this::convert).toList());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        try {
            sync();
        } catch (RuntimeException e) {
            log.warn("Could not sync local Prometheus target file: {}", e.getMessage());
        }
    }

    private PrometheusFileTarget convert(MonitorPrometheusTarget target) {
        MonitorVps vps = target.getVps();
        Map<String, String> labels = new HashMap<>();
        labels.put("vps_id", String.valueOf(vps.getVpsId()));
        labels.put("hostname", vps.getHostname() == null ? vps.getIpAddress() : vps.getHostname());
        return new PrometheusFileTarget(List.of(target.getTarget()), labels);
    }

    private void writeFile(String file, List<PrometheusFileTarget> targets) {
        Path destination = Path.of(file).toAbsolutePath().normalize();
        Path directory = destination.getParent();
        if (directory == null) throw new IllegalStateException("Invalid Prometheus target file path");
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "exporter_targets-", ".json.tmp");
            String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(targets);
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write local Prometheus target file: " + destination, e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException e) { log.warn("Could not delete temporary target file {}", temporary, e); }
            }
        }
    }

    private void syncAfterCommit() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            sync();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    sync();
                } catch (RuntimeException e) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Thay đổi đã được lưu, nhưng chưa ghi được file target Prometheus trên máy local. "
                                    + "Vui lòng kiểm tra PROMETHEUS_TARGETS_FILE và PROMETHEUS_WINDOWS_TARGETS_FILE; không đăng ký lại VPS.", e);
                }
            }
        });
    }
}

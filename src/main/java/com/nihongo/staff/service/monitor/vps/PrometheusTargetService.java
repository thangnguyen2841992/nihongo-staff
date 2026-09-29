package com.nihongo.staff.service.monitor.vps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.monitoring.MonitorPrometheusTarget;
import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.dto.PrometheusFileTarget;
import com.nihongo.staff.repository.MonitorPrometheusTargetRepository;
import com.nihongo.staff.repository.MonitorVpsRepository;
import lombok.RequiredArgsConstructor;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.SFTPClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class PrometheusTargetService {
    private final MonitorPrometheusTargetRepository targetRepository;
    private final MonitorVpsRepository vpsRepository;
    private final ObjectMapper objectMapper;

    @Value("${monitoring.prometheus-host}")
    private String prometheusHost;

    @Value("${monitoring.prometheus-port}")
    private int prometheusPort;

    @Value("${monitoring.prometheus-ssh-username}")
    private String prometheusUsername;

    @Value("${monitoring.prometheus-ssh-password}")
    private String prometheusPassword;

    @Value("${monitoring.prometheus-targets-file}")
    private String prometheusTargetsFile;

    @Value("${PROMETHEUS_SSH_FINGERPRINT:}") private String sshFingerprint;
    @Value("${PROMETHEUS_KNOWN_HOSTS:}") private String knownHosts;

    @Transactional
    public MonitorPrometheusTarget registerTarget(Long vpsId) {
        MonitorVps vps = vpsRepository.findById(vpsId).orElseThrow(() -> new RuntimeException("VPS not found: " + vpsId));

        String jobName = "node";
        String target = vps.getIpAddress() + ":" + vps.getAgentPort();

        MonitorPrometheusTarget targetEntity = targetRepository.findByVps_VpsIdAndJobName(vpsId, jobName).orElseGet(MonitorPrometheusTarget::new);

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

        List<MonitorPrometheusTarget> targets = targetRepository.findByEnabledTrue();

        List<PrometheusFileTarget> result = targets.stream().map(this::convert).toList();

        writeFile(result);
    }

    private PrometheusFileTarget convert(MonitorPrometheusTarget target) {

        MonitorVps vps = target.getVps();

        Map<String, String> labels = new HashMap<>();

        labels.put("vps_id", String.valueOf(vps.getVpsId()));

        labels.put("hostname", vps.getHostname());

        return new PrometheusFileTarget(List.of(target.getTarget()), labels);
    }

    private void writeFile(List<PrometheusFileTarget> targets) {

        try {
            String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(targets);

            uploadFileViaSsh(content);

        } catch (Exception e) {
            throw new RuntimeException("Cannot sync Prometheus targets via SSH", e);
        }
    }

    private void uploadFileViaSsh(String content) throws Exception {

        if (prometheusPassword == null || prometheusPassword.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình MONITORING_PROMETHEUS_SSH_PASSWORD để đồng bộ target sang Prometheus.");
        }

        Path localTempFile = Files.createTempFile("node_targets_", ".json");

        try {
            // 1. Ghi JSON ra file tạm trên server Spring Boot
            Files.writeString(localTempFile, content, StandardCharsets.UTF_8);

            // 2. SSH vào Prometheus server
            try (SSHClient ssh = new SSHClient()) {

                if (!sshFingerprint.isBlank()) {
                    ssh.addHostKeyVerifier(sshFingerprint);
                } else if (!knownHosts.isBlank()) {
                    ssh.loadKnownHosts(new java.io.File(knownHosts));
                } else {
                    ssh.loadKnownHosts();
                }
                ssh.setConnectTimeout(5000);
                ssh.setTimeout(10000);

                ssh.connect(prometheusHost, prometheusPort);

                // 3. Login bằng username + password
                ssh.authPassword(prometheusUsername, prometheusPassword);

                // File tạm trên Prometheus server
                String remoteTempFile = prometheusTargetsFile + ".tmp";

                // 4. Upload file
                try (SFTPClient sftp = ssh.newSFTPClient()) {

                    sftp.put(localTempFile.toString(), remoteTempFile);
                }

                // 5. Đổi file .tmp thành file chính
                try (Session session = ssh.startSession()) {

                    String command = "mv -f " + shellEscape(remoteTempFile) + " " + shellEscape(prometheusTargetsFile);

                    Session.Command cmd = session.exec(command);

                    cmd.join();

                    Integer exitStatus = cmd.getExitStatus();

                    if (exitStatus == null || exitStatus != 0) {

                        String error = new String(cmd.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

                        throw new RuntimeException("Cannot replace Prometheus target file: " + error);
                    }
                }
            }

        } finally {

            // 6. Xóa file tạm local
            Files.deleteIfExists(localTempFile);
        }
    }

    private String shellEscape(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void syncAfterCommit() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            sync();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    public void afterCommit() {
                try {
                    sync();
                } catch (RuntimeException e) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            prometheusPassword == null || prometheusPassword.isBlank()
                                    ? "Thay đổi đã được lưu, nhưng chưa đồng bộ Prometheus do thiếu MONITORING_PROMETHEUS_SSH_PASSWORD. Vui lòng bổ sung cấu hình SSH; không đăng ký lại VPS."
                                    : "Thay đổi đã được lưu, nhưng chưa đồng bộ được với Prometheus. Vui lòng kiểm tra kết nối SSH; không đăng ký lại VPS.", e);
                }
            }
        });
    }
}

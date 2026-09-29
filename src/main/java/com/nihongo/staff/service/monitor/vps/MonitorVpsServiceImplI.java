package com.nihongo.staff.service.monitor.vps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.monitoring.MonitorPrometheusTarget;
import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.dto.MonitorVpsRequest;
import com.nihongo.staff.model.monitoring.dto.NodeExporterDiscoveryResult;
import com.nihongo.staff.model.monitoring.dto.PrometheusFileTarget;
import com.nihongo.staff.model.monitoring.dto.RegisterMonitorVpsRequest;
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
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional
public class MonitorVpsServiceImplI implements IMonitorVpsService {

    private final RestClient restClient;

    private final MonitorPrometheusTargetRepository targetRepository;
    private final MonitorVpsRepository vpsRepository;

    private final ObjectMapper objectMapper;
    private final com.nihongo.staff.service.monitor.collection.NodeMetricSource nodeMetricSource;
    private final com.nihongo.staff.service.monitor.collection.MetricCatalog metricCatalog;
    private final com.nihongo.staff.service.monitor.collection.PerfCollectionStore perfStore;

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

    @Override
    @Transactional(readOnly = true)
    public List<com.nihongo.staff.model.monitoring.dto.MonitorVpsResponse> listVps() {
        return vpsRepository.findAll(org.springframework.data.domain.Sort.by("vpsId").descending()).stream()
                .map(vps -> com.nihongo.staff.model.monitoring.dto.MonitorVpsResponse.builder()
                        .vpsId(vps.getVpsId()).hostname(vps.getHostname()).ipAddress(vps.getIpAddress())
                        .agentPort(vps.getAgentPort()).osType(vps.getOsType()).osVersion(vps.getOsVersion())
                        .architecture(vps.getArchitecture()).status(vps.getStatus()).lastSeenAt(vps.getLastSeenAt())
                        .build()).toList();
    }

    @Override
    public NodeExporterDiscoveryResult discover(MonitorVpsRequest request) {

        String ipAddress = request.getIpAddress();
        Integer port = request.getAgentPort() == null ? 9100 : request.getAgentPort();
        if (port < 1 || port > 65535) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cổng phải nằm trong khoảng 1 đến 65535.");
        }

        String url = "http://" + ipAddress + ":" + port + "/metrics";

        try {

            String body = restClient.get().uri(url).retrieve().body(String.class);

            if (body == null || body.isBlank()) {
                return NodeExporterDiscoveryResult.failed(ipAddress, "Empty response from Node Exporter");
            }


            if (!body.contains("node_exporter_build_info")) {
                return NodeExporterDiscoveryResult.failed(ipAddress, "Port " + port + " is open but Node Exporter was not detected");
            }


            String nodeExporterVersion = extractLabel(body, "node_exporter_build_info", "version");


            String hostname = extractLabel(body, "node_uname_info", "nodename");

            String osType = extractLabel(body, "node_uname_info", "sysname");

            String osVersion = extractLabel(body, "node_uname_info", "release");

            String architecture = extractLabel(body, "node_uname_info", "machine");


            return NodeExporterDiscoveryResult.builder().installed(true).ipAddress(ipAddress).port(port).hostname(hostname).osType(osType).osVersion(osVersion).architecture(architecture).nodeExporterVersion(nodeExporterVersion).message("Node Exporter detected successfully").build();

        } catch (Exception e) {

            return NodeExporterDiscoveryResult.builder().installed(false).ipAddress(ipAddress).port(port).message("Cannot connect to Node Exporter: " + e.getMessage()).build();
        }
    }

    @Transactional
    public MonitorVps registerVps(RegisterMonitorVpsRequest request) {
        int port = request.getAgentPort() == null ? 9100 : request.getAgentPort();
        if (port < 1 || port > 65535 || request.getIpAddress() == null || request.getIpAddress().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập địa chỉ VPS và cổng hợp lệ.");
        }
        request.setIpAddress(request.getIpAddress().trim()); request.setAgentPort(port);
        if (request.getHostname() != null) request.setHostname(request.getHostname().trim());
        if (request.getHostname() != null && vpsRepository.existsByHostname(request.getHostname())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "VPS với hostname này đã được đăng ký. Vui lòng kiểm tra danh sách VPS.");
        }
        if (vpsRepository.existsByIpAddressAndAgentPort(request.getIpAddress(), request.getAgentPort())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Địa chỉ IP và cổng này đã được đăng ký. Vui lòng kiểm tra danh sách VPS.");
        }
        List<com.nihongo.staff.service.monitor.collection.NodeMetricSource.Sample> samples;
        try { samples = nodeMetricSource.fetch(request.getIpAddress(), port, 5000); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không thể khám phá object của VPS. Vui lòng kiểm tra Node Exporter rồi thử lại.", e); }
        MonitorVps vps = new MonitorVps();

        vps.setIpAddress(request.getIpAddress());
        vps.setAgentPort(request.getAgentPort());
        vps.setHostname(request.getHostname());
        vps.setOsType(request.getOsType());
        vps.setOsVersion(request.getOsVersion());
        vps.setArchitecture(request.getArchitecture());
        vps.setLastSeenAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        vps.setStatus(com.nihongo.staff.model.monitoring.VpsStatus.UP);

        // 1. Lưu VPS, lúc này có vpsId
        MonitorVps savedVps = vpsRepository.save(vps);
        metricCatalog.bindDefaults(savedVps);
        perfStore.discover(savedVps, samples);

        // 2. Tự tạo Prometheus target
        registerTarget(savedVps.getVpsId());

        // 3. registerTarget đã gọi syncAfterCommit()
        // → sau commit, JSON được upload sang Prometheus

        return savedVps;
    }

    @Override
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

    @Override
    public void enableTarget(Long targetId) {
        MonitorPrometheusTarget target = targetRepository.findById(targetId).orElseThrow(() -> new RuntimeException("Prometheus target not found: " + targetId));

        target.setEnabled(true);

        targetRepository.save(target);

        syncAfterCommit();
    }

    @Override
    public void disableTarget(Long targetId) {
        MonitorPrometheusTarget target = targetRepository.findById(targetId).orElseThrow(() -> new RuntimeException("Prometheus target not found: " + targetId));

        target.setEnabled(false);

        targetRepository.save(target);

        syncAfterCommit();
    }

    @Override
    @Transactional
    public void deleteTarget(Long targetId) {

        MonitorPrometheusTarget target = targetRepository.findById(targetId).orElseThrow(() -> new RuntimeException("Prometheus target not found: " + targetId));

        targetRepository.delete(target);

        syncAfterCommit();
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

    private String extractLabel(String body, String metricName, String labelName) {

        Pattern pattern = Pattern.compile("^" + metricName + "\\{[^}]*\\b" + labelName + "=\"([^\"]*)\"[^}]*\\}.*$", Pattern.MULTILINE);

        Matcher matcher = pattern.matcher(body);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private void uploadFileViaSsh(String content) throws Exception {

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
            @Override
            public void afterCommit() {
                try {
                    sync();
                } catch (RuntimeException e) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Thay đổi đã được lưu, nhưng chưa đồng bộ được với Prometheus. Vui lòng kiểm tra kết nối SSH; không đăng ký lại VPS.", e);
                }
            }
        });
    }
}

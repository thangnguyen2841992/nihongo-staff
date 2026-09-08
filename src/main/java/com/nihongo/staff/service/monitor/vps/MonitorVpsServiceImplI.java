package com.nihongo.staff.service.monitor.vps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.monitoring.MonitorPrometheusTarget;
import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.dto.MonitorVpsRequest;
import com.nihongo.staff.model.monitoring.dto.NodeExporterDiscoveryResult;
import com.nihongo.staff.model.monitoring.dto.PrometheusFileTarget;
import com.nihongo.staff.repository.MonitorPrometheusTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional
public class MonitorVpsServiceImplI implements IMonitorVpsService {

    private final RestClient restClient;

    private final MonitorPrometheusTargetRepository targetRepository;

    private final ObjectMapper objectMapper;

    @Value("${monitoring.prometheus-targets-file}")
    private String targetsFile;

    @Override
    public NodeExporterDiscoveryResult discover(MonitorVpsRequest request) {

        String ipAddress = request.getIpAddress();
        Integer port = 9100;

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

    @Override
    @Transactional
    public MonitorPrometheusTarget registerTarget(MonitorVps vps) {

        String jobName = "node";

        String target = vps.getIpAddress() + ":" + vps.getAgentPort();


        // ==========================================
        // Check target của VPS đã tồn tại chưa
        // ==========================================

        Optional<MonitorPrometheusTarget> optional = targetRepository.findByVps_VpsIdAndJobName(vps.getVpsId(), jobName);


        MonitorPrometheusTarget targetEntity;

        if (optional.isPresent()) {

            // Target đã tồn tại
            targetEntity = optional.get();

            targetEntity.setTarget(target);
            targetEntity.setEnabled(true);

        } else {

            // Tạo target mới
            targetEntity = new MonitorPrometheusTarget();

            targetEntity.setVps(vps);
            targetEntity.setJobName(jobName);
            targetEntity.setTarget(target);
            targetEntity.setEnabled(true);
        }


        // ==========================================
        // Save DB
        // ==========================================

        MonitorPrometheusTarget saved = targetRepository.save(targetEntity);


        // ==========================================
        // Sync Prometheus file
        // ==========================================

        sync();


        return saved;
    }

    @Override
    public void enableTarget(Long targetId) {
        MonitorPrometheusTarget target = targetRepository.findById(targetId).orElseThrow(() -> new RuntimeException("Prometheus target not found: " + targetId));

        target.setEnabled(true);

        targetRepository.save(target);

        sync();
    }

    @Override
    public void disableTarget(Long targetId) {
        MonitorPrometheusTarget target = targetRepository.findById(targetId).orElseThrow(() -> new RuntimeException("Prometheus target not found: " + targetId));

        target.setEnabled(false);

        targetRepository.save(target);

        sync();
    }

    @Override
    @Transactional
    public void deleteTarget(Long targetId) {

        MonitorPrometheusTarget target = targetRepository.findById(targetId).orElseThrow(() -> new RuntimeException("Prometheus target not found: " + targetId));

        targetRepository.delete(target);

        sync();
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

            Path path = Paths.get(targetsFile);

            Path parent = path.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }


            // File tạm
            Path tempFile = Paths.get(targetsFile + ".tmp");


            // Ghi file tạm
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), targets);


            // Rename atomically
            try {

                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            } catch (AtomicMoveNotSupportedException e) {

                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {

            throw new RuntimeException("Cannot sync Prometheus targets", e);
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
}

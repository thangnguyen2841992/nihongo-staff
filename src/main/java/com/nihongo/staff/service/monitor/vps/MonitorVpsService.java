package com.nihongo.staff.service.monitor.vps;

import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.VpsStatus;
import com.nihongo.staff.model.monitoring.ExporterType;
import com.nihongo.staff.model.monitoring.dto.*;
import com.nihongo.staff.repository.MonitorVpsRepository;
import com.nihongo.staff.service.monitor.collection.MetricCatalog;
import com.nihongo.staff.service.monitor.collection.NodeMetricSource;
import com.nihongo.staff.service.monitor.collection.PerfCollectionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class MonitorVpsService implements IMonitorVpsService {
    private final MonitorVpsRepository servers;
    private final NodeMetricSource source;
    private final MetricCatalog catalog;
    private final PerfCollectionStore store;
    private final PrometheusTargetService targets;

    @Override
    @Transactional(readOnly = true)
    public List<MonitorVpsResponse> listVps() {
        return servers.findAll(Sort.by("vpsId").descending()).stream().map(MonitorVpsResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NodeExporterDiscoveryResult discover(MonitorVpsRequest request) {
        String host = address(request.getIpAddress());
        int port = port(request.getAgentPort());
        try {
            var samples = source.fetch(host, port, 5000);
            var details = details(samples, null, null, null, null);
            return NodeExporterDiscoveryResult.builder().installed(true).ipAddress(host).port(port)
                    .hostname(details.hostname()).osType(details.osType())
                    .osVersion(details.osVersion()).architecture(details.architecture())
                    .exporterType(details.type()).nodeExporterVersion(details.version())
                    .message("Đã tìm thấy " + exporterName(details.type()) + ".").build();
        } catch (IllegalStateException error) {
            return NodeExporterDiscoveryResult.builder().installed(false).ipAddress(host).port(port)
                    .message(error.getMessage()).build();
        }
    }

    @Override
    public MonitorVps registerVps(RegisterMonitorVpsRequest request) {
        String host = address(request.getIpAddress());
        int port = port(request.getAgentPort());
        if (servers.existsByIpAddressAndAgentPort(host, port)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Địa chỉ IP và cổng này đã được đăng ký. Vui lòng kiểm tra danh sách máy chủ.");
        }
        List<NodeMetricSource.Sample> samples;
        try {
            samples = source.fetch(host, port, 5000);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể khám phá object của máy chủ. Vui lòng kiểm tra exporter rồi thử lại.", error);
        }
        // Re-read exporter metadata on registration instead of trusting an old UI discovery.
        var details = details(samples, request.getHostname(), request.getOsType(),
                request.getOsVersion(), request.getArchitecture());
        String hostname = details.hostname();
        if (hostname != null && servers.existsByHostname(hostname)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Máy chủ với hostname này đã được đăng ký. Vui lòng kiểm tra danh sách máy chủ.");
        }
        var vps = new MonitorVps();
        vps.setIpAddress(host);
        vps.setAgentPort(port);
        vps.setHostname(hostname);
        vps.setExporterType(details.type());
        vps.setOsType(details.osType());
        vps.setOsVersion(details.osVersion());
        vps.setArchitecture(details.architecture());
        vps.setStatus(VpsStatus.UP);
        vps.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));
        servers.save(vps);
        catalog.bindDefaults(vps);
        store.discover(vps, samples);
        targets.registerTarget(vps.getVpsId());
        return vps;
    }

    private record Details(ExporterType type, String hostname, String osType,
                           String osVersion, String architecture, String version) {}

    private static Details details(List<NodeMetricSource.Sample> samples, String hostname,
                                   String osType, String osVersion, String architecture) {
        ExporterType type = NodeMetricSource.exporterType(samples);
        if (type == ExporterType.WINDOWS_EXPORTER) {
            Map<String, String> host = labels(samples, "windows_os_hostname");
            Map<String, String> os = labels(samples, "windows_os_info");
            Map<String, String> build = labels(samples, "windows_exporter_build_info");
            return new Details(type, metadata(host, "hostname", hostname), "Windows",
                    metadata(os, "product", metadata(os, "version", osVersion)),
                    metadata(build, "goarch", architecture), build.get("version"));
        }
        Map<String, String> uname = labels(samples, "node_uname_info");
        return new Details(type, metadata(uname, "nodename", hostname),
                metadata(uname, "sysname", osType), metadata(uname, "release", osVersion),
                metadata(uname, "machine", architecture),
                labels(samples, "node_exporter_build_info").get("version"));
    }

    private static String exporterName(ExporterType type) {
        return type == ExporterType.WINDOWS_EXPORTER ? "Windows Exporter" : "Node Exporter";
    }

    private static Map<String, String> labels(List<NodeMetricSource.Sample> samples, String metric) {
        return samples.stream().filter(sample -> sample.metric().equals(metric))
                .findFirst().map(NodeMetricSource.Sample::labels).orElse(Map.of());
    }

    private static String metadata(Map<String, String> labels, String name, String fallback) {
        String value = labels.getOrDefault(name, fallback);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String address(String host) {
        if (host == null || host.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập địa chỉ máy chủ.");
        }
        return host.trim();
    }

    private static int port(Integer requested) {
        int port = requested == null ? 9100 : requested;
        if (port < 1 || port > 65535) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cổng phải nằm trong khoảng 1 đến 65535.");
        }
        return port;
    }
}

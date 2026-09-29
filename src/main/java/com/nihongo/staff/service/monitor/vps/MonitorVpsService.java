package com.nihongo.staff.service.monitor.vps;

import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.VpsStatus;
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
            var uname = labels(samples, "node_uname_info");
            return NodeExporterDiscoveryResult.builder().installed(true).ipAddress(host).port(port)
                    .hostname(uname.get("nodename")).osType(uname.get("sysname"))
                    .osVersion(uname.get("release")).architecture(uname.get("machine"))
                    .nodeExporterVersion(labels(samples, "node_exporter_build_info").get("version"))
                    .message("Đã tìm thấy Node Exporter.").build();
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
                    "Địa chỉ IP và cổng này đã được đăng ký. Vui lòng kiểm tra danh sách VPS.");
        }
        List<NodeMetricSource.Sample> samples;
        try {
            samples = source.fetch(host, port, 5000);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể khám phá object của VPS. Vui lòng kiểm tra Node Exporter rồi thử lại.", error);
        }
        // Re-read exporter metadata on registration instead of trusting an old UI discovery.
        var uname = labels(samples, "node_uname_info");
        String hostname = metadata(uname, "nodename", request.getHostname());
        if (hostname != null && servers.existsByHostname(hostname)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "VPS với hostname này đã được đăng ký. Vui lòng kiểm tra danh sách VPS.");
        }
        var vps = new MonitorVps();
        vps.setIpAddress(host);
        vps.setAgentPort(port);
        vps.setHostname(hostname);
        vps.setOsType(metadata(uname, "sysname", request.getOsType()));
        vps.setOsVersion(metadata(uname, "release", request.getOsVersion()));
        vps.setArchitecture(metadata(uname, "machine", request.getArchitecture()));
        vps.setStatus(VpsStatus.UP);
        vps.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));
        servers.save(vps);
        catalog.bindDefaults(vps);
        store.discover(vps, samples);
        targets.registerTarget(vps.getVpsId());
        return vps;
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập địa chỉ VPS.");
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

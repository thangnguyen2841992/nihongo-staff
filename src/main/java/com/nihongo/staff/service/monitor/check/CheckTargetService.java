package com.nihongo.staff.service.monitor.check;

import com.nihongo.staff.model.monitoring.*;
import com.nihongo.staff.model.monitoring.dto.CheckTargetRequest;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.monitor.collection.MetricCatalog;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class CheckTargetService {
    private final MonitorVpsRepository servers;
    private final MonitorCheckTargetRepository targets;
    private final CheckMetricSource source;
    private final MetricCatalog catalog;
    public record Configuration(String name, ExporterType type, String host, int port, String url, int statusMin, int statusMax) {}
    public record Target(long vpsId, String name, String type, String host, int port, String url,
                         int statusMin, int statusMax, String status, LocalDateTime lastSeenAt) {}
    public record Page(List<Target> content, int number, boolean hasNext) {}

    @Transactional(readOnly = true)
    public Page page(String type, int page, int size) {
        ExporterType kind;
        try { kind = ExporterType.valueOf(trim(type).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw bad("Type phải là PORT_CHECK hoặc WEB_CHECK."); }
        if ((kind != ExporterType.PORT_CHECK && kind != ExporterType.WEB_CHECK) || page < 0 || size < 1 || size > 100)
            throw bad("Type hoặc trang không hợp lệ; kích thước trang từ 1 đến 100.");
        var result = targets.page(kind, org.springframework.data.domain.PageRequest.of(page, size));
        return new Page(result.getContent().stream().map(CheckTargetService::response).toList(), page, result.hasNext());
    }
    private static Target response(MonitorCheckTargetRepository.Connection row) {
        return new Target(row.getVpsId(), row.getName(), row.getType().name(), row.getHost(), row.getPort(), row.getUrl(),
                row.getStatusMin(), row.getStatusMax(), row.getStatus().name(), row.getLastSeenAt());
    }
    public CheckMetricSource.Probe probe(CheckTargetRequest request) { return source.probe(validate(request), 5000); }

    @Transactional
    public Target register(CheckTargetRequest request) {
        var config = validate(request);
        String key = key(config);
        if (servers.existsByHostname(config.name()) || targets.existsByTargetKey(key))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên hoặc địa chỉ kiểm tra này đã được đăng ký.");
        var server = new MonitorVps(); server.setHostname(config.name()); server.setIpAddress(config.host());
        server.setAgentPort(config.port()); server.setExporterType(config.type());
        server.setOsType(config.type() == ExporterType.PORT_CHECK ? "Port Check" : "Web Check"); server.setStatus(VpsStatus.UNKNOWN);
        servers.saveAndFlush(server);
        var target = new MonitorCheckTarget(); target.setVpsId(server.getVpsId()); target.setTargetKey(key);
        target.setUrl(config.url()); target.setStatusMin(config.statusMin()); target.setStatusMax(config.statusMax());
        targets.saveAndFlush(target); catalog.bindDefaults(server);
        return response(server, target);
    }
    private static Target response(MonitorVps server, MonitorCheckTarget target) {
        return new Target(server.getVpsId(), server.getHostname(), ExporterType.forVps(server).name(), server.getIpAddress(),
                server.getAgentPort(), target.getUrl(), target.getStatusMin(), target.getStatusMax(), server.getStatus().name(), server.getLastSeenAt());
    }
    static Configuration validate(CheckTargetRequest input) {
        if (input == null) throw bad("Thiếu thông tin target.");
        String name = trim(input.name());
        if (name.isEmpty() || name.length() > 200) throw bad("Tên target phải từ 1 đến 200 ký tự.");
        ExporterType type;
        try { type = ExporterType.valueOf(trim(input.type()).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw bad("Type phải là PORT_CHECK hoặc WEB_CHECK."); }
        if (type != ExporterType.PORT_CHECK && type != ExporterType.WEB_CHECK) throw bad("Type không hỗ trợ kiểm tra kết nối.");
        String host, url = null; int port;
        if (type == ExporterType.WEB_CHECK) {
            String raw = trim(input.url());
            if (raw.length() > 2048) throw bad("URL tối đa 2048 ký tự.");
            try {
                var uri = URI.create(raw).normalize();
                String scheme = trim(uri.getScheme()).toLowerCase(Locale.ROOT);
                if (!Set.of("http", "https").contains(scheme) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null)
                    throw new IllegalArgumentException();
                host = cleanHost(uri.getHost()); port = uri.getPort() < 0 ? (scheme.equals("https") ? 443 : 80) : uri.getPort();
                if (port < 1 || port > 65535) throw new IllegalArgumentException();
                String authority = host.contains(":") ? "[" + host + "]" : host;
                if (!(scheme.equals("https") && port == 443) && !(scheme.equals("http") && port == 80)) authority += ":" + port;
                url = scheme + "://" + authority + (uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                        + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            } catch (IllegalArgumentException error) { throw bad("Nhập URL HTTP/HTTPS hợp lệ, không kèm tài khoản hoặc fragment."); }
        } else {
            host = cleanHost(trim(input.host()));
            if (input.port() == null || input.port() < 1 || input.port() > 65535) throw bad("Cổng TCP phải từ 1 đến 65535.");
            port = input.port();
        }
        int min = input.statusMin() == null ? 200 : input.statusMin(), max = input.statusMax() == null ? 399 : input.statusMax();
        if (min < 100 || max > 599 || min > max) throw bad("Khoảng HTTP status phải thuộc 100–599, từ nhỏ đến lớn.");
        return new Configuration(name, type, host, port, url, min, max);
    }
    private static String cleanHost(String host) {
        String value = host.toLowerCase(Locale.ROOT).replaceAll("^\\[|\\]$", "");
        if (value.contains(":")) {
            try { if (java.net.InetAddress.getByName(value) instanceof java.net.Inet6Address) return value; }
            catch (java.net.UnknownHostException ignored) { }
            throw bad("Địa chỉ IPv6 không hợp lệ.");
        }
        if (value.isBlank() || value.length() > 253 || !value.matches("[a-z0-9][a-z0-9.:-]*") || value.contains(".."))
            throw bad("Hostname hoặc IP không hợp lệ; không nhập giao thức hoặc đường dẫn vào Host.");
        return value;
    }
    private static String key(Configuration config) {
        try {
            String identity = config.type() + ":" + (config.url() == null ? config.host() + ":" + config.port() : config.url());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}

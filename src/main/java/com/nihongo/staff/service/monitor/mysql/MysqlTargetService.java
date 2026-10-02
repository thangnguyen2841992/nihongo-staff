package com.nihongo.staff.service.monitor.mysql;

import com.nihongo.staff.model.monitoring.ExporterType;
import com.nihongo.staff.model.monitoring.MonitorMysqlTarget;
import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.VpsStatus;
import com.nihongo.staff.model.monitoring.dto.MonitorVpsResponse;
import com.nihongo.staff.model.monitoring.dto.MysqlProbeResponse;
import com.nihongo.staff.model.monitoring.dto.MysqlTargetRequest;
import com.nihongo.staff.repository.MonitorMysqlTargetRepository;
import com.nihongo.staff.repository.MonitorVpsRepository;
import com.nihongo.staff.service.monitor.collection.MetricCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MysqlTargetService {
    private static final Set<String> SSL_MODES = Set.of("VERIFY_IDENTITY", "DISABLED");
    private final MonitorVpsRepository servers;
    private final MonitorMysqlTargetRepository targets;
    private final MysqlMetricSource source;
    private final MysqlCredentialCipher cipher;
    private final MetricCatalog catalog;
    private final PlatformTransactionManager transactionManager;

    public List<MonitorVpsResponse> list() {
        return servers.findAll(Sort.by("vpsId").descending()).stream()
                .filter(server -> ExporterType.forVps(server) == ExporterType.MYSQL_JDBC)
                .map(MonitorVpsResponse::from).toList();
    }

    public MysqlProbeResponse probe(MysqlTargetRequest request) {
        Validated valid = validate(request);
        try {
            var result = source.probe(valid.host(), valid.port(), valid.username(),
                    valid.password(), valid.sslMode(), 5000);
            return new MysqlProbeResponse(result.version(), result.values().get("uptime"),
                    result.values().get("threads_connected"), result.values().get("threads_running"));
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage());
        }
    }

    public MonitorVpsResponse register(MysqlTargetRequest request) {
        Validated valid = validate(request);
        // Confirm credentials before writing the target or its scheduled assignments.
        MysqlProbeResponse probe = probe(request);
        String encrypted;
        try { encrypted = cipher.encrypt(valid.password()); }
        catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage());
        }
        return new TransactionTemplate(transactionManager).execute(status -> {
            if (servers.existsByIpAddressAndAgentPort(valid.host(), valid.port()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Host và cổng này đã được đăng ký.");
            if (servers.existsByHostname(valid.name()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên target này đã được đăng ký.");
            MonitorVps server = new MonitorVps();
            server.setHostname(valid.name()); server.setIpAddress(valid.host()); server.setAgentPort(valid.port());
            server.setExporterType(ExporterType.MYSQL_JDBC); server.setOsType("MySQL");
            server.setOsVersion(probe.version()); server.setStatus(VpsStatus.UP);
            server.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));
            servers.saveAndFlush(server);
            MonitorMysqlTarget target = new MonitorMysqlTarget();
            target.setVpsId(server.getVpsId()); target.setUsername(valid.username());
            target.setPasswordEncrypted(encrypted); target.setSslMode(valid.sslMode());
            targets.save(target);
            catalog.bindDefaults(server);
            return MonitorVpsResponse.from(server);
        });
    }

    public void replacePassword(long vpsId, String password) {
        if (password == null || password.isBlank() || password.length() > 256)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu MySQL không hợp lệ.");
        MonitorVps server = servers.findById(vpsId)
                .filter(vps -> ExporterType.forVps(vps) == ExporterType.MYSQL_JDBC)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy target MySQL."));
        MonitorMysqlTarget target = targets.findById(vpsId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thiếu cấu hình target MySQL."));
        try {
            source.probe(server.getIpAddress(), server.getAgentPort(), target.getUsername(),
                    password, target.getSslMode(), 5000);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage());
        }
        String encrypted;
        try {
            encrypted = cipher.encrypt(password);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage());
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            MonitorMysqlTarget current = targets.findById(vpsId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thiếu cấu hình target MySQL."));
            current.setPasswordEncrypted(encrypted);
            targets.save(current);
        });
        source.invalidate(vpsId);
    }

    private record Validated(String name, String host, int port, String username,
                             String password, String sslMode) {}

    private static Validated validate(MysqlTargetRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu thông tin target MySQL.");
        String name = trim(request.name());
        String host = trim(request.host());
        String username = trim(request.username());
        String password = request.password();
        int port = request.port() == null ? 3306 : request.port();
        String sslMode = request.sslMode() == null || request.sslMode().isBlank()
                ? "VERIFY_IDENTITY" : request.sslMode().trim().toUpperCase(Locale.ROOT);
        if (name.isBlank() || name.length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên target phải từ 1 đến 200 ký tự.");
        if (!host.matches("[A-Za-z0-9][A-Za-z0-9.-]{0,44}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Host MySQL phải là IP hoặc hostname hợp lệ (tối đa 45 ký tự).");
        if (port < 1 || port > 65535)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cổng MySQL phải từ 1 đến 65535.");
        if (username.isBlank() || username.length() > 128 || password == null || password.isBlank() || password.length() > 256)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản giám sát MySQL không hợp lệ.");
        if (!SSL_MODES.contains(sslMode))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chế độ TLS MySQL không hợp lệ.");
        return new Validated(name, host, port, username, password, sslMode);
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
}

package com.nihongo.staff.service.monitor.mysql;

import com.nihongo.staff.model.monitoring.MonitorMysqlTarget;
import com.nihongo.staff.repository.MonitorMysqlTargetRepository;
import com.nihongo.staff.service.monitor.collection.NodeMetricSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.net.ssl.SSLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class MysqlMetricSource {
    private static final String STATUS_QUERY = "SHOW GLOBAL STATUS WHERE Variable_name IN "
            + "('Uptime','Threads_connected','Threads_running','Queries','Slow_queries',"
            + "'Connections','Bytes_received','Bytes_sent')";
    private static final Set<String> SSL_MODES = Set.of("VERIFY_IDENTITY", "DISABLED");
    private static final Duration CACHE_AGE = Duration.ofSeconds(3);

    private final MonitorMysqlTargetRepository targets;
    private final MysqlCredentialCipher cipher;
    private final ConcurrentHashMap<Long, CachedStatus> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    private record CachedStatus(Instant fetchedAt, Map<String, Double> values) {}
    public record Probe(String version, Map<String, Double> values) {}

    public Probe probe(String host, int port, String username, String password, String sslMode, int timeoutMs) {
        return fetch(host, port, username, password, sslMode, timeoutMs, true);
    }

    public void invalidate(long vpsId) {
        cache.remove(vpsId);
    }

    public List<NodeMetricSource.Reading> readings(long vpsId, String host, int port, String code, int timeoutMs) {
        CachedStatus current = cache.get(vpsId);
        if (fresh(current)) return map(code, current.values());
        synchronized (locks.computeIfAbsent(vpsId, ignored -> new Object())) {
            current = cache.get(vpsId);
            if (!fresh(current)) {
                MonitorMysqlTarget target = targets.findById(vpsId)
                        .orElseThrow(() -> new IllegalStateException("Thiếu cấu hình kết nối target MySQL."));
                Map<String, Double> values = fetch(host, port, target.getUsername(),
                        cipher.decrypt(target.getPasswordEncrypted()), target.getSslMode(), timeoutMs, false).values();
                current = new CachedStatus(Instant.now(), values);
                cache.put(vpsId, current);
            }
        }
        return map(code, current.values());
    }

    private boolean fresh(CachedStatus entry) {
        return entry != null && Duration.between(entry.fetchedAt(), Instant.now()).compareTo(CACHE_AGE) < 0;
    }

    private Probe fetch(String host, int port, String username, String password, String sslMode,
                        int timeoutMs, boolean includeVersion) {
        if (!SSL_MODES.contains(sslMode)) throw new IllegalArgumentException("Chế độ TLS MySQL không hợp lệ.");
        int timeout = Math.max(1000, Math.min(30000, timeoutMs));
        String url = "jdbc:mysql://" + host + ":" + port + "/?sslMode=" + sslMode
                + "&connectTimeout=" + timeout + "&socketTimeout=" + timeout
                + ("DISABLED".equals(sslMode) ? "&allowPublicKeyRetrieval=true" : "");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setReadOnly(true);
            String version = null;
            if (includeVersion) {
                try (Statement statement = connection.createStatement()) {
                    statement.setQueryTimeout(Math.max(1, timeout / 1000));
                    try (ResultSet rows = statement.executeQuery("SELECT VERSION()")) {
                        if (rows.next()) version = rows.getString(1);
                    }
                }
            }
            Map<String, Double> values = new TreeMap<>();
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(Math.max(1, timeout / 1000));
                try (ResultSet rows = statement.executeQuery(STATUS_QUERY)) {
                    while (rows.next()) {
                        try {
                            double value = Double.parseDouble(rows.getString(2));
                            if (Double.isFinite(value)) values.put(rows.getString(1).toLowerCase(Locale.ROOT), value);
                        } catch (NumberFormatException ignored) { /* Only numeric status values are collected. */ }
                    }
                }
            }
            if (!values.containsKey("uptime")) throw new IllegalStateException("MySQL không trả về trạng thái Uptime.");
            return new Probe(version, Map.copyOf(values));
        } catch (SQLException error) {
            throw new IllegalStateException(failureMessage(error), error);
        }
    }

    static String failureMessage(SQLException error) {
        String state = error.getSQLState();
        if (state != null && state.startsWith("28"))
            return "MySQL từ chối tài khoản giám sát; kiểm tra tên đăng nhập, mật khẩu và quyền truy cập từ máy chạy staff-service.";
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException)
                return "Xác thực TLS MySQL thất bại; kiểm tra chứng chỉ và hostname. Nếu JDBC cũ dùng useSSL=false, chọn Không dùng TLS để kiểm tra.";
        }
        if (state != null && state.startsWith("08"))
            return "Không kết nối được MySQL từ staff-service; kiểm tra host, cổng, firewall và quyền truy cập IP.";
        return "Không đọc được trạng thái MySQL (SQLSTATE "
                + (state == null ? "unknown" : state) + ").";
    }

    static List<NodeMetricSource.Reading> map(String code, Map<String, Double> status) {
        String name = switch (code) {
            case "MYSQL_UPTIME" -> "uptime";
            case "MYSQL_THREADS_CONNECTED" -> "threads_connected";
            case "MYSQL_THREADS_RUNNING" -> "threads_running";
            case "MYSQL_QUERIES_RATE" -> "queries";
            case "MYSQL_SLOW_QUERIES_RATE" -> "slow_queries";
            case "MYSQL_CONNECTIONS_RATE" -> "connections";
            case "MYSQL_BYTES_RECEIVED_RATE" -> "bytes_received";
            case "MYSQL_BYTES_SENT_RATE" -> "bytes_sent";
            default -> throw new IllegalArgumentException("Metric MySQL không được hỗ trợ: " + code);
        };
        Double value = status.get(name);
        if (value == null || !Double.isFinite(value)) return List.of();
        boolean counter = code.endsWith("_RATE");
        return List.of(new NodeMetricSource.Reading("VPS", "vps", Map.of(),
                counter ? null : value, counter ? value : null, null));
    }
}

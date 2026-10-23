package com.nihongo.staff.service.monitor.mysql;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.nihongo.staff.model.monitoring.MonitorMysqlTarget;
import com.nihongo.staff.repository.MonitorMysqlTargetRepository;
import com.nihongo.staff.service.monitor.collection.NodeMetricSource;
import com.nihongo.staff.service.monitor.collection.MetricSnapshotCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.net.ssl.SSLException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class MysqlMetricSource {
    private static final String STATUS_QUERY = "SHOW GLOBAL STATUS WHERE Variable_name IN "
            + "('Uptime','Threads_connected','Threads_running','Queries','Slow_queries',"
            + "'Connections','Bytes_received','Bytes_sent')";
    private static final Set<String> SSL_MODES = Set.of("VERIFY_IDENTITY", "DISABLED");
    // Connector/J otherwise re-reads server variables and session state on each new connection and sends
    // SET SESSION TRANSACTION READ ONLY; the statement run here is a fixed SHOW, so the local read-only flag suffices.
    private static final String CONNECTION_OPTIONS = "&cacheServerConfiguration=true&useLocalSessionState=true"
            + "&readOnlyPropagatesToServer=false&connectionAttributes=none";
    // A monitoring connection is reopened at least this often (also picks up DNS/certificate changes).
    private static final long MAX_SESSION_AGE_MS = Duration.ofMinutes(10).toMillis();

    private final MonitorMysqlTargetRepository targets;
    private final MysqlCredentialCipher cipher;
    private final MetricSnapshotCache<Long, Map<String, Double>> cache = new MetricSnapshotCache<>();
    public record Probe(String version, Map<String, Double> values) {}
    private record Account(String username, String password, String sslMode) {}
    private record Endpoint(String host, int port, int timeout, Account account) {}
    private record Session(Connection connection, Endpoint endpoint, long openedAt) {}
    // Decrypted account per target, so a collection does not read the app database. Changed only through
    // MysqlTargetService.replacePassword, which calls invalidate().
    private final Cache<Long, Account> accounts = Caffeine.newBuilder().maximumSize(1024)
            .expireAfterWrite(Duration.ofMinutes(10)).build();
    // One open connection per target instead of TCP + TLS + authentication every cycle. It is closed after two idle
    // minutes, after MAX_SESSION_AGE_MS, on any error and on invalidate(); closing happens on the calling thread.
    private final Cache<Long, Session> sessions = Caffeine.newBuilder().maximumSize(1024)
            .expireAfterAccess(Duration.ofMinutes(2)).executor(Runnable::run)
            .removalListener((Long id, Session session, RemovalCause cause) -> { if (session != null) closeQuietly(session.connection()); })
            .build();

    public Probe probe(String host, int port, String username, String password, String sslMode, int timeoutMs) {
        if (!SSL_MODES.contains(sslMode)) throw new IllegalArgumentException("Chế độ TLS MySQL không hợp lệ.");
        int timeout = clamp(timeoutMs);
        try (Connection connection = open(url(host, port, sslMode, timeout), username, password)) {
            String version = null;
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(Math.max(1, timeout / 1000));
                try (ResultSet rows = statement.executeQuery("SELECT VERSION()")) {
                    if (rows.next()) version = rows.getString(1);
                }
            }
            return new Probe(version, readStatus(connection, timeout));
        } catch (SQLException error) {
            throw new IllegalStateException(failureMessage(error), error);
        }
    }

    /** Forgets the cached snapshot, account and open connection, e.g. after the password changed. */
    public void invalidate(long vpsId) {
        cache.invalidate(vpsId);
        accounts.invalidate(vpsId);
        sessions.invalidate(vpsId);
    }

    // Test hook: lets a test read again without waiting for the 3-second snapshot to expire.
    void forgetSnapshot(long vpsId) { cache.invalidate(vpsId); }

    @jakarta.annotation.PreDestroy
    public void close() {
        sessions.invalidateAll();
        sessions.cleanUp();
    }

    public List<NodeMetricSource.Reading> readings(long vpsId, String host, int port, String code, int timeoutMs) {
        var status = cache.get(vpsId, id -> status(id, new Endpoint(host, port, clamp(timeoutMs), accounts.get(id, this::account))));
        return map(code, status);
    }

    private Account account(long vpsId) {
        MonitorMysqlTarget target = targets.findById(vpsId)
                .orElseThrow(() -> new IllegalStateException("Thiếu cấu hình kết nối target MySQL."));
        if (!SSL_MODES.contains(target.getSslMode())) throw new IllegalArgumentException("Chế độ TLS MySQL không hợp lệ.");
        return new Account(target.getUsername(), cipher.decrypt(target.getPasswordEncrypted()), target.getSslMode());
    }

    private Map<String, Double> status(long vpsId, Endpoint endpoint) {
        Session session = sessions.getIfPresent(vpsId);
        if (session != null && (!session.endpoint().equals(endpoint)
                || System.currentTimeMillis() - session.openedAt() > MAX_SESSION_AGE_MS)) {
            sessions.invalidate(vpsId);
            session = null;
        }
        if (session != null) {
            try {
                return readStatus(session.connection(), endpoint.timeout());
            } catch (SQLException | RuntimeException stale) {
                // Dropped by the server (wait_timeout), a firewall or a restart: reconnect once below.
                sessions.invalidate(vpsId);
            }
        }
        Account account = endpoint.account();
        Connection connection;
        try {
            connection = open(url(endpoint.host(), endpoint.port(), account.sslMode(), endpoint.timeout()),
                    account.username(), account.password());
        } catch (SQLException error) {
            throw new IllegalStateException(failureMessage(error), error);
        }
        try {
            var values = readStatus(connection, endpoint.timeout());
            sessions.put(vpsId, new Session(connection, endpoint, System.currentTimeMillis()));
            return values;
        } catch (SQLException error) {
            closeQuietly(connection);
            throw new IllegalStateException(failureMessage(error), error);
        } catch (RuntimeException error) {
            closeQuietly(connection);
            throw error;
        }
    }

    protected Connection open(String url, String username, String password) throws SQLException {
        Connection connection = DriverManager.getConnection(url, username, password);
        connection.setReadOnly(true);
        return connection;
    }

    static String url(String host, int port, String sslMode, int timeout) {
        return "jdbc:mysql://" + host + ":" + port + "/?sslMode=" + sslMode
                + "&connectTimeout=" + timeout + "&socketTimeout=" + timeout
                + ("DISABLED".equals(sslMode) ? "&allowPublicKeyRetrieval=true" : "")
                + CONNECTION_OPTIONS;
    }

    private static int clamp(int timeoutMs) { return Math.max(1000, Math.min(30000, timeoutMs)); }

    private static Map<String, Double> readStatus(Connection connection, int timeout) throws SQLException {
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
        return Map.copyOf(values);
    }

    private static void closeQuietly(Connection connection) {
        try { connection.close(); } catch (SQLException | RuntimeException ignored) { /* Already broken. */ }
    }

    static String failureMessage(SQLException error) {
        if (error.getMessage() != null && error.getMessage().contains("No suitable driver"))
            return "Thiếu MySQL Connector/J trong staff-service; hãy bổ sung mysql-connector-j và khởi động lại service.";
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

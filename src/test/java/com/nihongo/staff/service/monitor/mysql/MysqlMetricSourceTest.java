package com.nihongo.staff.service.monitor.mysql;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MysqlMetricSourceTest {
    @Test void gaugeAndCounterMapToSeparateReadingKinds() {
        var status = Map.of("threads_connected", 12D, "queries", 410D);
        var gauge = MysqlMetricSource.map("MYSQL_THREADS_CONNECTED", status).get(0);
        assertEquals(12D, gauge.value());
        assertNull(gauge.counter());
        var rate = MysqlMetricSource.map("MYSQL_QUERIES_RATE", status).get(0);
        assertNull(rate.value());
        assertEquals(410D, rate.counter());
    }

    @Test void missingStatusProducesNoReading() {
        assertTrue(MysqlMetricSource.map("MYSQL_UPTIME", Map.of()).isEmpty());
    }

    @Test void distinguishesTlsFailureFromAuthenticationFailure() {
        var tls = new SQLException("Connection failed", "08001", new SSLHandshakeException("certificate failed"));
        assertTrue(MysqlMetricSource.failureMessage(tls).contains("TLS"));
        var auth = new SQLException("Access denied", "28000");
        assertTrue(MysqlMetricSource.failureMessage(auth).contains("tài khoản"));
    }

    @Test void explainsMissingMysqlDriver() {
        var error = new SQLException("No suitable driver found for jdbc:mysql://db:3306", "08001");
        assertTrue(MysqlMetricSource.failureMessage(error).contains("MySQL Connector/J"));
    }

    static java.sql.Connection server(java.util.concurrent.atomic.AtomicBoolean broken) throws SQLException {
        var connection = mock(java.sql.Connection.class);
        var statement = mock(java.sql.Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenAnswer(call -> {
            if (broken.get()) throw new SQLException("Communications link failure", "08S01");
            var rows = mock(java.sql.ResultSet.class);
            when(rows.next()).thenReturn(true, true, false);
            when(rows.getString(1)).thenReturn("Uptime", "Threads_connected");
            when(rows.getString(2)).thenReturn("100", "7");
            return rows;
        });
        return connection;
    }

    @Test void reusesOneConnectionPerTargetAndReconnectsWhenItDrops() throws Exception {
        var targets = mock(com.nihongo.staff.repository.MonitorMysqlTargetRepository.class);
        var cipher = mock(MysqlCredentialCipher.class);
        var target = new com.nihongo.staff.model.monitoring.MonitorMysqlTarget();
        target.setVpsId(1L); target.setUsername("monitor"); target.setPasswordEncrypted("secret"); target.setSslMode("DISABLED");
        when(targets.findById(1L)).thenReturn(java.util.Optional.of(target));
        when(cipher.decrypt("secret")).thenReturn("pw");
        var broken = new java.util.concurrent.atomic.AtomicBoolean();
        var first = server(broken);
        var second = server(new java.util.concurrent.atomic.AtomicBoolean());
        var opened = new java.util.ArrayList<String>();
        var queue = new java.util.ArrayDeque<>(java.util.List.of(first, second));
        var source = new MysqlMetricSource(targets, cipher) {
            @Override protected java.sql.Connection open(String url, String username, String password) {
                opened.add(url); return queue.removeFirst();
            }
        };
        try {
            assertEquals(7D, source.readings(1, "db", 3306, "MYSQL_THREADS_CONNECTED", 5000).get(0).value());
            source.forgetSnapshot(1);
            assertEquals(100D, source.readings(1, "db", 3306, "MYSQL_UPTIME", 5000).get(0).value());
            assertEquals(1, opened.size(), "the second cycle reuses the open connection");
            assertTrue(opened.get(0).contains("cacheServerConfiguration=true") && opened.get(0).contains("readOnlyPropagatesToServer=false"));
            verify(targets, times(1)).findById(1L);

            broken.set(true);
            source.forgetSnapshot(1);
            assertEquals(7D, source.readings(1, "db", 3306, "MYSQL_THREADS_CONNECTED", 5000).get(0).value());
            assertEquals(2, opened.size(), "a dropped connection is replaced within the same cycle");
            verify(first).close();

            source.invalidate(1);
            verify(second).close();
        } finally { source.close(); }
    }
}

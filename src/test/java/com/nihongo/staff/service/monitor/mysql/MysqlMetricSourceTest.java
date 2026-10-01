package com.nihongo.staff.service.monitor.mysql;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
}

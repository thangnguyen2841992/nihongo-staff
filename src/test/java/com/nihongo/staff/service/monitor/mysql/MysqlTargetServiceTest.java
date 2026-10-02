package com.nihongo.staff.service.monitor.mysql;

import com.nihongo.staff.model.monitoring.ExporterType;
import com.nihongo.staff.model.monitoring.MonitorMysqlTarget;
import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.repository.MonitorMysqlTargetRepository;
import com.nihongo.staff.repository.MonitorVpsRepository;
import com.nihongo.staff.service.monitor.collection.MetricCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MysqlTargetServiceTest {
    @Test
    void replacesUndecryptablePasswordAfterSuccessfulProbeWithoutRecreatingTarget() {
        var fixture = new Fixture();
        var oldKey = new byte[32];
        var newKey = new byte[32];
        newKey[0] = 1;
        fixture.target.setPasswordEncrypted(
                new MysqlCredentialCipher(Base64.getEncoder().encodeToString(oldKey)).encrypt("old-password"));
        var cipher = new MysqlCredentialCipher(Base64.getEncoder().encodeToString(newKey));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(fixture.target.getPasswordEncrypted()));
        when(fixture.source.probe("db.example.com", 3306, "monitor", "new-password", "DISABLED", 5000))
                .thenReturn(new MysqlMetricSource.Probe("8.0", Map.of("uptime", 1D)));
        fixture.service(cipher).replacePassword(42L, "new-password");

        assertEquals("new-password", cipher.decrypt(fixture.target.getPasswordEncrypted()));
        verify(fixture.targets).save(fixture.target);
        verify(fixture.source).invalidate(42L);
        verify(fixture.servers, never()).save(any());
    }

    @Test
    void leavesEncryptedPasswordUntouchedWhenProbeFails() {
        var fixture = new Fixture();
        fixture.target.setPasswordEncrypted("existing-ciphertext");
        var cipher = new MysqlCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        when(fixture.source.probe("db.example.com", 3306, "monitor", "wrong", "DISABLED", 5000))
                .thenThrow(new IllegalStateException("MySQL từ chối tài khoản"));

        assertThrows(ResponseStatusException.class,
                () -> fixture.service(cipher).replacePassword(42L, "wrong"));
        assertEquals("existing-ciphertext", fixture.target.getPasswordEncrypted());
        verify(fixture.targets, never()).save(any());
        verify(fixture.source, never()).invalidate(anyLong());
    }

    private static class Fixture {
        final MonitorVpsRepository servers = mock(MonitorVpsRepository.class);
        final MonitorMysqlTargetRepository targets = mock(MonitorMysqlTargetRepository.class);
        final MysqlMetricSource source = mock(MysqlMetricSource.class);
        final MetricCatalog catalog = mock(MetricCatalog.class);
        final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        final MonitorMysqlTarget target = new MonitorMysqlTarget();

        Fixture() {
            var server = new MonitorVps();
            server.setVpsId(42L);
            server.setIpAddress("db.example.com");
            server.setAgentPort(3306);
            server.setExporterType(ExporterType.MYSQL_JDBC);
            target.setVpsId(42L);
            target.setUsername("monitor");
            target.setSslMode("DISABLED");
            when(servers.findById(42L)).thenReturn(Optional.of(server));
            when(targets.findById(42L)).thenReturn(Optional.of(target));
            when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        }

        MysqlTargetService service(MysqlCredentialCipher cipher) {
            return new MysqlTargetService(servers, targets, source, cipher, catalog, transactions);
        }
    }
}

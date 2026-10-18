package com.nihongo.staff.service.monitor.kafka;

import com.nihongo.staff.model.monitoring.ExporterType;
import com.nihongo.staff.model.monitoring.MonitorKafkaTarget;
import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.dto.KafkaTargetRequest;
import com.nihongo.staff.repository.MonitorKafkaTargetRepository;
import com.nihongo.staff.repository.MonitorVpsRepository;
import com.nihongo.staff.service.monitor.collection.MetricCatalog;
import com.nihongo.staff.service.monitor.mysql.MysqlCredentialCipher;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KafkaTargetServiceTest {
    @Test void normalizesBrokersAndKeepsSaslCredentials() {
        var result = KafkaTargetService.validate(new KafkaTargetRequest("  production-kafka  ",
                "Broker-A:9092, broker-b:9093, BROKER-A:9092", "sasl_ssl",
                "scram-sha-512", "monitor", "secret"));

        assertEquals("production-kafka", result.name());
        assertEquals("broker-a:9092,broker-b:9093", result.bootstrapServers());
        assertEquals("broker-a", result.primaryHost());
        assertEquals(9092, result.primaryPort());
        assertEquals("SASL_SSL", result.securityProtocol());
        assertEquals("SCRAM-SHA-512", result.saslMechanism());
        assertEquals("monitor", result.username());
        assertEquals("secret", result.password());
    }

    @Test void plaintextDropsUnexpectedCredentials() {
        var result = KafkaTargetService.validate(new KafkaTargetRequest("local", "localhost:9092",
                "PLAINTEXT", "PLAIN", "ignored", "ignored"));

        assertNull(result.saslMechanism());
        assertNull(result.username());
        assertNull(result.password());
    }

    @Test void saslRequiresSupportedMechanismAndCredentials() {
        var error = assertThrows(ResponseStatusException.class, () -> KafkaTargetService.validate(
                new KafkaTargetRequest("cluster", "broker:9092", "SASL_SSL",
                        "GSSAPI", "", "")));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test void rejectsInvalidBrokerAndPort() {
        assertThrows(ResponseStatusException.class, () -> KafkaTargetService.validate(
                new KafkaTargetRequest("cluster", "https://broker:9092", "SSL", null, null, null)));
        assertThrows(ResponseStatusException.class, () -> KafkaTargetService.validate(
                new KafkaTargetRequest("cluster", "broker:70000", "SSL", null, null, null)));
    }

    @Test void listsKafkaTargetsWithTwoDatabaseQueriesInsteadOfOnePerTarget() {
        var servers = mock(MonitorVpsRepository.class);
        var targets = mock(MonitorKafkaTargetRepository.class);
        var first = server(2L, "Kafka 2");
        var second = server(1L, "Kafka 1");
        var firstConfig = target(2L, "broker-2:9092");
        var secondConfig = target(1L, "broker-1:9092");
        when(servers.findAllByExporterTypeOrderByVpsIdDesc(ExporterType.KAFKA_ADMIN))
                .thenReturn(List.of(first, second));
        when(targets.findAllById(List.of(2L, 1L))).thenReturn(List.of(firstConfig, secondConfig));
        var service = new KafkaTargetService(servers, targets, mock(KafkaMetricSource.class),
                mock(MysqlCredentialCipher.class), mock(MetricCatalog.class),
                mock(PlatformTransactionManager.class));

        var result = service.list();

        assertEquals(List.of("broker-2:9092", "broker-1:9092"),
                result.stream().map(item -> item.getBootstrapServers()).toList());
        verify(targets).findAllById(List.of(2L, 1L));
        verify(targets, never()).findById(anyLong());
    }

    private static MonitorVps server(long id, String name) {
        var server = new MonitorVps();
        server.setVpsId(id); server.setHostname(name); server.setIpAddress("broker");
        server.setAgentPort(9092); server.setExporterType(ExporterType.KAFKA_ADMIN);
        return server;
    }

    private static MonitorKafkaTarget target(long id, String brokers) {
        var target = new MonitorKafkaTarget();
        target.setVpsId(id); target.setBootstrapServers(brokers); target.setSecurityProtocol("PLAINTEXT");
        return target;
    }
}

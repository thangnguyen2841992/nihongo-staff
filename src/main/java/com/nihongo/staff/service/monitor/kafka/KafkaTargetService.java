package com.nihongo.staff.service.monitor.kafka;

import com.nihongo.staff.model.monitoring.ExporterType;
import com.nihongo.staff.model.monitoring.MonitorKafkaTarget;
import com.nihongo.staff.model.monitoring.MonitorVps;
import com.nihongo.staff.model.monitoring.VpsStatus;
import com.nihongo.staff.model.monitoring.dto.KafkaCredentialsUpdateRequest;
import com.nihongo.staff.model.monitoring.dto.KafkaProbeResponse;
import com.nihongo.staff.model.monitoring.dto.KafkaTargetRequest;
import com.nihongo.staff.model.monitoring.dto.MonitorVpsResponse;
import com.nihongo.staff.repository.MonitorKafkaTargetRepository;
import com.nihongo.staff.repository.MonitorVpsRepository;
import com.nihongo.staff.service.monitor.collection.MetricCatalog;
import com.nihongo.staff.service.monitor.mysql.MysqlCredentialCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class KafkaTargetService {
    private static final Set<String> PROTOCOLS = Set.of("PLAINTEXT", "SSL", "SASL_SSL");
    private static final Set<String> MECHANISMS = Set.of("PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512");
    private static final Pattern BROKER = Pattern.compile("([A-Za-z0-9][A-Za-z0-9.-]{0,252}):(\\d{1,5})");
    private final MonitorVpsRepository servers;
    private final MonitorKafkaTargetRepository targets;
    private final KafkaMetricSource source;
    private final MysqlCredentialCipher cipher;
    private final MetricCatalog catalog;
    private final PlatformTransactionManager transactionManager;

    public List<MonitorVpsResponse> list() {
        List<MonitorVps> kafkaServers = servers.findAllByExporterTypeOrderByVpsIdDesc(ExporterType.KAFKA_ADMIN);
        Map<Long, MonitorKafkaTarget> configurations = targets.findAllById(
                        kafkaServers.stream().map(MonitorVps::getVpsId).toList()).stream()
                .collect(Collectors.toMap(MonitorKafkaTarget::getVpsId, Function.identity()));
        return kafkaServers.stream()
                .map(server -> configurations.containsKey(server.getVpsId())
                        ? MonitorVpsResponse.fromKafka(server, configurations.get(server.getVpsId()))
                        : MonitorVpsResponse.from(server))
                .toList();
    }

    public KafkaProbeResponse probe(KafkaTargetRequest request) {
        Validated valid = validate(request);
        try {
            return response(source.probe(valid.bootstrapServers(), valid.securityProtocol(),
                    valid.saslMechanism(), valid.username(), valid.password(), 10000));
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage());
        }
    }

    public MonitorVpsResponse register(KafkaTargetRequest request) {
        Validated valid = validate(request);
        KafkaProbeResponse probe = probe(request);
        String encrypted = encrypt(valid.password());
        return new TransactionTemplate(transactionManager).execute(status -> {
            if (servers.existsByHostname(valid.name()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên target này đã được đăng ký.");
            if (targets.existsByBootstrapServers(valid.bootstrapServers()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cụm Kafka này đã được đăng ký.");
            MonitorVps server = new MonitorVps();
            server.setHostname(valid.name()); server.setIpAddress(valid.primaryHost());
            server.setAgentPort(valid.primaryPort()); server.setExporterType(ExporterType.KAFKA_ADMIN);
            server.setOsType("Kafka"); server.setOsVersion(probe.clusterId()); server.setArchitecture(null);
            server.setStatus(VpsStatus.UP); server.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));
            servers.saveAndFlush(server);
            MonitorKafkaTarget target = new MonitorKafkaTarget();
            target.setVpsId(server.getVpsId()); target.setBootstrapServers(valid.bootstrapServers());
            target.setSecurityProtocol(valid.securityProtocol()); target.setSaslMechanism(valid.saslMechanism());
            target.setUsername(valid.username()); target.setPasswordEncrypted(encrypted);
            targets.save(target); catalog.bindDefaults(server);
            return MonitorVpsResponse.fromKafka(server, target);
        });
    }

    public void replaceCredentials(long vpsId, KafkaCredentialsUpdateRequest request) {
        MonitorVps server = servers.findById(vpsId)
                .filter(vps -> ExporterType.forVps(vps) == ExporterType.KAFKA_ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy target Kafka."));
        MonitorKafkaTarget target = targets.findById(vpsId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thiếu cấu hình target Kafka."));
        if (!"SASL_SSL".equals(target.getSecurityProtocol()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target Kafka này không sử dụng SASL.");
        String username = trim(request == null ? null : request.username());
        String password = request == null ? null : request.password();
        if (username.isBlank() || username.length() > 256 || password == null || password.isBlank() || password.length() > 512)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin xác thực Kafka không hợp lệ.");
        try {
            source.probe(target.getBootstrapServers(), target.getSecurityProtocol(), target.getSaslMechanism(),
                    username, password, 10000);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage());
        }
        String encrypted = encrypt(password);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            MonitorKafkaTarget current = targets.findById(vpsId).orElseThrow();
            current.setUsername(username); current.setPasswordEncrypted(encrypted); targets.save(current);
        });
        source.invalidate(server.getVpsId());
    }

    private String encrypt(String password) {
        if (password == null) return null;
        try { return cipher.encrypt(password); }
        catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage());
        }
    }

    private static KafkaProbeResponse response(KafkaMetricSource.Probe probe) {
        return new KafkaProbeResponse(probe.clusterId(), probe.brokerCount(), probe.controllerId(),
                probe.topicCount(), probe.partitionCount(), probe.consumerGroupCount());
    }

    record Validated(String name, String bootstrapServers, String primaryHost, int primaryPort,
                     String securityProtocol, String saslMechanism, String username, String password) {}

    static Validated validate(KafkaTargetRequest request) {
        if (request == null) throw bad("Thiếu thông tin target Kafka.");
        String name = trim(request.name());
        if (name.isBlank() || name.length() > 200) throw bad("Tên target phải từ 1 đến 200 ký tự.");
        String raw = trim(request.bootstrapServers());
        if (raw.isBlank() || raw.length() > 1000) throw bad("Bootstrap servers Kafka không hợp lệ.");
        String[] input = raw.split(",");
        if (input.length > 10) throw bad("Chỉ cấu hình tối đa 10 bootstrap broker.");
        List<String> brokers = new ArrayList<>();
        String primaryHost = null; int primaryPort = 0;
        for (String item : input) {
            var matcher = BROKER.matcher(item.trim());
            if (!matcher.matches()) throw bad("Broker Kafka phải có dạng hostname:port.");
            int port;
            try { port = Integer.parseInt(matcher.group(2)); }
            catch (NumberFormatException error) { throw bad("Cổng Kafka không hợp lệ."); }
            if (port < 1 || port > 65535) throw bad("Cổng Kafka phải từ 1 đến 65535.");
            String broker = matcher.group(1).toLowerCase(Locale.ROOT) + ":" + port;
            if (!brokers.contains(broker)) brokers.add(broker);
            if (primaryHost == null) { primaryHost = matcher.group(1).toLowerCase(Locale.ROOT); primaryPort = port; }
        }
        String protocol = trim(request.securityProtocol()).toUpperCase(Locale.ROOT);
        if (!PROTOCOLS.contains(protocol)) throw bad("Giao thức bảo mật Kafka không hợp lệ.");
        String mechanism = request.saslMechanism() == null ? null : request.saslMechanism().trim().toUpperCase(Locale.ROOT);
        String username = trim(request.username());
        String password = request.password();
        if ("SASL_SSL".equals(protocol)) {
            if (!MECHANISMS.contains(mechanism)) throw bad("Cơ chế SASL Kafka không hợp lệ.");
            if (username.isBlank() || username.length() > 256 || password == null || password.isBlank() || password.length() > 512)
                throw bad("Thông tin xác thực Kafka không hợp lệ.");
        } else {
            mechanism = null; username = null; password = null;
        }
        return new Validated(name, String.join(",", brokers), primaryHost, primaryPort,
                protocol, mechanism, username, password);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
}

package com.nihongo.staff.service.monitor.kafka;

import com.nihongo.staff.model.monitoring.MonitorKafkaTarget;
import com.nihongo.staff.repository.MonitorKafkaTargetRepository;
import com.nihongo.staff.service.monitor.collection.NodeMetricSource;
import com.nihongo.staff.service.monitor.collection.MetricSnapshotCache;
import com.nihongo.staff.service.monitor.mysql.MysqlCredentialCipher;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class KafkaMetricSource {
    private static final int MAX_GROUPS = 500;
    private final MonitorKafkaTargetRepository targets;
    private final MysqlCredentialCipher cipher;
    private final MetricSnapshotCache<Long, Snapshot> cache = new MetricSnapshotCache<>();
    public record Probe(String clusterId, int brokerCount, Integer controllerId, int topicCount,
                        int partitionCount, Integer consumerGroupCount) {}
    record Snapshot(Probe probe, double offlinePartitions, double underReplicatedPartitions,
                    Map<String, Double> consumerLag) {}

    public Probe probe(String bootstrapServers, String securityProtocol, String saslMechanism,
                       String username, String password, int timeoutMs) {
        return fetch(bootstrapServers, securityProtocol, saslMechanism, username, password, timeoutMs).probe();
    }

    public void invalidate(long vpsId) { cache.invalidate(vpsId); }

    public List<NodeMetricSource.Reading> readings(long vpsId, String code, int timeoutMs) {
        var snapshot = cache.get(vpsId, ignored -> {
            MonitorKafkaTarget target = targets.findById(vpsId)
                    .orElseThrow(() -> new IllegalStateException("Thiếu cấu hình kết nối Kafka."));
            String password = target.getPasswordEncrypted() == null ? null
                    : cipher.decrypt(target.getPasswordEncrypted());
            return fetch(target.getBootstrapServers(), target.getSecurityProtocol(),
                    target.getSaslMechanism(), target.getUsername(), password, timeoutMs);
        });
        return map(code, snapshot);
    }

    private Snapshot fetch(String bootstrapServers, String securityProtocol, String saslMechanism,
                           String username, String password, int timeoutMs) {
        int timeout = Math.max(1000, Math.min(30000, timeoutMs));
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        try {
            Admin admin = Admin.create(properties(bootstrapServers, securityProtocol, saslMechanism,
                    username, password, timeout));
            try {
                var cluster = admin.describeCluster();
                var topicNamesFuture = admin.listTopics().names();
                var groupsFuture = admin.listConsumerGroups().all();
                String clusterId = await(cluster.clusterId(), deadline);
                Collection<org.apache.kafka.common.Node> nodes = await(cluster.nodes(), deadline);
                org.apache.kafka.common.Node controller = await(cluster.controller(), deadline);
                var topicNames = await(topicNamesFuture, deadline);
                Map<String, org.apache.kafka.clients.admin.TopicDescription> descriptions = topicNames.isEmpty()
                        ? Map.of() : await(admin.describeTopics(topicNames).allTopicNames(), deadline);
                int partitions = 0;
                double offline = 0;
                double underReplicated = 0;
                for (var topic : descriptions.values()) {
                    partitions += topic.partitions().size();
                    for (var partition : topic.partitions()) {
                        if (partition.leader() == null || partition.leader().id() < 0) offline++;
                        if (partition.isr().size() < partition.replicas().size()) underReplicated++;
                    }
                }
                Integer groupCount = null;
                Map<String, Double> lag = Map.of();
                try {
                    var groups = await(groupsFuture, deadline);
                    groupCount = groups.size();
                    lag = consumerLag(admin, groups.stream().limit(MAX_GROUPS).map(g -> g.groupId()).toList(), deadline);
                } catch (InterruptedException interrupted) {
                    throw interrupted;
                } catch (Exception ignored) {
                    // Cluster-level metrics remain useful when the monitoring principal cannot inspect groups.
                }
                Probe probe = new Probe(clusterId, nodes.size(), controller == null ? null : controller.id(),
                        descriptions.size(), partitions, groupCount);
                return new Snapshot(probe, offline, underReplicated, lag);
            } finally {
                admin.close(Duration.ofMillis(500));
            }
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            Throwable cause = root(error);
            String detail = cause.getMessage();
            if (cause instanceof org.apache.kafka.common.errors.SslAuthenticationException)
                throw new IllegalStateException("Không xác thực được TLS Kafka. Kiểm tra chứng chỉ và hostname broker.", error);
            if (cause instanceof org.apache.kafka.common.errors.AuthenticationException)
                throw new IllegalStateException("Kafka từ chối thông tin xác thực.", error);
            if (cause instanceof java.util.concurrent.TimeoutException ||
                    cause instanceof org.apache.kafka.common.errors.TimeoutException)
                throw new IllegalStateException("Kafka không phản hồi trong thời gian cho phép.", error);
            throw new IllegalStateException(detail == null || detail.isBlank()
                    ? "Không kết nối được Kafka." : "Không kết nối được Kafka: " + detail, error);
        }
    }

    static <T> T await(KafkaFuture<T> future, long deadline) throws InterruptedException, ExecutionException, TimeoutException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new TimeoutException("Kafka collection deadline exceeded");
        return future.get(remaining, TimeUnit.NANOSECONDS);
    }

    private static Map<String, Double> consumerLag(Admin admin, List<String> groups, long deadline) throws Exception {
        Map<String, Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata>> committed = new TreeMap<>();
        Map<TopicPartition, OffsetSpec> latest = new HashMap<>();
        Map<String, KafkaFuture<Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata>>> pending =
                new TreeMap<>();
        for (String group : groups) {
            pending.put(group, admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata());
        }
        for (var request : pending.entrySet()) {
            var offsets = await(request.getValue(), deadline);
            String group = request.getKey();
            committed.put(group, offsets);
            offsets.keySet().forEach(partition -> latest.put(partition, OffsetSpec.latest()));
        }
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends = latest.isEmpty() ? Map.of()
                : await(admin.listOffsets(latest).all(), deadline);
        Map<String, Double> result = new TreeMap<>();
        committed.forEach((group, offsets) -> {
            double total = 0;
            for (var entry : offsets.entrySet()) {
                var end = ends.get(entry.getKey());
                long position = entry.getValue().offset();
                if (end != null && position >= 0) total += Math.max(0, end.offset() - position);
            }
            result.put(group, total);
        });
        return Map.copyOf(result);
    }

    static List<NodeMetricSource.Reading> map(String code, Snapshot snapshot) {
        Double value = switch (code) {
            case "KAFKA_BROKER_COUNT" -> (double) snapshot.probe().brokerCount();
            case "KAFKA_TOPIC_COUNT" -> (double) snapshot.probe().topicCount();
            case "KAFKA_PARTITION_COUNT" -> (double) snapshot.probe().partitionCount();
            case "KAFKA_OFFLINE_PARTITIONS" -> snapshot.offlinePartitions();
            case "KAFKA_UNDER_REPLICATED_PARTITIONS" -> snapshot.underReplicatedPartitions();
            case "KAFKA_CONSUMER_GROUP_COUNT" -> snapshot.probe().consumerGroupCount() == null
                    ? null : snapshot.probe().consumerGroupCount().doubleValue();
            default -> null;
        };
        if ("KAFKA_CONSUMER_LAG".equals(code)) {
            List<NodeMetricSource.Reading> readings = new ArrayList<>();
            snapshot.consumerLag().forEach((group, lag) -> readings.add(new NodeMetricSource.Reading(
                    "KAFKA_CONSUMER_GROUP", objectKey(group), Map.of("group", group), lag, null, null)));
            return readings;
        }
        return value == null ? List.of() : List.of(new NodeMetricSource.Reading(
                "VPS", "vps", Map.of("cluster_id", snapshot.probe().clusterId()), value, null, null));
    }

    private static Properties properties(String bootstrapServers, String securityProtocol, String mechanism,
                                         String username, String password, int timeout) {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(AdminClientConfig.CLIENT_ID_CONFIG, "nihongo-monitoring");
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, Integer.toString(timeout));
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, Integer.toString(timeout));
        properties.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, securityProtocol);
        if ("SSL".equals(securityProtocol) || "SASL_SSL".equals(securityProtocol))
            properties.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "https");
        if ("SASL_SSL".equals(securityProtocol)) {
            properties.put(SaslConfigs.SASL_MECHANISM, mechanism);
            String module = "PLAIN".equals(mechanism)
                    ? "org.apache.kafka.common.security.plain.PlainLoginModule"
                    : "org.apache.kafka.common.security.scram.ScramLoginModule";
            properties.put(SaslConfigs.SASL_JAAS_CONFIG, module + " required username=\"" + escape(username)
                    + "\" password=\"" + escape(password) + "\";");
        }
        return properties;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String objectKey(String group) {
        if (group.length() <= 255) return group;
        String suffix = UUID.nameUUIDFromBytes(group.getBytes(StandardCharsets.UTF_8)).toString();
        return group.substring(0, 218) + "#" + suffix;
    }

    private static Throwable root(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }
}

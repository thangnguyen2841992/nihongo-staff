package com.nihongo.staff.service.monitor.kafka;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.common.KafkaFuture;

class KafkaMetricSourceTest {
    @Test void expiredCollectionDeadlineDoesNotStartAnotherWait() {
        @SuppressWarnings("unchecked")
        KafkaFuture<String> future = mock(KafkaFuture.class);
        assertThrows(TimeoutException.class, () -> KafkaMetricSource.await(future, System.nanoTime() - 1));
        verifyNoInteractions(future);
    }

    @Test void eachKafkaWaitOnlyReceivesTheRemainingCollectionBudget() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaFuture<String> future = mock(KafkaFuture.class);
        long budget = TimeUnit.SECONDS.toNanos(1);
        when(future.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenAnswer(invocation -> {
            long remaining = invocation.getArgument(0);
            org.junit.jupiter.api.Assertions.assertTrue(remaining > 0 && remaining <= budget);
            return "result";
        });
        assertEquals("result", KafkaMetricSource.await(future, System.nanoTime() + budget));
    }
    private static KafkaMetricSource.Snapshot snapshot() {
        return new KafkaMetricSource.Snapshot(
                new KafkaMetricSource.Probe("cluster-1", 3, 1, 12, 48, 2),
                1D, 4D, Map.of("billing", 25D, "email", 0D));
    }

    @Test void mapsClusterGaugeWithClusterLabel() {
        var readings = KafkaMetricSource.map("KAFKA_BROKER_COUNT", snapshot());

        assertEquals(1, readings.size());
        assertEquals(3D, readings.get(0).value());
        assertEquals("cluster-1", readings.get(0).labels().get("cluster_id"));
    }

    @Test void mapsOneConsumerLagReadingPerGroup() {
        var readings = KafkaMetricSource.map("KAFKA_CONSUMER_LAG", snapshot());

        assertEquals(2, readings.size());
        assertEquals("KAFKA_CONSUMER_GROUP", readings.get(0).type());
        assertEquals(25D, readings.stream().filter(reading -> "billing".equals(reading.key()))
                .findFirst().orElseThrow().value());
    }

    @Test void createsStableDatabaseKeysForLongConsumerGroupNames() {
        String prefix = "group-".repeat(50);
        String first = KafkaMetricSource.objectKey(prefix + "a");
        String second = KafkaMetricSource.objectKey(prefix + "b");

        assertEquals(255, first.length());
        assertEquals(first, KafkaMetricSource.objectKey(prefix + "a"));
        assertNotEquals(first, second);
    }

    @Test void reusesOneAdminClientPerTargetAndReplacesItAfterAFailure() throws Exception {
        var targets = mock(com.nihongo.staff.repository.MonitorKafkaTargetRepository.class);
        var cipher = mock(com.nihongo.staff.service.monitor.mysql.MysqlCredentialCipher.class);
        var target = new com.nihongo.staff.model.monitoring.MonitorKafkaTarget();
        target.setVpsId(1L); target.setBootstrapServers("broker:9092"); target.setSecurityProtocol("PLAINTEXT");
        when(targets.findById(1L)).thenReturn(java.util.Optional.of(target));
        var first = mock(org.apache.kafka.clients.admin.Admin.class);
        var second = mock(org.apache.kafka.clients.admin.Admin.class);
        var opened = new java.util.ArrayDeque<>(java.util.List.of(first, second));
        var broken = new java.util.concurrent.atomic.AtomicBoolean();
        var source = new KafkaMetricSource(targets, cipher) {
            @Override protected org.apache.kafka.clients.admin.Admin open(Account account, int timeout) { return opened.removeFirst(); }
            @Override protected Snapshot read(org.apache.kafka.clients.admin.Admin admin, int timeout) {
                if (admin == first && broken.get()) throw new IllegalStateException("broker restarted");
                return snapshot();
            }
        };
        try {
            assertEquals(3D, source.readings(1, "KAFKA_BROKER_COUNT", 5000).get(0).value());
            ReflectionHelper.forget(source, 1L);
            source.readings(1, "KAFKA_TOPIC_COUNT", 5000);
            assertEquals(1, 2 - opened.size(), "the second cycle reuses the open client");
            verify(targets, times(1)).findById(1L);

            broken.set(true);
            ReflectionHelper.forget(source, 1L);
            assertEquals(3D, source.readings(1, "KAFKA_BROKER_COUNT", 5000).get(0).value());
            assertEquals(0, opened.size(), "a broken client is replaced within the same cycle");
            verify(first).close(any(java.time.Duration.class));

            source.invalidate(1);
            verify(second).close(any(java.time.Duration.class));
        } finally { source.close(); }
    }

    /** Drops only the 3-second snapshot so the next call reads again without waiting. */
    static final class ReflectionHelper {
        @SuppressWarnings("unchecked")
        static void forget(KafkaMetricSource source, long id) {
            var cache = (com.nihongo.staff.service.monitor.collection.MetricSnapshotCache<Long, ?>)
                    org.springframework.test.util.ReflectionTestUtils.getField(source, "cache");
            cache.invalidate(id);
        }
    }
}

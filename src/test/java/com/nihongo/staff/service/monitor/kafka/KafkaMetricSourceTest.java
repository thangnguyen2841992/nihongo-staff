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
}

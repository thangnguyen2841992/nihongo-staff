package com.nihongo.staff.service.monitor.check;

import com.nihongo.staff.model.monitoring.*;
import com.nihongo.staff.model.monitoring.dto.CheckTargetRequest;
import com.nihongo.staff.repository.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckMetricSourceTest {
    final CheckMetricSource source = new CheckMetricSource(mock(MonitorCheckTargetRepository.class));
    HttpServer server;
    ExecutorService executor;
    @AfterEach void close() { source.close(); if (server != null) server.stop(0); if (executor != null) executor.shutdownNow(); }
    String start(String path, int code) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(); server.setExecutor(executor);
        server.createContext(path, exchange -> { exchange.sendResponseHeaders(code, -1); exchange.close(); });
        server.start(); return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
    CheckTargetService.Configuration web(String url, int min, int max) {
        return CheckTargetService.validate(new CheckTargetRequest("test", "WEB_CHECK", null, null, url, min, max));
    }
    @Test void tcpConnectsAndRecordsRefusedPortsAsZeroAvailability() throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
            assertTrue(source.probe(new CheckTargetService.Configuration("tcp", ExporterType.PORT_CHECK, "127.0.0.1", port, null, 200, 399), 1000).healthy());
        }
        var result = source.probe(new CheckTargetService.Configuration("tcp", ExporterType.PORT_CHECK, "127.0.0.1", port, null, 200, 399), 1000);
        assertFalse(result.healthy()); assertTrue(result.responseMs() >= 0);
        assertEquals(0D, CheckMetricSource.readings("PORT_AVAILABLE", result).get(0).value());
    }
    @Test void checksStatusRangeAndDoesNotFollowRedirects() throws Exception {
        String url = start("/health", 503);
        var result = source.probe(web(url, 200, 399), 1000);
        assertFalse(result.healthy()); assertEquals(503, result.statusCode());
        assertTrue(source.probe(web(url, 500, 599), 1000).healthy());
        var followed = new AtomicInteger();
        server.createContext("/destination", exchange -> { followed.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.createContext("/redirect", exchange -> { exchange.getResponseHeaders().set("Location", "/destination"); exchange.sendResponseHeaders(302, -1); exchange.close(); });
        var redirect = source.probe(web(url.replace("/health", "/redirect"), 200, 399), 1000);
        assertTrue(redirect.healthy()); assertEquals(302, redirect.statusCode()); assertEquals(0, followed.get());
    }
    @Test void timesOutWhileWaitingForHeadersAndDiscardsLargeResponseBodies() throws Exception {
        String url = start("/health", 200);
        var release = new CountDownLatch(1);
        server.createContext("/slow", exchange -> {
            try { release.await(3, TimeUnit.SECONDS); exchange.sendResponseHeaders(200, -1); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        try { assertFalse(source.probe(web(url.replace("/health", "/slow"), 200, 399), 1000).healthy()); }
        finally { release.countDown(); }
        assertTrue(source.probe(web(url, 200, 399), 1000).healthy());
        var finishBody = new CountDownLatch(1);
        server.createContext("/stream", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().write(1); exchange.getResponseBody().flush();
                finishBody.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        try {
            assertTrue(executor.submit(() -> source.probe(web(url.replace("/health", "/stream"), 200, 399), 1000)).get(2, TimeUnit.SECONDS).healthy());
        } finally { finishBody.countDown(); }
    }
    @Test void rejectsLinkLocalMetadataAndMulticastBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () -> CheckMetricSource.addresses("169.254.169.254"));
        assertThrows(IllegalArgumentException.class, () -> CheckMetricSource.addresses("224.0.0.1"));
        assertThrows(IllegalArgumentException.class, () -> CheckMetricSource.addresses("0.0.0.0"));
    }
    @Test void dnsLookupCannotHoldTheCollectionWorkerBeyondItsTimeout() throws Exception {
        var slow = spy(new CheckMetricSource(mock(MonitorCheckTargetRepository.class)));
        var release = new CountDownLatch(1);
        doAnswer(invocation -> { release.await(5, TimeUnit.SECONDS); return new InetAddress[]{InetAddress.getLoopbackAddress()}; })
                .when(slow).resolveHost("slow.test");
        executor = Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> slow.probe(new CheckTargetService.Configuration("dns", ExporterType.PORT_CHECK,
                    "slow.test", 80, null, 200, 399), 1000)).get(3, TimeUnit.SECONDS);
            assertFalse(result.healthy()); assertEquals("Hết thời gian kiểm tra.", result.message());
        } finally { release.countDown(); slow.close(); }
    }
    @Test void reusesAProbeForAllMetricsOfOneTarget() throws Exception {
        String url = start("/health", 200);
        var configurations = mock(MonitorCheckTargetRepository.class);
        var target = mock(MonitorCheckTargetRepository.Connection.class);
        when(target.getName()).thenReturn("web"); when(target.getHost()).thenReturn("127.0.0.1");
        when(target.getPort()).thenReturn(server.getAddress().getPort()); when(target.getUrl()).thenReturn(url);
        when(target.getType()).thenReturn(ExporterType.WEB_CHECK); when(target.getStatusMin()).thenReturn(200); when(target.getStatusMax()).thenReturn(399);
        when(configurations.connection(1L)).thenReturn(Optional.of(target));
        var cached = new CheckMetricSource(configurations);
        try {
            assertSame(cached.collect(1, 1000), cached.collect(1, 1000));
            verify(configurations, times(1)).connection(1L);
        } finally { cached.close(); }
    }
    @Test void removesExpiredQueuedLookupsAndReportsCapacityAsAnInternalFailure() throws Exception {
        var pool = (ThreadPoolExecutor) org.springframework.test.util.ReflectionTestUtils.getField(source, "dns");
        var entered = new CountDownLatch(2); var release = new CountDownLatch(1);
        Runnable block = () -> { entered.countDown(); try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); } };
        pool.execute(block); pool.execute(block);
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var configuration = new CheckTargetService.Configuration("busy", ExporterType.PORT_CHECK, "localhost", 80, null, 200, 399);
            assertThrows(CheckMetricSource.ProbeUnavailableException.class, () -> source.probe(configuration, 1000));
            assertTrue(pool.getQueue().isEmpty(), "An expired DNS lookup must not occupy the bounded queue");
            for (int index = 0; index < 16; index++) pool.execute(() -> {});
            assertThrows(CheckMetricSource.ProbeUnavailableException.class, () -> source.probe(configuration, 1000));
        } finally { release.countDown(); }
    }
    @Test void leavesTimeForAnIpv4FallbackWhenTheFirstAddressTimesOut() throws Exception {
        var dual = spy(new CheckMetricSource(mock(MonitorCheckTargetRepository.class)));
        var first = InetAddress.getByName("::1"); var second = InetAddress.getByName("127.0.0.1");
        doReturn(new InetAddress[]{first, second}).when(dual).resolveHost("dual.test");
        doThrow(new SocketTimeoutException()).when(dual).connectAddress(eq(first), eq(80), anyInt());
        doNothing().when(dual).connectAddress(eq(second), eq(80), anyInt());
        try {
            assertTrue(dual.probe(new CheckTargetService.Configuration("dual", ExporterType.PORT_CHECK, "dual.test", 80, null, 200, 399), 1000).healthy());
            verify(dual).connectAddress(eq(first), eq(80), intThat(budget -> budget > 0 && budget <= 500));
            verify(dual).connectAddress(eq(second), eq(80), intThat(budget -> budget > 0));
        } finally { dual.close(); }
    }
    @Test void busyManualProbesDoNotBlockScheduledChecks() throws Exception {
        String url = start("/health", 200);
        var configurations = mock(MonitorCheckTargetRepository.class);
        var target = mock(MonitorCheckTargetRepository.Connection.class);
        when(target.getName()).thenReturn("web"); when(target.getHost()).thenReturn("127.0.0.1");
        when(target.getPort()).thenReturn(server.getAddress().getPort()); when(target.getUrl()).thenReturn(url);
        when(target.getType()).thenReturn(ExporterType.WEB_CHECK); when(target.getStatusMin()).thenReturn(200); when(target.getStatusMax()).thenReturn(399);
        when(configurations.connection(1L)).thenReturn(Optional.of(target));
        var checks = new CheckMetricSource(configurations);
        var manual = (ThreadPoolExecutor) org.springframework.test.util.ReflectionTestUtils.getField(checks, "dns");
        var release = new CountDownLatch(1);
        Runnable block = () -> { try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); } };
        try {
            for (int index = 0; index < 2 + 16; index++) manual.execute(block);
            assertTrue(checks.collect(1, 2000).healthy());
        } finally { release.countDown(); checks.close(); }
    }
}

package com.nihongo.staff.service.monitor.check;

import com.nihongo.staff.model.monitoring.ExporterType;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.monitor.collection.MetricSnapshotCache;
import com.nihongo.staff.service.monitor.collection.NodeMetricSource;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

@Component
public class CheckMetricSource {
    public record Probe(boolean healthy, double responseMs, Integer statusCode, String message) {}
    private record CacheKey(long vpsId, int timeout) {}
    private final MonitorCheckTargetRepository targets;
    private final MetricSnapshotCache<CacheKey, Probe> snapshots = new MetricSnapshotCache<>();
    private final ThreadPoolExecutor dns = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), work -> { var thread = new Thread(work, "monitor-check-dns"); thread.setDaemon(true); return thread; });
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector() {
                @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                @Override public void connectFailed(URI uri, SocketAddress address, IOException error) { }
            }).build();
    public static class ProbeUnavailableException extends IllegalStateException {
        public ProbeUnavailableException() { super("Bộ kiểm tra DNS đang bận. Vui lòng thử lại."); }
    }
    public CheckMetricSource(MonitorCheckTargetRepository targets) {
        this.targets = targets;
    }
    public Probe collect(long vpsId, int timeout) {
        return snapshots.get(new CacheKey(vpsId, timeout), ignored -> {
            var target = targets.connection(vpsId).orElseThrow(() -> new IllegalStateException("Thiếu cấu hình kiểm tra kết nối."));
            var type = target.getType();
            if (type != ExporterType.PORT_CHECK && type != ExporterType.WEB_CHECK) throw new IllegalStateException("Type target không phải Port/Web Check.");
            return probe(new CheckTargetService.Configuration(target.getName(), type, target.getHost(), target.getPort(),
                    target.getUrl(), target.getStatusMin(), target.getStatusMax()), timeout);
        });
    }
    public Probe probe(CheckTargetService.Configuration config, int timeoutMs) {
        long started = System.nanoTime();
        int timeout = Math.max(1000, Math.min(30000, timeoutMs));
        try {
            long deadline = started + TimeUnit.MILLISECONDS.toNanos(timeout);
            var resolverStarted = new java.util.concurrent.atomic.AtomicLong();
            var lookup = new FutureTask<>(() -> { resolverStarted.set(System.nanoTime()); return resolveHost(config.host()); });
            InetAddress[] addresses;
            try {
                dns.execute(lookup);
                addresses = lookup.get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException error) {
                if (resolverStarted.get() == 0 || resolverStarted.get() >= deadline) throw new ProbeUnavailableException();
                throw error;
            } finally { lookup.cancel(true); dns.remove(lookup); }
            if (config.type() == ExporterType.PORT_CHECK) {
                IOException last = null;
                for (int index = 0; index < addresses.length; index++) {
                    var address = addresses[index];
                    int remaining = timeout - (int) ((System.nanoTime() - started) / 1_000_000);
                    if (remaining <= 0) throw new SocketTimeoutException();
                    try {
                        connectAddress(address, config.port(), Math.max(1, remaining / (addresses.length - index)));
                        return result(started, true, null, "Kết nối TCP thành công.");
                    } catch (IOException error) { last = error; }
                }
                throw last == null ? new ConnectException() : last;
            }
            long remaining = timeout - (System.nanoTime() - started) / 1_000_000;
            if (remaining <= 0) throw new HttpTimeoutException("Deadline exceeded");
            var request = HttpRequest.newBuilder(URI.create(config.url())).timeout(Duration.ofMillis(remaining))
                    .header("User-Agent", "Nihongo-Monitor/1.0").GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                int code = response.statusCode();
                boolean healthy = code >= config.statusMin() && code <= config.statusMax();
                return result(started, healthy, code, healthy ? "HTTP " + code + " đúng khoảng mong đợi."
                        : "HTTP " + code + " ngoài khoảng " + config.statusMin() + "–" + config.statusMax() + ".");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Đã dừng kiểm tra kết nối.", error);
        } catch (Exception error) {
            if (error instanceof ProbeUnavailableException unavailable) throw unavailable;
            if (error instanceof RejectedExecutionException) throw new ProbeUnavailableException();
            Throwable cause = error;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            String detail = cause instanceof UnknownHostException ? "Không phân giải được hostname."
                    : causedBy(error, java.net.http.HttpTimeoutException.class) || cause instanceof SocketTimeoutException || cause instanceof TimeoutException ? "Hết thời gian kiểm tra."
                    : causedBy(error, javax.net.ssl.SSLException.class) ? "Chứng chỉ hoặc kết nối TLS không hợp lệ."
                    : cause instanceof IllegalArgumentException ? "Địa chỉ kiểm tra không được phép."
                    : "Không kết nối được tới dịch vụ.";
            return result(started, false, null, detail);
        }
    }
    protected void connectAddress(InetAddress address, int port, int timeout) throws IOException {
        try (Socket socket = new Socket()) { socket.connect(new InetSocketAddress(address, port), timeout); }
    }
    private static boolean causedBy(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return true;
            if (cause == cause.getCause()) break;
        }
        return false;
    }
    protected InetAddress[] resolveHost(String host) throws UnknownHostException { return addresses(host); }
    @jakarta.annotation.PreDestroy public void close() { dns.shutdownNow(); }
    private static Probe result(long started, boolean healthy, Integer status, String message) {
        return new Probe(healthy, (System.nanoTime() - started) / 1_000_000D, status, message);
    }
    static InetAddress[] addresses(String host) throws UnknownHostException {
        var addresses = InetAddress.getAllByName(host);
        for (var address : addresses) {
            if (address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()
                    || address.getHostAddress().equals("100.100.100.200"))
                throw new IllegalArgumentException("Destination is not allowed");
        }
        return addresses;
    }
    public static List<NodeMetricSource.Reading> readings(String code, Probe probe) {
        Double value;
        if (code.endsWith("_AVAILABLE")) value = probe.healthy() ? 1D : 0D;
        else if (code.endsWith("_RESPONSE_MS")) value = probe.responseMs();
        else if (code.equals("WEB_STATUS_CODE")) value = probe.statusCode() == null ? 0D : probe.statusCode().doubleValue();
        else throw new IllegalArgumentException("Metric kiểm tra không được hỗ trợ.");
        return List.of(new NodeMetricSource.Reading("VPS", "vps", Map.of(), value, null, null));
    }
}

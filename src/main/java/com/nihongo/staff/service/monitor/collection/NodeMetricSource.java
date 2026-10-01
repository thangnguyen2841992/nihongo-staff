package com.nihongo.staff.service.monitor.collection;

import com.nihongo.staff.model.monitoring.ExporterType;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

@Component
public class NodeMetricSource {
    public record Sample(String metric, Map<String, String> labels, double value) {}
    public record Reading(String type, String key, Map<String, String> labels, Double value, Double counter, Double auxiliary) {}
    private static final Pattern LINE = Pattern.compile("^((?:node|windows)_[a-zA-Z0-9_]+)(?:\\{(.*)\\})?\\s+([^\\s]+)(?:\\s+.*)?$");
    private static final Pattern LABEL = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)=\"((?:\\\\.|[^\"\\\\])*)\"");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();

    public List<Sample> fetch(String host, int port, int timeoutMs) {
        try {
            URI uri = new URI("http", null, host, port, "/metrics", null, null);
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeoutMs)).header("Accept", "text/plain").GET().build();
            var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> response;
            try { response = future.get(timeoutMs, TimeUnit.MILLISECONDS); }
            finally { if (!future.isDone()) future.cancel(true); }
            if (response.statusCode() != 200) throw new IllegalStateException("Exporter trả HTTP " + response.statusCode());
            List<Sample> samples = parse(response.body());
            if (samples.stream().noneMatch(s -> s.metric().equals("node_exporter_build_info")
                    || s.metric().equals("windows_exporter_build_info")))
                throw new IllegalStateException("Không tìm thấy Node Exporter hoặc Windows Exporter tại địa chỉ này.");
            return samples;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            String reason = failureReason(e);
            throw new IllegalStateException("Không thể đọc exporter " + host + ":" + port + ": " + reason, e);
        }
    }
    static String failureReason(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        if (cause instanceof java.net.ConnectException || cause instanceof java.net.http.HttpConnectTimeoutException)
            return "không thiết lập được kết nối; kiểm tra IP, cổng và firewall.";
        if (cause instanceof java.net.UnknownHostException || cause instanceof java.nio.channels.UnresolvedAddressException)
            return "không phân giải được hostname.";
        if (cause instanceof java.util.concurrent.TimeoutException || cause instanceof java.net.http.HttpTimeoutException)
            return "quá thời gian chờ phản hồi.";
        if (cause instanceof IllegalStateException) return cause.getMessage();
        return "kết nối hoặc phản hồi không hợp lệ (" + cause.getClass().getSimpleName() + ").";
    }

    static List<Sample> parse(String text) {
        List<Sample> result = new ArrayList<>();
        for (String line : text.split("\\R")) {
            Matcher matcher = LINE.matcher(line.trim());
            if (!matcher.matches()) continue;
            Map<String, String> labels = new TreeMap<>();
            if (matcher.group(2) != null) {
                Matcher label = LABEL.matcher(matcher.group(2));
                while (label.find()) labels.put(label.group(1), unescape(label.group(2)));
            }
            try {
                double value = Double.parseDouble(matcher.group(3));
                if (Double.isFinite(value)) result.add(new Sample(matcher.group(1), labels, value));
            } catch (NumberFormatException ignored) { /* Non-numeric samples are not performance values. */ }
        }
        return result;
    }
    static String unescape(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) { c = text.charAt(++i); result.append(c == 'n' ? '\n' : c); }
            else result.append(c);
        }
        return result.toString();
    }
    public static ExporterType exporterType(List<Sample> samples) {
        boolean windows = samples.stream().anyMatch(s -> s.metric().equals("windows_exporter_build_info"));
        boolean node = samples.stream().anyMatch(s -> s.metric().equals("node_exporter_build_info"));
        if (windows && node) throw new IllegalStateException("Endpoint trả về nhiều loại exporter.");
        if (windows) return ExporterType.WINDOWS_EXPORTER;
        if (node) return ExporterType.NODE_EXPORTER;
        // Also classify fixtures and older exporters that omit build metadata.
        if (samples.stream().anyMatch(s -> s.metric().startsWith("windows_"))) return ExporterType.WINDOWS_EXPORTER;
        if (samples.stream().anyMatch(s -> s.metric().startsWith("node_"))) return ExporterType.NODE_EXPORTER;
        throw new IllegalStateException("Không nhận diện được loại exporter.");
    }
    public static List<Reading> readings(String code, List<Sample> samples) {
        return readings(code, samples, ExporterType.NODE_EXPORTER);
    }
    public static List<Reading> readings(String code, List<Sample> samples, ExporterType exporter) {
        if (exporter == ExporterType.WINDOWS_EXPORTER) return windowsReadings(code, samples);
        List<Reading> result = new ArrayList<>();
        if (code.equals("CPU_USAGE")) {
            Map<String, Double> totals = new TreeMap<>(), idle = new TreeMap<>();
            for (Sample sample : samples) if (sample.metric().equals("node_cpu_seconds_total") && sample.labels().containsKey("cpu")) {
                String cpu = sample.labels().get("cpu"), mode = sample.labels().get("mode");
                // guest/guest_nice are already included in user/nice by the Linux CPU counters.
                if (!Set.of("guest", "guest_nice").contains(mode == null ? "" : mode)) totals.merge(cpu, sample.value(), Double::sum);
                if ("idle".equals(mode)) idle.put(cpu, sample.value());
            }
            totals.forEach((cpu, total) -> { if (idle.containsKey(cpu)) result.add(new Reading("CPU", cpu, Map.of("cpu", cpu), null, total, idle.get(cpu))); });
        } else if (code.equals("DISK_USAGE")) {
            for (Sample size : samples) if (size.metric().equals("node_filesystem_size_bytes") && size.value() > 0 &&
                    !Set.of("tmpfs", "devtmpfs", "overlay", "squashfs").contains(size.labels().getOrDefault("fstype", ""))) {
                samples.stream().filter(s -> s.metric().equals("node_filesystem_avail_bytes") && s.labels().equals(size.labels())).findFirst().ifPresent(avail -> {
                    Map<String, String> labels = new TreeMap<>(); labels.put("device", size.labels().getOrDefault("device", "?")); labels.put("mountpoint", size.labels().getOrDefault("mountpoint", "?"));
                    result.add(new Reading("FILESYSTEM", key(labels), labels, 100 * (1 - avail.value() / size.value()), null, null));
                });
            }
        } else if (code.equals("NETWORK_RECEIVE") || code.equals("NETWORK_TRANSMIT")) {
            String name = code.equals("NETWORK_RECEIVE") ? "node_network_receive_bytes_total" : "node_network_transmit_bytes_total";
            for (Sample sample : samples) if (sample.metric().equals(name) && sample.labels().containsKey("device") && !sample.labels().get("device").equals("lo")) {
                String device = sample.labels().get("device"); result.add(new Reading("NETWORK", device, Map.of("device", device), null, sample.value(), null));
            }
        } else {
            Double value = null;
            if (code.equals("MEMORY_USAGE")) { Double total = scalar(samples, "node_memory_MemTotal_bytes"), available = scalar(samples, "node_memory_MemAvailable_bytes"); if (total != null && total > 0 && available != null) value = 100 * (1 - available / total); }
            else if (code.equals("LOAD_1M")) value = scalar(samples, "node_load1");
            else if (code.equals("UPTIME")) { Double time = scalar(samples, "node_time_seconds"), boot = scalar(samples, "node_boot_time_seconds"); if (time != null && boot != null) value = Math.max(0, time - boot); }
            else throw new IllegalArgumentException("Metric không có collector: " + code);
            if (value != null && Double.isFinite(value)) result.add(new Reading("VPS", "vps", Map.of(), value, null, null));
        }
        return result;
    }
    private static List<Reading> windowsReadings(String code, List<Sample> samples) {
        List<Reading> result = new ArrayList<>();
        if (code.equals("CPU_USAGE")) {
            Map<String, Double> totals = new TreeMap<>(), idle = new TreeMap<>();
            for (Sample sample : samples) if (sample.metric().equals("windows_cpu_time_total")) {
                String core = sample.labels().get("core"), mode = sample.labels().get("mode");
                if (core == null || core.equals("_Total")) continue;
                if (mode != null && Set.of("idle", "user", "privileged").contains(mode)) totals.merge(core, sample.value(), Double::sum);
                if ("idle".equals(mode)) idle.put(core, sample.value());
            }
            totals.forEach((core, total) -> {
                if (idle.containsKey(core) && total > idle.get(core))
                    result.add(new Reading("CPU", core, Map.of("cpu", core), null, total, idle.get(core)));
            });
        } else if (code.equals("DISK_USAGE")) {
            Map<String, Double> free = new HashMap<>();
            for (Sample sample : samples) if (sample.metric().equals("windows_logical_disk_free_bytes"))
                free.put(sample.labels().get("volume"), sample.value());
            for (Sample sample : samples) if (sample.metric().equals("windows_logical_disk_size_bytes")) {
                String volume = sample.labels().get("volume");
                if (volume == null || volume.equals("_Total") || sample.value() <= 0 || !free.containsKey(volume)) continue;
                Map<String, String> labels = Map.of("device", volume, "mountpoint", volume);
                result.add(new Reading("FILESYSTEM", key(labels), labels,
                        Math.max(0, Math.min(100, 100 * (1 - free.get(volume) / sample.value()))), null, null));
            }
        } else if (code.equals("NETWORK_RECEIVE") || code.equals("NETWORK_TRANSMIT")) {
            String metric = code.equals("NETWORK_RECEIVE") ? "windows_net_bytes_received_total" : "windows_net_bytes_sent_total";
            for (Sample sample : samples) if (sample.metric().equals(metric)) {
                String nic = sample.labels().get("nic");
                if (nic != null && !nic.toLowerCase(Locale.ROOT).contains("loopback"))
                    result.add(new Reading("NETWORK", nic, Map.of("device", nic), null, sample.value(), null));
            }
        } else {
            Double value = null;
            if (code.equals("MEMORY_USAGE")) {
                Double total = scalar(samples, "windows_memory_physical_total_bytes");
                Double available = scalar(samples, "windows_memory_available_bytes");
                if (total != null && total > 0 && available != null)
                    value = Math.max(0, Math.min(100, 100 * (1 - available / total)));
            } else if (code.equals("UPTIME")) {
                Double boot = scalar(samples, "windows_system_boot_time_timestamp");
                if (boot != null) value = Math.max(0, java.time.Instant.now().getEpochSecond() - boot);
            } else if (!code.equals("LOAD_1M")) {
                throw new IllegalArgumentException("Metric không có collector: " + code);
            }
            if (value != null && Double.isFinite(value)) result.add(new Reading("VPS", "vps", Map.of(), value, null, null));
        }
        return result;
    }
    private static Double scalar(List<Sample> samples, String name) { return samples.stream().filter(s -> s.metric().equals(name)).map(Sample::value).findFirst().orElse(null); }
    static String key(Map<String, String> labels) {
        // Length-prefixed components preserve identity even when labels contain delimiters.
        String key = labels.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> e.getKey() + ":" + e.getValue().length() + ":" + e.getValue()).reduce((a,b) -> a + "|" + b).orElse("vps");
        if (key.length() <= 255) return key;
        try { return "sha256:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

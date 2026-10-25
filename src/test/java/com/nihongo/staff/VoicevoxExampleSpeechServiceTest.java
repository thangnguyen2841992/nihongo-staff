package com.nihongo.staff;

import com.nihongo.staff.service.VoicevoxExampleSpeechService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VoicevoxExampleSpeechServiceTest {
    @TempDir Path cache;

    @Test void stripsRubyAndCachesGeneratedAudioUntilTheSentenceChanges() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var queries = new AtomicInteger();
        var syntheses = new AtomicInteger();
        server.createContext("/audio_query", exchange -> {
            queries.incrementAndGet();
            assertTrue(exchange.getRequestURI().getRawQuery().contains("speaker=3"));
            assertFalse(exchange.getRequestURI().getRawQuery().contains("%E3%81%AB%E3%81%BB%E3%82%93%E3%81%94"));
            byte[] response = "{\"speedScale\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) { body.write(response); }
        });
        server.createContext("/synthesis", exchange -> {
            syntheses.incrementAndGet();
            assertEquals("{\"speedScale\":1}", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] wav = new byte[44];
            System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, wav, 0, 4);
            exchange.sendResponseHeaders(200, wav.length);
            try (var body = exchange.getResponseBody()) { body.write(wav); }
        });
        server.start();
        try {
            var service = new VoicevoxExampleSpeechService("http://127.0.0.1:" + server.getAddress().getPort(), cache.toString());
            byte[] first = service.speech("<ruby>日本語<rt>にほんご</rt></ruby>を学ぶ。");
            assertArrayEquals(first, service.speech("<ruby>日本語<rt>にほんご</rt></ruby>を学ぶ。"));
            assertEquals(1, queries.get());
            assertEquals(1, syntheses.get());
            service.speech("日本語を話す。");
            assertEquals(2, queries.get());
        } finally {
            server.stop(0);
        }
    }

    @Test void rejectsEmptyTextWithoutCallingTheEngine() {
        var service = new VoicevoxExampleSpeechService("http://127.0.0.1:50021", cache.toString());
        assertThrows(ResponseStatusException.class, () -> service.speech("<ruby><rt>ふりがな</rt></ruby>"));
    }

    static byte[] wav(int marker) {
        byte[] wav = new byte[44];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, wav, 0, 4);
        wav[43] = (byte) marker;
        return wav;
    }

    @Test void aSlowSentenceDoesNotBlockOtherSentencesAndDuplicatesShareOneSynthesis() throws Exception {
        var release = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>();
        var service = new VoicevoxExampleSpeechService("http://127.0.0.1:1", cache.toString(), 2) {
            @Override protected byte[] synthesize(String text) throws InterruptedException {
                calls.computeIfAbsent(text, ignored -> new AtomicInteger()).incrementAndGet();
                if (text.equals("遅い文。")) release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return wav(text.length());
            }
        };
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var slow = pool.submit(() -> service.speech("遅い文。"));
            var duplicate = pool.submit(() -> service.speech("遅い文。"));
            long started = System.nanoTime();
            assertNotNull(service.speech("速い文です。"));
            assertTrue(System.nanoTime() - started < java.util.concurrent.TimeUnit.SECONDS.toNanos(2),
                    "another sentence is synthesized while the slow one is still running");
            release.countDown();
            assertArrayEquals(slow.get(5, java.util.concurrent.TimeUnit.SECONDS), duplicate.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, calls.get("遅い文。").get(), "concurrent requests for one sentence share a single synthesis");
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void etagFollowsTheSpokenTextNotTheMarkup() {
        var service = new VoicevoxExampleSpeechService("http://127.0.0.1:1", cache.toString());
        assertEquals(service.etag("<ruby>日本語<rt>にほんご</rt></ruby>を学ぶ。"), service.etag("日本語を学ぶ。"));
        assertNotEquals(service.etag("日本語を学ぶ。"), service.etag("日本語を話す。"));
        assertTrue(service.etag("日本語を学ぶ。").startsWith("\""));
    }
}

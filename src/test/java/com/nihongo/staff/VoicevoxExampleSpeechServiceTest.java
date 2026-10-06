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
}

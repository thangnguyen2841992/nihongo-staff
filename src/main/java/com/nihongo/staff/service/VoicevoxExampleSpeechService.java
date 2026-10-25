package com.nihongo.staff.service;

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class VoicevoxExampleSpeechService {
    private static final int MAX_TEXT_LENGTH = 500;
    // ずんだもん, normal style. The UI displays this voice's required credit.
    private static final int SPEAKER = 3;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final URI engine;
    private final Path cacheDirectory;
    // Requests for the same sentence share one synthesis; different sentences no longer wait behind each other.
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<byte[]>> inFlight =
            new java.util.concurrent.ConcurrentHashMap<>();
    // The engine is CPU bound: at most this many syntheses run at once; further misses wait briefly, then get 503.
    private final java.util.concurrent.Semaphore engineSlots;

    @org.springframework.beans.factory.annotation.Autowired
    public VoicevoxExampleSpeechService(
            @Value("${voicevox.url:http://127.0.0.1:50021}") String url,
            @Value("${voicevox.cache-dir:${user.home}/.nihongo/voicevox-cache}") String cacheDirectory) {
        this(url, cacheDirectory, 2);
    }

    public VoicevoxExampleSpeechService(String url, String cacheDirectory, int maxConcurrentSyntheses) {
        this.engine = URI.create(url.endsWith("/") ? url : url + "/");
        this.cacheDirectory = Path.of(cacheDirectory);
        this.engineSlots = new java.util.concurrent.Semaphore(Math.max(1, maxConcurrentSyntheses));
    }

    /** Spoken text of an example (ruby readings and markup removed); throws 422 when it cannot be spoken. */
    static String text(String html) {
        var document = Jsoup.parseBodyFragment(html == null ? "" : html);
        document.select("rt, rp, script, style").remove();
        String text = document.body().text().replaceAll("\\s+", " ").trim();
        if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Câu ví dụ trống hoặc quá dài để phát âm.");
        }
        return text;
    }

    /** Strong validator for the audio of this example: it changes whenever the spoken text or the voice changes. */
    public String etag(String html) {
        return "\"" + hash(SPEAKER + "\n" + text(html)) + "\"";
    }

    public byte[] speech(String html) {
        String text = text(html);
        String key = hash(SPEAKER + "\n" + text);
        Path file = cacheDirectory.resolve(key + ".wav");
        try {
            if (Files.isRegularFile(file)) return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VOICEVOX chưa sẵn sàng.", e);
        }
        var mine = new java.util.concurrent.CompletableFuture<byte[]>();
        var running = inFlight.putIfAbsent(key, mine);
        if (running != null) return await(running);
        try {
            byte[] audio = synthesizeAndStore(text, file);
            mine.complete(audio);
            return audio;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private byte[] synthesizeAndStore(String text, Path file) {
        boolean acquired = false;
        try {
            if (Files.isRegularFile(file)) return Files.readAllBytes(file);
            acquired = engineSlots.tryAcquire(20, java.util.concurrent.TimeUnit.SECONDS);
            if (!acquired) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VOICEVOX đang bận. Vui lòng thử lại.");
            byte[] audio = synthesize(text);
            Files.createDirectories(cacheDirectory);
            Path temporary = Files.createTempFile(cacheDirectory, "voicevox-", ".wav");
            try {
                Files.write(temporary, audio);
                Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return audio;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VOICEVOX chưa sẵn sàng.", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VOICEVOX chưa sẵn sàng.", e);
        } finally {
            if (acquired) engineSlots.release();
        }
    }

    private static byte[] await(java.util.concurrent.CompletableFuture<byte[]> running) {
        try {
            return running.get(70, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VOICEVOX chưa sẵn sàng.", e);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof ResponseStatusException status) throw status;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VOICEVOX chưa sẵn sàng.", e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VOICEVOX đang bận. Vui lòng thử lại.", e);
        }
    }

    protected byte[] synthesize(String text) throws IOException, InterruptedException {
        String queryPath = "audio_query?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8) + "&speaker=" + SPEAKER;
        var query = client.send(HttpRequest.newBuilder(engine.resolve(queryPath))
                .timeout(Duration.ofSeconds(20)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (query.statusCode() != 200) throw new IOException("VOICEVOX audio_query returned " + query.statusCode());
        var audio = client.send(HttpRequest.newBuilder(engine.resolve("synthesis?speaker=" + SPEAKER))
                .timeout(Duration.ofSeconds(40)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(query.body(), StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (audio.statusCode() != 200 || audio.body().length < 44
                || audio.body()[0] != 'R' || audio.body()[1] != 'I'
                || audio.body()[2] != 'F' || audio.body()[3] != 'F') {
            throw new IOException("VOICEVOX synthesis returned invalid audio (HTTP " + audio.statusCode() + ")");
        }
        return audio.body();
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

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

    public VoicevoxExampleSpeechService(
            @Value("${voicevox.url:http://127.0.0.1:50021}") String url,
            @Value("${voicevox.cache-dir:${user.home}/.nihongo/voicevox-cache}") String cacheDirectory) {
        this.engine = URI.create(url.endsWith("/") ? url : url + "/");
        this.cacheDirectory = Path.of(cacheDirectory);
    }

    public byte[] speech(String html) {
        var document = Jsoup.parseBodyFragment(html == null ? "" : html);
        document.select("rt, rp, script, style").remove();
        String text = document.body().text().replaceAll("\\s+", " ").trim();
        if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Câu ví dụ trống hoặc quá dài để phát âm.");
        }
        Path file = cacheDirectory.resolve(hash(SPEAKER + "\n" + text) + ".wav");
        try {
            if (Files.isRegularFile(file)) return Files.readAllBytes(file);
            // Serializing cache misses prevents concurrent requests for the same sentence from synthesizing it twice.
            synchronized (this) {
                if (Files.isRegularFile(file)) return Files.readAllBytes(file);
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
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VOICEVOX chưa sẵn sàng.", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VOICEVOX chưa sẵn sàng.", e);
        }
    }

    private byte[] synthesize(String text) throws IOException, InterruptedException {
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

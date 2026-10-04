package com.nihongo.staff.service.imports;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.UUID;

@Component
public class BookImportStorage {
    private final Path root;
    public BookImportStorage(@Value("${book-import.storage-directory:./.local/book-imports}") String path) { root=Path.of(path).toAbsolutePath().normalize(); }
    public Path root() throws java.io.IOException { Files.createDirectories(root); return root; }
    public Path directory(String id) {
        try { if(!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException(); }
        catch(Exception e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy lần nhập sách."); }
        var path=root.resolve(id).normalize();
        if(!path.startsWith(root)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return path;
    }
    public Path pdf(String id) { return directory(id).resolve("source.pdf"); }
    public Path image(String id,int page) { return directory(id).resolve("pages").resolve(String.format("%03d.png",page)); }
    public Path audio(String id,BookImportContent.Asset asset) {
        UUID.fromString(asset.id());
        if(!java.util.Set.of("mp3","m4a","wav","ogg").contains(asset.extension())) throw new IllegalArgumentException("Invalid audio extension");
        return directory(id).resolve("audio").resolve(asset.id()+"."+asset.extension());
    }
}

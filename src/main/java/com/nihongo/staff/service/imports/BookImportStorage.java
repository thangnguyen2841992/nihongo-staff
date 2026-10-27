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
    /**
     * Listening files of published books, kept in the staff repository (book-assets/) so every machine that pulls the
     * code can play them; the import workspace under .local stays per machine.
     */
    private final Path shared;
    public BookImportStorage(@Value("${book-import.storage-directory:./.local/book-imports}") String path,
                             @Value("${book-import.shared-directory:}") String sharedPath) {
        root=Path.of(path).toAbsolutePath().normalize();
        shared=(sharedPath==null || sharedPath.isBlank() ? staffModule(Path.of("")).resolve("book-assets") : Path.of(sharedPath)).toAbsolutePath().normalize();
    }
    /**
     * The staff service's own git repository (its folder is "staff" on one machine and "nihongo-staff" on another; the
     * service may be started from that folder or from the workspace above it).
     */
    static Path staffModule(Path workingDirectory) {
        var start=workingDirectory.toAbsolutePath().normalize();
        if(isStaffModule(start)) return start;
        try(var children=Files.list(start)) {
            return children.filter(BookImportStorage::isStaffModule).sorted().findFirst().orElse(start);
        } catch(java.io.IOException e) { return start; }
    }
    private static boolean isStaffModule(Path dir) { return Files.isDirectory(dir.resolve("src/main/java/com/nihongo/staff")); }
    public Path shared() { return shared; }
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
    /** Where an uploaded file is written; reads fall back to the shared copy when this machine has no local one. */
    public Path audio(String id,BookImportContent.Asset asset) {
        var local=localAudio(id,asset);
        if(Files.exists(local)) return local;
        var copy=sharedAudio(id,asset);
        return Files.exists(copy) ? copy : local;
    }
    public Path localAudio(String id,BookImportContent.Asset asset) {
        UUID.fromString(asset.id());
        if(!java.util.Set.of("mp3","m4a","wav","ogg").contains(asset.extension())) throw new IllegalArgumentException("Invalid audio extension");
        return directory(id).resolve("audio").resolve(asset.id()+"."+asset.extension());
    }
    public Path sharedAudio(String id,BookImportContent.Asset asset) {
        var file=localAudio(id,asset).getFileName();
        var path=shared.resolve(directory(id).getFileName()).resolve("audio").resolve(file).normalize();
        if(!path.startsWith(shared)) throw new IllegalArgumentException("Invalid audio path");
        return path;
    }
    /** Copies the listening files a published book uses into the shared folder (commit book-assets/ to share them). */
    public int share(String id,java.util.Collection<BookImportContent.Asset> assets) throws java.io.IOException {
        int copied=0;
        for(var asset:assets) {
            var local=localAudio(id,asset);var copy=sharedAudio(id,asset);
            if(!Files.exists(local) || Files.exists(copy) && Files.size(copy)==Files.size(local)) continue;
            Files.createDirectories(copy.getParent());
            Files.copy(local,copy,StandardCopyOption.REPLACE_EXISTING);copied++;
        }
        return copied;
    }
}

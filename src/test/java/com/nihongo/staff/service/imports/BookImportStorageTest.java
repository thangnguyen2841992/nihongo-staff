package com.nihongo.staff.service.imports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BookImportStorageTest {
    @TempDir Path project;

    @Test void findsTheStaffRepositoryFromTheWorkspaceOrFromItself() throws Exception {
        var module = project.resolve("nihongo-staff");
        Files.createDirectories(module.resolve("src/main/java/com/nihongo/staff"));
        Files.createDirectories(project.resolve("nihongo-user/src/main/java"));
        assertEquals(module.toAbsolutePath().normalize(), BookImportStorage.staffModule(project));
        assertEquals(module.toAbsolutePath().normalize(), BookImportStorage.staffModule(module));
    }

    @Test void playsTheSharedCopyWhenThisMachineHasNoLocalFile() throws Exception {
        var storage = new BookImportStorage(project.resolve(".local/book-imports").toString(), project.resolve("staff/book-assets").toString());
        String id = UUID.randomUUID().toString();
        var asset = new BookImportContent.Asset(UUID.randomUUID().toString(), "01 第26課 ことば.mp3", "audio/mpeg", "mp3", 3);
        assertEquals(storage.localAudio(id, asset), storage.audio(id, asset));

        Files.createDirectories(storage.localAudio(id, asset).getParent());
        Files.write(storage.localAudio(id, asset), new byte[] {1, 2, 3});
        assertEquals(1, storage.share(id, List.of(asset)));
        assertEquals(0, storage.share(id, List.of(asset)));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(storage.sharedAudio(id, asset)));

        // Another machine: same code and database, no local import workspace.
        Files.delete(storage.localAudio(id, asset));
        assertEquals(storage.sharedAudio(id, asset), storage.audio(id, asset));
    }
}

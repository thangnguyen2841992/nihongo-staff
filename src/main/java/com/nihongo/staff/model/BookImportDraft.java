package com.nihongo.staff.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "book_import_draft") @Getter @Setter
public class BookImportDraft {
    @Id @Column(length = 180) private String sourceKey;
    @Version private Long version;
    @Lob @Column(columnDefinition = "LONGTEXT", nullable = false) private String payload;
    @Column(nullable = false) private LocalDateTime reviewedAt;
}

package com.nihongo.staff.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="book_import_session") @Getter @Setter
public class BookImportSession {
    @Id @Column(length=36) private String id;
    @Version private Long version;
    @Column(length=64,nullable=false,unique=true) private String sourceHash;
    @Column(nullable=false) private String fileName;
    @Column(nullable=false) private String state;
    private int pageCount;
    private int processedPages;
    private String autoMode;
    private int aiProcessedPages;
    private boolean aiCompleted;
    @Lob @Column(columnDefinition="LONGTEXT") private String autoWarnings;
    @Lob @Column(columnDefinition="LONGTEXT") private String audioHints;
    @Column(length=500) private String message;
    @Lob @Column(columnDefinition="LONGTEXT",nullable=false) private String payload;
    private Long importedBookId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist void created() { createdAt=LocalDateTime.now(); updatedAt=createdAt; }
    @PreUpdate void updated() { updatedAt=LocalDateTime.now(); }
}

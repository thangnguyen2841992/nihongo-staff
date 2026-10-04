package com.nihongo.staff.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="book_import_page",indexes=@Index(name="idx_import_page_session",columnList="session_id")) @Getter @Setter
public class BookImportPage {
    @Id @Column(length=50) private String id;
    @Column(name="session_id",length=36,nullable=false) private String sessionId;
    private int pageNumber;
    private String method;
    @Lob @Column(columnDefinition="LONGTEXT") private String text;
}

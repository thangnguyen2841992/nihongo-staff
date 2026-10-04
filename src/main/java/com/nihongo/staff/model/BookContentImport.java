package com.nihongo.staff.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One committed import per source; the row and all content share one transaction. */
@Entity
@Table(name = "book_content_import")
@Getter @Setter @NoArgsConstructor
public class BookContentImport {
    @Id
    @Column(length = 180)
    private String sourceKey;
    @Version
    private Long version;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Books book;
}

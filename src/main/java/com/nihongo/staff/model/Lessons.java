package com.nihongo.staff.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
public class Lessons {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lessonId;

    private String name;

    private String description;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String reading;

    @Column(length = 2)
    private String audioTrack;
    @Column(length = 36) private String audioImportId;
    @Column(length = 36) private String audioAssetId;

    @CreationTimestamp
    private LocalDateTime dateCreated;

    @UpdateTimestamp
    private LocalDateTime dateModified;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id",  nullable = false)
    private Books book;


}

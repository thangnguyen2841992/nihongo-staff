package com.nihongo.staff.model;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name="content_revision",indexes=@Index(name="idx_revision_resource",columnList="kind,resourceId,createdAt"))
public class ContentRevision {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=16) private String kind;
 @Column(nullable=false) private Long resourceId;
 @Column(nullable=false) private Long bookId;
 @Column(nullable=false,length=36) private String actor;
 @Column(nullable=false,length=20) private String action;
 @Lob @Column(nullable=false,columnDefinition="LONGTEXT") private String snapshot;
 @Column(nullable=false) private LocalDateTime createdAt;
}

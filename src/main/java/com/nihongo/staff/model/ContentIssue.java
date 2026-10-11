package com.nihongo.staff.model;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name="content_issue",indexes={@Index(name="idx_issue_status",columnList="status,createdAt"),@Index(name="idx_issue_reporter",columnList="reporter,createdAt")})
public class ContentIssue {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=36) private String reporter;
 @Column(nullable=false) private Long lessonId;
 private Long exerciseId;
 @Column(nullable=false,length=20) private String category;
 @Column(nullable=false,length=2000) private String detail;
 @Column(nullable=false,length=20) private String status="OPEN";
 @Column(length=36) private String assignee;
 @Column(length=2000) private String resolution;
 @Column(nullable=false) private LocalDateTime createdAt;
 private LocalDateTime updatedAt;
 @Version private long version;
}

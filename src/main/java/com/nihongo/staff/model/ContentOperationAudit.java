package com.nihongo.staff.model;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name="content_operation_audit",indexes=@Index(name="idx_content_audit_time",columnList="createdAt"))
public class ContentOperationAudit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=36) private String actor;
 @Column(nullable=false,length=30) private String action;
 @Column(nullable=false,length=100) private String target;
 @Column(nullable=false,length=3000) private String detail;
 @Column(nullable=false) private LocalDateTime createdAt;
}

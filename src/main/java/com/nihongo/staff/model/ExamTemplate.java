package com.nihongo.staff.model;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name="exam_template",indexes=@Index(name="idx_exam_created",columnList="createdAt"))
public class ExamTemplate {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=200) private String title;
 @Column(nullable=false,length=36) private String owner;
 @Lob @Column(nullable=false,columnDefinition="LONGTEXT") private String snapshot;
 @Column(nullable=false) private LocalDateTime createdAt;
}

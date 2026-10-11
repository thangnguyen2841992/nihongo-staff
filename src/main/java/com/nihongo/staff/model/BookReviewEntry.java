package com.nihongo.staff.model;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name="book_review_entry",indexes=@Index(name="idx_review_book",columnList="bookId,createdAt"))
public class BookReviewEntry {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long bookId;
 @Column(nullable=false,length=36) private String actor;
 @Column(nullable=false,length=24) private String action;
 @Column(length=2000) private String note;
 @Column(length=36) private String assignee;
 @Column(nullable=false) private LocalDateTime createdAt;
}

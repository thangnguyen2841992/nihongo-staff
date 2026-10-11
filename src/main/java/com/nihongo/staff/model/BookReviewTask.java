package com.nihongo.staff.model;
import jakarta.persistence.*;import lombok.*;
@Entity @Getter @Setter @Table(name="book_review_task")
public class BookReviewTask {
 @Id private Long bookId;
 @Column(length=36) private String assignee;
 @Version private long version;
}

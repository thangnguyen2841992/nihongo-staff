package com.nihongo.staff.model;
import jakarta.persistence.*;import lombok.*;
@Entity @Getter @Setter @Table(name="question_metadata")
public class QuestionMetadata {
 @Id private Long exerciseId;
 @Column(nullable=false,length=10) private String difficulty="NORMAL";
 @Version private long version;
}

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
@Builder
public class ExersiceKeyword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long exerciseKeywordId;


    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String contentNihongo;

    private String answerA;
    private String answerB;
    private String answerC;
    private String answerD;

    private String correctAnswer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lessons_id", nullable = false)
    private Lessons lessons;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exerciseType_id", nullable = false)
    private ExerciseType exerciseType;


    @CreationTimestamp
    private LocalDateTime dateCreated;

    @UpdateTimestamp
    private LocalDateTime dateModified;
}

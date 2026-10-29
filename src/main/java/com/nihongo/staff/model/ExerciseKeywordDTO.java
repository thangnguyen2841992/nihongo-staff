package com.nihongo.staff.model;

import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ExerciseKeywordDTO {
    private Long exerciseKeywordId;


    private String contentNihongo;

    private String answerA;
    private String answerB;
    private String answerC;
    private String answerD;

    private String correctAnswer;

    private Long lessonId;

    private Long exerciseTypeId;
    private String exerciseTypeName;
    private String audioUrl;
    /** Label of the shared lesson track the audio comes from (e.g. "練習 B3 · Luyện tập B3"), if any. */
    private String audioLabel;
    private com.nihongo.staff.service.BookAiSolutionService.Solution aiSolution;
}


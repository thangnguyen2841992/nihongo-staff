package com.nihongo.staff.model;

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
}


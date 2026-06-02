package com.nihongo.staff.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GrammarResponse {
    private Long grammarId;

    private String title;

    private Long lessonId;

    private String description;

    private String imageUrl;
}

package com.nihongo.staff.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class GrammarRequest {
    private Long grammarId;

    private String title;

    private String structure;

    private Long lessonId;

    private String description;
}

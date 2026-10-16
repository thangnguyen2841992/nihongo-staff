package com.nihongo.staff.model.dto;

import java.util.List;

public record LessonGrammarContentResponse(
        List<GrammarResponse> grammars,
        List<ExampleResponse> examples
) {}

package com.nihongo.staff.service.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;

@Component
public class TryN3ChapterData {
    private final Chapter chapter;

    public TryN3ChapterData(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("imports/try-n3/chapter-1.json").getInputStream()) {
            chapter = mapper.readValue(input, Chapter.class);
        }
        var grammars = chapter.lessons().stream().flatMap(l -> l.grammars().stream()).toList();
        var exercises = chapter.lessons().stream().flatMap(l -> l.exercises().stream()).toList();
        if (chapter.lessons().size() != 2 || grammars.size() != 10 || exercises.size() != 18
                || !grammars.stream().map(GrammarData::number).toList().equals(java.util.stream.IntStream.rangeClosed(1, 10).boxed().toList())
                || exercises.stream().anyMatch(e -> e.correctAnswer() == null || !e.correctAnswer().matches("[ABCD]"))) {
            throw new IOException("Dữ liệu mẫu TRY N3 không hợp lệ");
        }
    }

    public Chapter chapter() { return chapter; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chapter(String sourceKey, String bookName, String levelName, String typeName,
                          String description, String sourceFile, List<Integer> sourcePdfPages,
                          List<String> notes, String exerciseTypeName, List<LessonData> lessons) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LessonData(String name, String description, String reading, List<Integer> sourcePrintedPages,
                             List<GrammarData> grammars, List<ExerciseData> exercises, String audioTrack) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GrammarData(int number, String title, int sourcePrintedPage, String description, List<ExampleData> examples) {}
    public record ExampleData(String nihongo, String vietnamese) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExerciseData(String sourceKey, int sourceQuestionNumber, String contentNihongo,
                               String answerA, String answerB, String answerC, String answerD, String correctAnswer,
                               String exerciseTypeName) {}
}

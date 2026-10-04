package com.nihongo.staff.service.imports;
import java.util.List;

/** Editable content is separate from extraction. Publication requires an explicit content/key review. */
public record BookImportContent(String bookName, Long levelId, Long typeId, String description,
                                List<Asset> audio, List<Lesson> lessons) {
    public record Asset(String id, String name, String mediaType, String extension, long bytes) {}
    public record Lesson(String name, String description, int firstPage, int lastPage, String reading,
                         String audioId, List<Grammar> grammars, List<Question> exercises) {}
    public record Grammar(String title, String description, List<Example> examples) {}
    public record Example(String nihongo, String vietnamese) {}
    public record Question(String groupName, String contentNihongo, String answerA, String answerB,
                           String answerC, String answerD, String correctAnswer, String audioId) {}
}

package com.nihongo.staff.service.imports;

import com.nihongo.staff.model.*;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.security.ContentHtml;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class TryN3ImportService {
    private final TryN3ChapterData data;
    private final BookContentImportRepository imports;
    private final IBookRepository books;
    private final ILessonsRepository lessons;
    private final IGrammarRepository grammars;
    private final IExampleRepository examples;
    private final IExerciseKeywordRepository exercises;
    private final IExerciseTypeRepository exerciseTypes;
    private final ILevelsRepository levels;
    private final ITypeRepository types;

    @Transactional(readOnly = true)
    public Preview preview() {
        var chapter = data.chapter();
        Long bookId = imports.findById(chapter.sourceKey()).map(i -> i.getBook().getBookId()).orElse(null);
        return new Preview(chapter, bookId);
    }

    @Transactional
    @CacheEvict(value = {"books", "exerciseTypes"}, allEntries = true)
    public Result importChapter() {
        var chapter = data.chapter();
        var existing = imports.findById(chapter.sourceKey());
        if (existing.isPresent()) return result(existing.get().getBook(), true);

        var level = levels.findByLevelName(chapter.levelName()).orElseThrow(() -> missing("trình độ N3"));
        var type = types.findByTypeName(chapter.typeName()).orElseThrow(() -> missing("loại sách Ngữ Pháp"));
        // Flush the unique source key before writing any content. Concurrent imports cannot create duplicates.
        var marker = new BookContentImport();
        marker.setSourceKey(chapter.sourceKey());
        imports.saveAndFlush(marker);
        var book = new Books();
        book.setPublicationStatus(PublicationStatus.DRAFT);
        book.setBookName(chapter.bookName()); book.setDescription(chapter.description());
        book.setLevel(level); book.setTypes(type);
        books.save(book);
        appendChapter(book, chapter);
        marker.setBook(book);
        imports.save(marker);
        return result(book, false);
    }

    // Called inside the book import's transaction as well as the original trial import.
    public void appendChapter(Books book, TryN3ChapterData.Chapter chapter) {
        var exerciseType = exerciseTypes.findByName(chapter.exerciseTypeName()).orElseGet(() -> {
            var value = new ExerciseType(); value.setName(chapter.exerciseTypeName());
            return exerciseTypes.save(value);
        });
        for (var input : chapter.lessons()) {
            var lesson = new Lessons();
            lesson.setBook(book); lesson.setName(input.name()); lesson.setDescription(input.description());
            lesson.setReading(ContentHtml.clean(input.reading()));
            lesson.setAudioTrack(input.audioTrack());
            lessons.save(lesson);
            for (var item : input.grammars()) {
                var grammar = new Grammar();
                grammar.setLessons(lesson); grammar.setTitle(item.title());
                grammar.setDescription(ContentHtml.clean(item.description()));
                grammars.save(grammar);
                for (var sentence : item.examples()) {
                    var example = new Example();
                    example.setGrammar(grammar); example.setNihongo(ContentHtml.clean(sentence.nihongo()));
                    example.setVietnamese(ContentHtml.clean(sentence.vietnamese()));
                    examples.save(example);
                }
            }
            for (var question : input.exercises()) {
                var questionType = question.exerciseTypeName() == null ? exerciseType :
                        exerciseTypes.findByName(question.exerciseTypeName()).orElseGet(() -> {
                            var value = new ExerciseType(); value.setName(question.exerciseTypeName());
                            return exerciseTypes.save(value);
                        });
                var exercise = new ExersiceKeyword();
                exercise.setLessons(lesson); exercise.setExerciseType(questionType);
                exercise.setContentNihongo(ContentHtml.clean(question.contentNihongo()));
                exercise.setAnswerA(question.answerA()); exercise.setAnswerB(question.answerB());
                exercise.setAnswerC(question.answerC()); exercise.setAnswerD(question.answerD());
                exercise.setCorrectAnswer(question.correctAnswer());
                exercises.save(exercise);
            }
        }
    }

    private Result result(Books book, boolean alreadyImported) {
        var chapter = data.chapter();
        int grammarCount = chapter.lessons().stream().mapToInt(l -> l.grammars().size()).sum();
        int exampleCount = chapter.lessons().stream().flatMap(l -> l.grammars().stream()).mapToInt(g -> g.examples().size()).sum();
        int exerciseCount = chapter.lessons().stream().mapToInt(l -> l.exercises().size()).sum();
        return new Result(book.getBookId(), book.getBookName(), alreadyImported, chapter.lessons().size(), grammarCount, exampleCount, exerciseCount);
    }

    private ResponseStatusException missing(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Chưa có " + name + ". Vui lòng khởi tạo danh mục trước.");
    }
    public record Preview(TryN3ChapterData.Chapter chapter, Long importedBookId) {}
    public record Result(Long bookId, String bookName, boolean alreadyImported, int lessonCount, int grammarCount, int exampleCount, int exerciseCount) {}
}

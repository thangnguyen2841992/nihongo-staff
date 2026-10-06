package com.nihongo.staff;

import com.nihongo.staff.model.*;
import com.nihongo.staff.repository.IExampleRepository;
import com.nihongo.staff.repository.IBookRepository;
import com.nihongo.staff.repository.ILessonsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:lesson_examples;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class LessonExamplesRepositoryTest {
    @Autowired TestEntityManager entityManager;
    @Autowired IExampleRepository examples;
    @Autowired IBookRepository books;
    @Autowired ILessonsRepository lessons;

    @Test void loadsLearningLocationsWithoutLessonContent() {
        var level = new Levels(); level.setLevelName("N3"); entityManager.persist(level);
        var type = new Types(); type.setTypeName("Course"); entityManager.persist(type);
        var book = new Books(); book.setBookName("TRY N3"); book.setLevel(level); book.setTypes(type); entityManager.persist(book);
        var lesson = lesson(book, "First"); lesson.setReading("long lesson content");
        entityManager.flush();
        entityManager.clear();

        var bookLocation = books.findLocationById(book.getBookId()).orElseThrow();
        var lessonLocation = lessons.findLocationById(lesson.getLessonId()).orElseThrow();
        assertEquals(book.getBookId(), bookLocation.bookId());
        assertEquals(level.getLevelId(), bookLocation.levelId());
        assertEquals(book.getBookId(), lessonLocation.bookId());
        assertEquals(level.getLevelId(), lessonLocation.levelId());
        assertEquals("First", lessonLocation.name());
    }

    @Test void loadsOnlyExamplesBelongingToTheRequestedLesson() {
        var level = new Levels(); level.setLevelName("N3"); entityManager.persist(level);
        var type = new Types(); type.setTypeName("Course"); entityManager.persist(type);
        var book = new Books(); book.setBookName("TRY N3"); book.setLevel(level); book.setTypes(type); entityManager.persist(book);
        var firstLesson = lesson(book, "First");
        var secondLesson = lesson(book, "Second");
        var firstGrammar = grammar(firstLesson);
        var secondGrammar = grammar(secondLesson);
        example(firstGrammar, "一番");
        example(firstGrammar, "二番");
        example(secondGrammar, "別の課");
        entityManager.flush();
        entityManager.clear();

        var rows = examples.findByLessonIdWithGrammar(firstLesson.getLessonId());
        assertEquals(2, rows.size());
        assertEquals("一番", rows.get(0).getNihongo());
        assertEquals(firstGrammar.getGrammarId(), rows.get(0).getGrammar().getGrammarId());
        assertEquals("二番", rows.get(1).getNihongo());
    }

    private Lessons lesson(Books book, String name) {
        var lesson = new Lessons(); lesson.setBook(book); lesson.setName(name);
        return entityManager.persist(lesson);
    }

    private Grammar grammar(Lessons lesson) {
        var grammar = new Grammar(); grammar.setLessons(lesson); grammar.setTitle("文法");
        return entityManager.persist(grammar);
    }

    private void example(Grammar grammar, String text) {
        var example = new Example(); example.setGrammar(grammar); example.setNihongo(text);
        entityManager.persist(example);
    }
}

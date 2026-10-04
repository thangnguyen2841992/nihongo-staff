package com.nihongo.staff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.imports.TryN3ImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:try_n3_import;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "monitoring.collection.enabled=false",
        "monitoring.prometheus-targets-file=target/try-n3-test/node_targets.json",
        "monitoring.prometheus-windows-targets-file=target/try-n3-test/windows_targets.json",
        "jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA="
})
@AutoConfigureMockMvc
class TryN3ImportIntegrationTest {
    static final String PATH = "/api/staff/imports/try-n3/chapter-1";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TryN3ImportService service;
    @Autowired com.nihongo.staff.service.imports.TryN3BookImportService bookImport;
    @Autowired com.nihongo.staff.service.imports.TryN3BookData bookData;
    @Autowired com.nihongo.staff.service.imports.TryN3ChapterData trialData;
    @Autowired BookImportDraftRepository drafts;
    @Autowired BookContentImportRepository imports;
    @Autowired IBookRepository books;
    @Autowired ILessonsRepository lessons;
    @Autowired IGrammarRepository grammars;
    @Autowired IExampleRepository examples;
    @Autowired IExerciseKeywordRepository exercises;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void clearContent() {
        drafts.deleteAll(); imports.deleteAll(); exercises.deleteAll(); examples.deleteAll(); grammars.deleteAll(); lessons.deleteAll(); books.deleteAll();
    }
    String bearer(String role) throws Exception {
        var jwt = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
                new com.nimbusds.jwt.JWTClaimsSet.Builder().subject("test").claim("roles", List.of(role))
                        .expirationTime(Date.from(java.time.Instant.now().plusSeconds(300))).build());
        jwt.sign(new com.nimbusds.jose.crypto.MACSigner(Base64.getDecoder().decode("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA=")));
        return "Bearer " + jwt.serialize();
    }

    @Test void importsOnceAndExistingGraderAcceptsOriginalAnswerKey() throws Exception {
        mvc.perform(get(PATH).header("Authorization", bearer("STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.chapter.lessons.length()").value(2));
        var first = mvc.perform(post(PATH).header("Authorization", bearer("STAFF")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.alreadyImported").value(false))
                .andExpect(jsonPath("$.exampleCount").value(37)).andReturn();
        long bookId = mapper.readTree(first.getResponse().getContentAsString()).path("bookId").asLong();
        mvc.perform(post(PATH).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookId").value(bookId))
                .andExpect(jsonPath("$.alreadyImported").value(true));
        assertEquals(1, books.count()); assertEquals(1, imports.count()); assertEquals(2, lessons.count());
        assertEquals(10, grammars.count()); assertEquals(37, examples.count()); assertEquals(18, exercises.count());
        assertTrue(examples.findAll().stream().allMatch(e -> e.getVietnamese() != null && !e.getVietnamese().isBlank()));
        var questions = exercises.findAll().stream().sorted(Comparator.comparing(q -> q.getExerciseKeywordId())).toList();
        assertEquals(List.of("C", "A", "D", "B", "C", "A", "B", "A", "B", "A", "A", "A", "C", "C", "A", "A", "D", "A"), questions.stream().map(q -> q.getCorrectAnswer()).toList());
        assertEquals("", questions.get(17).getAnswerD());
        var answers = new LinkedHashMap<Long,String>();
        questions.forEach(q -> answers.put(q.getExerciseKeywordId(), q.getCorrectAnswer()));
        long lessonId = questions.get(0).getLessons().getLessonId();
        mvc.perform(post("/api/staff/lessons/" + lessonId + "/grade").header("Authorization", bearer("STAFF"))
                .contentType("application/json").content(mapper.writeValueAsString(answers)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correctCount").value(18));
        mvc.perform(get(PATH).header("Authorization", bearer("STAFF")))
                .andExpect(jsonPath("$.importedBookId").value(bookId));
    }

    @Test void rejectsAnonymousAndUserImportsAndRestrictsSourcePages() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        for (String path : List.of(PATH, PATH + "/pages/15")) {
            mvc.perform(get(path).header("Authorization", bearer("USER"))).andExpect(status().isForbidden());
        }
        mvc.perform(post(PATH).header("Authorization", bearer("USER"))).andExpect(status().isForbidden());
        mvc.perform(get(PATH + "/pages/15").header("Authorization", bearer("STAFF")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get(PATH + "/pages/14").header("Authorization", bearer("STAFF"))).andExpect(status().isNotFound());
        assertEquals(0, books.count());
    }

    @Test void rollsBackEntireChapterAndAllowsRetryAfterFailure() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            service.importChapter();
            throw new IllegalStateException("Simulated failure before commit");
        }));
        assertEquals(0, imports.count()); assertEquals(0, books.count()); assertEquals(0, lessons.count());
        assertEquals(0, grammars.count()); assertEquals(0, examples.count()); assertEquals(0, exercises.count());
        var result = service.importChapter();
        assertFalse(result.alreadyImported()); assertEquals(18, exercises.count());
    }

    // Deliberately synthetic chapter 2: checks transactions and schema, never used as book content.
    com.nihongo.staff.service.imports.TryN3ChapterData.Chapter chapterTwoFixture() {
        var c = trialData.chapter(); var index = bookData.chapter(2);
        var parts = new ArrayList<com.nihongo.staff.service.imports.TryN3ChapterData.LessonData>();
        for (int i = 0; i < 2; i++) {
            var original = c.lessons().get(i);
            var grammar = original.grammars().stream().map(g -> new com.nihongo.staff.service.imports.TryN3ChapterData.GrammarData(
                    g.number() + 10, g.title(), 31, g.description(), g.examples())).toList();
            parts.add(new com.nihongo.staff.service.imports.TryN3ChapterData.LessonData(index.lessonNames().get(i),
                    original.description(), original.reading(), List.of(31, 36), grammar, original.exercises(), i == 0 ? "07" : "08"));
        }
        return new com.nihongo.staff.service.imports.TryN3ChapterData.Chapter(bookImport.chapterKey(2), "TEST FIXTURE", "N3", "Ngữ Pháp",
                c.description(), c.sourceFile(), index.sourcePdfPages(), c.notes(), c.exerciseTypeName(), parts);
    }

    @Test void bookImportRequiresReviewedCompleteContentAndResumesWithoutDuplicatingTrial() throws Exception {
        String bookPath = "/api/staff/imports/try-n3/book";
        mvc.perform(get(bookPath).header("Authorization", bearer("USER"))).andExpect(status().isForbidden());
        mvc.perform(get(bookPath)).andExpect(status().isUnauthorized());
        mvc.perform(post(bookPath + "/chapters/3/import").header("Authorization", bearer("STAFF")))
                .andExpect(status().isConflict());
        assertEquals(0, imports.count()); assertEquals(0, books.count());
        var first = service.importChapter();
        var edited = lessons.findAll().get(0); edited.setReading("<p>教師の修正</p>"); lessons.save(edited);
        var fixture = chapterTwoFixture();
        bookImport.review(2, new com.nihongo.staff.service.imports.TryN3BookImportService.Review(true, fixture));
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            try { bookImport.importChapter(2); } catch (Exception e) { throw new IllegalStateException(e); }
            throw new IllegalStateException("Failure before commit");
        }));
        assertEquals(2, lessons.count()); assertEquals(10, grammars.count());
        var progress = bookImport.importChapter(2);
        assertEquals(first.bookId(), progress.bookId()); assertEquals(1, books.count());
        assertEquals(4, lessons.count()); assertEquals(20, grammars.count()); assertEquals(74, examples.count());
        assertFalse(progress.complete()); assertEquals(2, progress.chapters().stream().filter(s -> s.imported()).count());
        assertEquals("<p>教師の修正</p>", lessons.findById(edited.getLessonId()).orElseThrow().getReading());
        assertEquals("07", lessons.findAll().stream().filter(l -> l.getName().equals(bookData.chapter(2).lessonNames().get(0))).findFirst().orElseThrow().getAudioTrack());
        bookImport.importChapter(2);
        assertEquals(4, lessons.count()); assertEquals(36, exercises.count());
        mvc.perform(get(bookPath + "/pages/30").header("Authorization", bearer("STAFF")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get(bookPath + "/pages/209").header("Authorization", bearer("STAFF"))).andExpect(status().isOk());
        mvc.perform(get(bookPath + "/pages/210").header("Authorization", bearer("STAFF"))).andExpect(status().isNotFound());
    }

    @Test void bookImportRejectsMissingTranslationsInvalidTracksAndEmptyCorrectChoice() throws Exception {
        var source = mapper.valueToTree(chapterTwoFixture());
        ((com.fasterxml.jackson.databind.node.ObjectNode) source.path("lessons").get(0)).put("audioTrack", "65");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> bookImport.validate(2,
                mapper.treeToValue(source, com.nihongo.staff.service.imports.TryN3ChapterData.Chapter.class)));
        var missingTranslation = mapper.valueToTree(chapterTwoFixture());
        ((com.fasterxml.jackson.databind.node.ObjectNode) missingTranslation.path("lessons").get(0).path("grammars").get(0).path("examples").get(0)).put("vietnamese", "");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> bookImport.validate(2,
                mapper.treeToValue(missingTranslation, com.nihongo.staff.service.imports.TryN3ChapterData.Chapter.class)));
        var emptyCorrect = mapper.valueToTree(chapterTwoFixture());
        ((com.fasterxml.jackson.databind.node.ObjectNode) emptyCorrect.path("lessons").get(1).path("exercises").get(0)).put("answerC", "");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> bookImport.validate(2,
                mapper.treeToValue(emptyCorrect, com.nihongo.staff.service.imports.TryN3ChapterData.Chapter.class)));
        assertEquals(0, drafts.count()); assertEquals(0, imports.count());
    }

    @Test void fullReviewedBookKeepsAllKeysAndResumesWithoutDuplicates() throws Exception {
        var keys = List.of("CADBCABABAAACCAADA", "BBACBDACCBCDBCABBD", "BCAADBDBBBADABCCDCB",
                "DBBACABABDCABAC", "ABDBAAACBCAAABADCABCAC", "ACBABAACBCCBABDCBCBAAC",
                "BADAACBCBBADCACAA", "DABADABCCABC", "DCABCABCAAACBADABCC",
                "DABCABAAABCDDACAAAAC", "DCBADDBABCBCAABC");
        Long bookId = null;
        for (int n = 1; n <= 11; n++) {
            var progress = bookImport.importChapter(n);
            if (bookId == null) bookId = progress.bookId();
            assertEquals(bookId, progress.bookId());
            assertEquals(n == 11, progress.complete());
            var names = bookData.chapter(n).lessonNames();
            var lastPart = lessons.findAll().stream().filter(l -> l.getName().equals(names.get(names.size() - 1))).findFirst().orElseThrow();
            var questions = new TransactionTemplate(transactions).execute(s -> exercises.findAll().stream()
                    .filter(q -> q.getLessons().getLessonId().equals(lastPart.getLessonId()))
                    .sorted(Comparator.comparing(q -> q.getExerciseKeywordId())).toList());
            assertEquals(keys.get(n - 1), questions.stream().map(q -> q.getCorrectAnswer()).collect(java.util.stream.Collectors.joining()));
            var answers = new LinkedHashMap<Long,String>(); questions.forEach(q -> answers.put(q.getExerciseKeywordId(), q.getCorrectAnswer()));
            mvc.perform(post("/api/staff/lessons/" + lastPart.getLessonId() + "/grade").header("Authorization", bearer("STAFF"))
                    .contentType("application/json").content(mapper.writeValueAsString(answers)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.correctCount").value(keys.get(n - 1).length()));
        }
        assertEquals(1, books.count()); assertEquals(21, lessons.count()); assertEquals(113, grammars.count());
        assertEquals(463, examples.count()); assertEquals(198, exercises.count());
        assertEquals(bookData.book().bookName(), books.findById(bookId).orElseThrow().getBookName());
        assertTrue(lessons.findAll().stream().allMatch(l -> l.getAudioTrack() != null));
        for (int n = 1; n <= 11; n++) bookImport.importChapter(n);
        assertEquals(21, lessons.count()); assertEquals(198, exercises.count()); assertEquals(463, examples.count());
    }

    @Test void reviewedChapterTwoKeepsOriginalKeyAndPersistsReadingTracks() throws Exception {
        var first = service.importChapter();
        bookImport.importChapter(2);
        assertEquals(1, books.count()); assertEquals(4, lessons.count());
        assertEquals(20, grammars.count()); assertEquals(73, examples.count()); assertEquals(36, exercises.count());
        var secondLesson = lessons.findAll().stream().filter(l -> l.getName().startsWith("04.")).findFirst().orElseThrow();
        var questions = new TransactionTemplate(transactions).execute(s -> exercises.findAll().stream()
                .filter(q -> q.getLessons().getLessonId().equals(secondLesson.getLessonId()))
                .sorted(Comparator.comparing(q -> q.getExerciseKeywordId())).toList());
        assertEquals("BBACBDACCBCDBCABBD", questions.stream().map(q -> q.getCorrectAnswer()).collect(java.util.stream.Collectors.joining()));
        var answers = new LinkedHashMap<Long,String>(); questions.forEach(q -> answers.put(q.getExerciseKeywordId(), q.getCorrectAnswer()));
        mvc.perform(post("/api/staff/lessons/" + secondLesson.getLessonId() + "/grade").header("Authorization", bearer("STAFF"))
                .contentType("application/json").content(mapper.writeValueAsString(answers)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correctCount").value(18));
        mvc.perform(get("/api/staff/lessons/" + secondLesson.getLessonId()).header("Authorization", bearer("STAFF")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.audioTrack").value("08"));
        assertEquals(first.bookId(), bookImport.preview().bookId());
    }
}

package com.nihongo.staff.controller;

import com.nihongo.staff.model.ExerciseKeywordDTO;
import com.nihongo.staff.model.ExerciseType;
import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
import com.nihongo.staff.model.dto.*;
import com.nihongo.staff.service.IStaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffRestController {
    private final IStaffService staffService;
    private final com.nihongo.staff.service.ExerciseGradingService grading;
    private final com.nihongo.staff.service.BookAiSolutionService aiSolutions;
    private final com.nihongo.staff.security.ContentAccess contentAccess;
    @PreAuthorize("@contentAccess.lesson(#id,authentication)")
    @PostMapping("/lessons/{id}/grade")
    public com.nihongo.staff.service.ExerciseGradingService.Grade grade(@PathVariable Long id,
            @RequestBody java.util.Map<Long,String> answers) { return grading.grade(id,answers); }



    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @GetMapping("/types")
    public ResponseEntity<List<Types>> getAllTypes() {
        return ResponseEntity.ok(this.staffService.getTypes());
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @GetMapping("/levels")
    public ResponseEntity<List<Levels>> getAllLevels() {
        return ResponseEntity.ok(this.staffService.getLevels());
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF','USER')")
    @GetMapping("/exerciseTypes")
    public ResponseEntity<List<ExerciseType>> getAllExerciseTypes() {
        return ResponseEntity.ok(this.staffService.getExerciseTypes());
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @GetMapping("/books")
    public ResponseEntity<List<BookResponse>> getAllBooks() {
        return ResponseEntity.ok(this.staffService.getBooks());
    }

    @PreAuthorize("@contentAccess.book(#bookId,authentication)")
    @GetMapping("/books/{bookId}")
    public ResponseEntity<BookResponse> getBookDetail(@PathVariable Long bookId) {
        return ResponseEntity.ok(this.staffService.getBookDetail(bookId));
    }


    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @GetMapping("/getBooksByLevelAndType")
    public ResponseEntity<List<BookResponse>> getBooksByLevelAndType(@RequestParam Long levelId, @RequestParam Long typeId) {
        return ResponseEntity.ok(this.staffService.getBooksByLevelAndType(levelId, typeId));
    }

    @PreAuthorize("@contentAccess.level(#levelId,authentication)")
    @GetMapping("/getBooksByLevel")
    public ResponseEntity<List<BookResponse>> getBooksByLevel(@RequestParam("levelId") Long levelId) {
        return ResponseEntity.ok(this.staffService.getBooksByLevel(levelId));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/books")
    public ResponseEntity<BookResponse> createNewBook(@RequestBody CreateNewBookRequest bookRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.staffService.createNewBook(bookRequest));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PutMapping("/books")
    public ResponseEntity<BookResponse> updateBook(@RequestBody UpdateBookRequest bookRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.staffService.updateBook(bookRequest));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/images")
    public ResponseEntity<List<ImageDTO>> updateImagesOfBook(@RequestBody UpdateImageOfBookRequest imageOfBookRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.staffService.updateImagesOfBooks(imageOfBookRequest));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/lessons")
    public ResponseEntity<LessonResponse> createNewLesson(@RequestBody CreateNewLessonRequest lessonRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.staffService.createNewLesson(lessonRequest));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PutMapping("/lessons")
    public ResponseEntity<LessonResponse> updateLesson(@RequestBody CreateNewLessonRequest lessonRequest) {
        return ResponseEntity.status(HttpStatus.OK).body(this.staffService.updateLesson(lessonRequest));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @DeleteMapping("/lessons")
    public ResponseEntity<?> deleteLesson(@RequestBody CreateNewLessonRequest lessonRequest) {
        this.staffService.deleteLesson(lessonRequest.getLessonId());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PreAuthorize("@contentAccess.lesson(#id,authentication)")
    @GetMapping("/lessons/{id}")
    public ResponseEntity<LessonResponse> getLessonById(@PathVariable Long id) {
        return ResponseEntity.ok(this.staffService.getLessonByIdAPI(id));
    }

    @PreAuthorize("@contentAccess.book(#bookId,authentication)")
    @GetMapping("/getLessonsByBook")
    public ResponseEntity<List<LessonResponse>> getLessonsByBook(@RequestParam Long bookId) {
        return ResponseEntity.ok(this.staffService.getAllLessonByBook(bookId));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/grammars")
    public ResponseEntity<GrammarResponse> createNewGrammar(@RequestBody GrammarRequest grammarRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.staffService.createNewGrammar(grammarRequest));
    }

    @PreAuthorize("@contentAccess.lesson(#lessonId,authentication)")
    @GetMapping("/getAllGrammarByLesson")
    public ResponseEntity<List<GrammarResponse>> getAllGrammarByLesson(@RequestParam Long lessonId) {
        return ResponseEntity.ok(this.staffService.getAllGrammarByLesson(lessonId));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PutMapping("/grammars")
    public ResponseEntity<GrammarResponse> updateGrammar(@RequestBody GrammarRequest grammarRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.staffService.updateGrammar(grammarRequest));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @DeleteMapping("/grammars")
    public ResponseEntity<?> deleteGrammar(@RequestBody GrammarRequest grammarRequest) {
        this.staffService.deleteGrammar(grammarRequest.getGrammarId());
        return ResponseEntity.status(HttpStatus.OK).body("Delete successfully");
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/examples")
    public ResponseEntity<ExampleResponse> createNewExample(@RequestBody ExampleRequest exampleRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.staffService.createNewExample(exampleRequest));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PutMapping("/examples")
    public ResponseEntity<ExampleResponse> updateExample(@RequestBody ExampleRequest exampleRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.staffService.updateExample(exampleRequest));
    }

    @PreAuthorize("@contentAccess.grammar(#grammarId,authentication)")
    @GetMapping("/getAllExampleByGrammar")
    public ResponseEntity<List<ExampleResponse>> getAllExampleByGrammar(@RequestParam Long grammarId) {
        return ResponseEntity.ok(this.staffService.findAllExampleOfGrammar(grammarId));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/exercises")
    public ResponseEntity<ExerciseKeywordDTO> createNewExercise(@RequestBody ExerciseKeywordDTO exerciseKeywordDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.staffService.createNewExcercise(exerciseKeywordDTO));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PutMapping("/exercises")
    public ResponseEntity<ExerciseKeywordDTO> updateExercise(@RequestBody ExerciseKeywordDTO exerciseKeywordDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.staffService.updateExcercise(exerciseKeywordDTO));
    }

    @PreAuthorize("@contentAccess.lesson(#lessonId,authentication)")
    @GetMapping("/getAllExcercisesKeywordOfLesson/{lessonId}")
    public ResponseEntity<List<ExerciseKeywordDTO>> getAllExercisesKeywordOfLesson(@PathVariable Long lessonId, org.springframework.security.core.Authentication authentication) {
        var rows = this.staffService.getAllExcercisesKeywordOfLesson(lessonId);
        aiSolutions.attachForReading(rows, contentAccess.manager(authentication));
        if (!contentAccess.manager(authentication)) rows.forEach(row -> row.setCorrectAnswer(null));
        return ResponseEntity.ok(rows);
    }

}

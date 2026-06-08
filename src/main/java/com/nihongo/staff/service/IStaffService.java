package com.nihongo.staff.service;

import com.nihongo.staff.model.*;
import com.nihongo.staff.model.dto.*;

import java.util.List;

public interface IStaffService {
    BookResponse createNewBook(CreateNewBookRequest newBookRequest);
    BookResponse updateBook(UpdateBookRequest bookRequest);
    BookResponse   getBookDetail(Long bookId);
    List<Types> getTypes();
    List<Levels> getLevels();
    List<ExerciseType> getExerciseTypes();
    List<BookResponse> getBooks();
    List<BookResponse> getBooksByLevelAndType(Long levelId, Long typeId);
    List<LessonResponse> getAllLessonByBook(Long bookId);
    LessonResponse createNewLesson(CreateNewLessonRequest request);
    LessonResponse updateLesson(CreateNewLessonRequest request);
    LessonResponse getLessonByIdAPI(Long lessonId);
    void deleteLesson(Long lessonId);
    BookResponse mappingBookToBookResponse(Books book);
    List<ImageDTO> updateImagesOfBooks(UpdateImageOfBookRequest newBookRequest);
    GrammarResponse createNewGrammar(GrammarRequest request);
    GrammarResponse updateGrammar(GrammarRequest request);
    void deleteGrammar(Long grammarId);
    List<GrammarResponse> getAllGrammarByLesson(Long lessonId);
    ExampleResponse createNewExample(ExampleRequest request);
    ExampleResponse updateExample(ExampleRequest request);
    List<ExampleResponse> findAllExampleOfGrammar(Long grammarId);
    ExerciseKeywordDTO createNewExcercise(ExerciseKeywordDTO exerciseKeywordDTO);
    ExerciseKeywordDTO updateExcercise(ExerciseKeywordDTO exerciseKeywordDTO);
    List<ExerciseKeywordDTO> getAllExcercisesKeywordOfLesson(Long lessonId);


}

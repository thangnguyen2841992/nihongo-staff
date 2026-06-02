package com.nihongo.staff.service;

import com.nihongo.staff.model.Books;
import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
import com.nihongo.staff.model.dto.*;

import java.util.List;

public interface IStaffService {
    BookResponse createNewBook(CreateNewBookRequest newBookRequest);
    BookResponse updateBook(UpdateBookRequest bookRequest);
    BookResponse   getBookDetail(Long bookId);
    List<Types> getTypes();
    List<Levels> getLevels();
    List<BookResponse> getBooks();
    List<BookResponse> getBooksByLevelAndType(Long levelId, Long typeId);
    List<LessonResponse> getAllLessonByBook(Long bookId);
    LessonResponse createNewLesson(CreateNewLessonRequest request);
    BookResponse mappingBookToBookResponse(Books book);
    List<ImageDTO> updateImagesOfBooks(UpdateImageOfBookRequest newBookRequest);
    GrammarResponse createNewGrammar(GrammarRequest request);
    GrammarResponse updateGrammar(GrammarRequest request);
    void deleteGrammar(Long grammarId);
    List<GrammarResponse> getAllGrammarByLesson(Long lessonId);
    ExampleResponse createNewExample(ExampleRequest request);
    ExampleResponse updateExample(ExampleRequest request);
    List<ExampleResponse> findAllExampleOfGrammar(Long grammarId);
}

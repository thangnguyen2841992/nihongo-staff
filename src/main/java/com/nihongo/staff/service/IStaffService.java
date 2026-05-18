package com.nihongo.staff.service;

import com.nihongo.staff.model.Books;
import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
import com.nihongo.staff.model.dto.*;

import java.util.List;

public interface IStaffService {
    BookResponse createNewBook(CreateNewBookRequest newBookRequest);
    BookResponse updateBook(UpdateBookRequest bookRequest);
    List<Types> getTypes();
    List<Levels> getLevels();
    List<BookResponse> getBooks();
    List<BookResponse> getBooksByLevelAndType(Long levelId, Long typeId);
    BookResponse mappingBookToBookResponse(Books book);
    List<ImageDTO> updateImagesOfBooks(UpdateImageOfBookRequest newBookRequest);
}

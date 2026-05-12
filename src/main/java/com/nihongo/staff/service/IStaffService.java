package com.nihongo.staff.service;

import com.nihongo.staff.model.Books;
import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
import com.nihongo.staff.model.dto.BookResponse;
import com.nihongo.staff.model.dto.CreateNewBookRequest;

import java.util.List;

public interface IStaffService {
    BookResponse createNewBook(CreateNewBookRequest newBookRequest);
    List<Types> getTypes();
    List<Levels> getLevels();
    List<BookResponse> getBooks();
    BookResponse mappingBookToBookResponse(Books book);
}

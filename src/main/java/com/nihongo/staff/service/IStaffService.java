package com.nihongo.staff.service;

import com.nihongo.staff.model.Books;
import com.nihongo.staff.model.dto.CreateNewBookRequest;

public interface IStaffService {
    Books createNewBook(CreateNewBookRequest newBookRequest);

}

package com.nihongo.staff.service;

import com.nihongo.staff.model.Books;
import com.nihongo.staff.model.dto.CreateNewBookRequest;
import com.nihongo.staff.repository.IBookRepository;
import com.nihongo.staff.repository.ILessonsRepository;
import com.nihongo.staff.repository.ITypeRepository;
import org.springframework.stereotype.Service;

@Service
public class StaffServiceImpl implements IStaffService {
    private final IBookRepository bookRepository;

    private final ILessonsRepository lessonsRepository;

    private final ITypeRepository typeRepository;

    public StaffServiceImpl(IBookRepository bookRepository, ILessonsRepository lessonsRepository, ITypeRepository typeRepository) {
        this.bookRepository = bookRepository;
        this.lessonsRepository = lessonsRepository;
        this.typeRepository = typeRepository;
    }


    @Override
    public Books createNewBook(CreateNewBookRequest newBookRequest) {
        Books book = new Books();
        book.setBookName(newBookRequest.getBookName());
//        book.setTypes();
        return null;
    }
}

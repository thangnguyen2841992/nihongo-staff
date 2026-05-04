package com.nihongo.staff.service;

import com.nihongo.staff.model.Books;
import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
import com.nihongo.staff.model.dto.CreateNewBookRequest;
import com.nihongo.staff.repository.IBookRepository;
import com.nihongo.staff.repository.ILessonsRepository;
import com.nihongo.staff.repository.ILevelsRepository;
import com.nihongo.staff.repository.ITypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StaffServiceImpl implements IStaffService {
    private final IBookRepository bookRepository;

    private final ILessonsRepository lessonsRepository;

    private final ITypeRepository typeRepository;

    private final ILevelsRepository levelsRepository;

    public StaffServiceImpl(IBookRepository bookRepository, ILessonsRepository lessonsRepository, ITypeRepository typeRepository, ILevelsRepository levelsRepository) {
        this.bookRepository = bookRepository;
        this.lessonsRepository = lessonsRepository;
        this.typeRepository = typeRepository;
        this.levelsRepository = levelsRepository;
    }


    @Override
    public Books createNewBook(CreateNewBookRequest newBookRequest) {
        Books book = new Books();
        book.setBookName(newBookRequest.getBookName());
        book.setTypes(newBookRequest.getTypes());
        book.setLevel(newBookRequest.getLevels());
        return this.bookRepository.save(book);
    }

    @Override
    public List<Types> getTypes() {
        return this.typeRepository.findAll();
    }

    @Override
    public List<Levels> getLevels() {
        return this.levelsRepository.findAll();
    }
}

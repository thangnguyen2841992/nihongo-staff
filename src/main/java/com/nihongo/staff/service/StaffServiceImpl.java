package com.nihongo.staff.service;

import com.nihongo.staff.model.Books;
import com.nihongo.staff.model.Images;
import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
import com.nihongo.staff.model.dto.BookResponse;
import com.nihongo.staff.model.dto.CreateNewBookRequest;
import com.nihongo.staff.model.dto.ImageDTO;
import com.nihongo.staff.repository.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class StaffServiceImpl implements IStaffService {
    private final IBookRepository bookRepository;

    private final ILessonsRepository lessonsRepository;

    private final ITypeRepository typeRepository;

    private final ILevelsRepository levelsRepository;

    private final IImageRepository imageRepository;

    public StaffServiceImpl(IBookRepository bookRepository, ILessonsRepository lessonsRepository, ITypeRepository typeRepository, ILevelsRepository levelsRepository, IImageRepository imageRepository) {
        this.bookRepository = bookRepository;
        this.lessonsRepository = lessonsRepository;
        this.typeRepository = typeRepository;
        this.levelsRepository = levelsRepository;
        this.imageRepository = imageRepository;
    }


    @Override
    public BookResponse createNewBook(CreateNewBookRequest newBookRequest) {
        Books book = new Books();
        book.setBookName(newBookRequest.getBookName());
        Levels level = this.levelsRepository.findById(newBookRequest.getLevelId()).orElse(null);
        Types type = this.typeRepository.findById(newBookRequest.getTypeId()).orElse(null);
        book.setTypes(type);
        book.setLevel(level);
        Books newBook = this.bookRepository.save(book);
        for (String imgUrl : newBookRequest.getUrls()) {
            Images image = new Images();
            image.setBooks(newBook);
            image.setUrl(imgUrl);
            this.imageRepository.save(image);
        }

        return mappingBookToBookResponse(newBook);
    }

    @Override
    public List<Types> getTypes() {
        return this.typeRepository.findAll();
    }

    @Override
    public List<Levels> getLevels() {
        return this.levelsRepository.findAll();
    }

    @Override
    public List<BookResponse> getBooks() {
        List<Books> books = this.bookRepository.findAll();
        List<BookResponse> bookResponses = new ArrayList<>();
        for (Books book : books) {
            BookResponse bookResponse = mappingBookToBookResponse(book);
            bookResponses.add(bookResponse);
        }
        return bookResponses;
    }

    @Override
    public BookResponse mappingBookToBookResponse(Books book) {
        BookResponse bookResponse = new BookResponse();
        bookResponse.setBookId(book.getBookId());
        bookResponse.setBookName(book.getBookName());
        bookResponse.setLevelName(book.getLevel().getLevelName());
        bookResponse.setTypeName(book.getTypes().getTypeName());
        List<Images> images = this.imageRepository.findByBooks_BookId(book.getBookId());
        List<ImageDTO> imageResponses = new ArrayList<>();
        for (Images image : images) {
            ImageDTO imageDTO = new ImageDTO();
            imageDTO.setImageId(image.getImageId());
            imageDTO.setImgUrl(image.getUrl());
            imageResponses.add(imageDTO);
        }
        bookResponse.setImageUrls(imageResponses);
        return bookResponse;
    }
}

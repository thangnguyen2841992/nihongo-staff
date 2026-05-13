package com.nihongo.staff.service;

import com.nihongo.staff.model.Books;
import com.nihongo.staff.model.Images;
import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
import com.nihongo.staff.model.dto.BookResponse;
import com.nihongo.staff.model.dto.CreateNewBookRequest;
import com.nihongo.staff.model.dto.ImageDTO;
import com.nihongo.staff.model.dto.UpdateImageOfBookRequest;
import com.nihongo.staff.repository.IBookRepository;
import com.nihongo.staff.repository.IImageRepository;
import com.nihongo.staff.repository.ILessonsRepository;
import com.nihongo.staff.repository.ILevelsRepository;
import com.nihongo.staff.repository.ITypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public BookResponse createNewBook(CreateNewBookRequest request) {

        Levels level = this.levelsRepository.findById(request.getLevelId()).orElseThrow(() -> new RuntimeException("Level not found"));

        Types type = this.typeRepository.findById(request.getTypeId()).orElseThrow(() -> new RuntimeException("Type not found"));

        Books book = new Books();

        book.setBookName(request.getBookName());

        book.setLevel(level);

        book.setTypes(type);

        Books savedBook = this.bookRepository.save(book);

        if (request.getUrls() != null && !request.getUrls().isEmpty()) {

            List<Images> images = request.getUrls().stream().map(url -> {

                Images image = new Images();

                image.setBooks(savedBook);

                image.setUrl(url);

                return image;

            }).toList();

            this.imageRepository.saveAll(images);
        }

        return mappingBookToBookResponse(savedBook);
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
        return this.bookRepository.findAll().stream().map(this::mappingBookToBookResponse).toList();
    }


    @Override
    @Transactional
    public List<ImageDTO> updateImagesOfBooks(UpdateImageOfBookRequest request) {

        Books book = this.bookRepository.findById(request.getBookId()).orElseThrow(() -> new RuntimeException("Book not found"));

        if (request.getListDeleteImg() != null && !request.getListDeleteImg().isEmpty()) {

            this.imageRepository.deleteAllById(request.getListDeleteImg());
        }

        if (request.getListAddImg() != null && !request.getListAddImg().isEmpty()) {

            List<Images> newImages = request.getListAddImg().stream().map(imgUrl -> {

                Images image = new Images();

                image.setBooks(book);

                image.setUrl(imgUrl);

                return image;

            }).toList();

            this.imageRepository.saveAll(newImages);
        }

        return this.imageRepository.findByBooks_BookId(request.getBookId()).stream().map(this::mapImageToDTO).toList();
    }

    @Override
    public BookResponse mappingBookToBookResponse(Books book) {

        BookResponse response = new BookResponse();

        response.setBookId(book.getBookId());

        response.setBookName(book.getBookName());

        response.setLevelName(book.getLevel().getLevelName());

        response.setTypeName(book.getTypes().getTypeName());

        List<ImageDTO> imageResponses = this.imageRepository.findByBooks_BookId(book.getBookId()).stream().map(this::mapImageToDTO).toList();

        response.setImageUrls(imageResponses);

        return response;
    }

    private ImageDTO mapImageToDTO(Images image) {

        ImageDTO dto = new ImageDTO();
        dto.setImageId(image.getImageId());
        dto.setImgUrl(image.getUrl());

        return dto;
    }
}
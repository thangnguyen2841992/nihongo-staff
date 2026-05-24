package com.nihongo.staff.service;

import com.nihongo.staff.controller.ResourceNotFoundException;
import com.nihongo.staff.model.*;
import com.nihongo.staff.model.dto.*;
import com.nihongo.staff.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements IStaffService {

    private final IBookRepository bookRepository;

    private final ILessonsRepository lessonsRepository;

    private final ITypeRepository typeRepository;

    private final ILevelsRepository levelsRepository;

    private final IImageRepository imageRepository;
    private final IGrammarRepository grammarRepository;

    @Override
    @Transactional
    public BookResponse createNewBook(CreateNewBookRequest request) {

        Levels level = getLevelById(request.getLevelId());

        Types type = getTypeById(request.getTypeId());

        Books book = new Books();
        book.setBookName(request.getBookName());
        book.setLevel(level);
        book.setTypes(type);

        Books savedBook = bookRepository.save(book);

        saveImages(savedBook, request.getUrls());

        return mappingBookToBookResponse(savedBook);
    }

    @Transactional
    @Override
    public BookResponse updateBook(UpdateBookRequest request) {

        Books book = getBookById(request.getBookId());

        book.setBookName(request.getBookName());
        book.setLevel(getLevelById(request.getLevelId()));
        book.setTypes(getTypeById(request.getTypeId()));

        return mappingBookToBookResponse(book);
    }

    @Override
    @Transactional(readOnly = true)
    public BookResponse getBookDetail(Long bookId) {
        Books book = getBookById(bookId);
        return mappingBookToBookResponse(book);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Types> getTypes() {
        return typeRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Levels> getLevels() {
        return levelsRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookResponse> getBooks() {

        List<Books> books = bookRepository.findAllWithRelations();

        Map<Long, List<ImageDTO>> imageMap = getImageMap(books);

        return books.stream().map(book -> mapBook(book, imageMap.get(book.getBookId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookResponse> getBooksByLevelAndType(Long levelId, Long typeId) {

        List<Books> books = bookRepository.findByLevel_LevelIdAndTypes_TypeId(levelId, typeId);

        Map<Long, List<ImageDTO>> imageMap = getImageMap(books);

        return books.stream().map(book -> mapBook(book, imageMap.get(book.getBookId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LessonResponse> getAllLessonByBook(Long bookId) {

        return lessonsRepository.findByBook_BookId(bookId).stream().map(this::mapLessonToResponse).toList();
    }

    @Override
    @Transactional
    public LessonResponse createNewLesson(CreateNewLessonRequest request) {
        Lessons newLessons = new Lessons();
        newLessons.setName(request.getName());
        newLessons.setDescription(request.getDescription());
        newLessons.setBook(this.bookRepository.findById(request.getBookId()).orElseThrow(() -> new RuntimeException("Book not found")));
        return mapLessonToResponse(this.lessonsRepository.save(newLessons));
    }

    @Override
    @Transactional
    public List<ImageDTO> updateImagesOfBooks(UpdateImageOfBookRequest request) {

        Books book = getBookById(request.getBookId());

        if (request.getListDeleteImg() != null && !request.getListDeleteImg().isEmpty()) {

            imageRepository.deleteAllById(request.getListDeleteImg());
        }

        saveImages(book, request.getListAddImg());

        return imageRepository.findByBooks_BookId(book.getBookId()).stream().map(this::mapImageToDTO).toList();
    }

    @Override
    @Transactional
    public GrammarResponse createNewGrammar(GrammarRequest request) {
        Grammar grammar = new Grammar();
        grammar.setTitle(request.getTitle());
        grammar.setDescription(request.getDescription());
        grammar.setStructure(request.getStructure());
        Lessons lessons = this.lessonsRepository.findById(request.getLessonId()).orElseThrow(() -> new RuntimeException("Lesson not found"));
        grammar.setLessons(lessons);
        return (mapGrammarToResponse(this.grammarRepository.save(grammar)));
    }

    @Override
    @Transactional
    public GrammarResponse updateGrammar(GrammarRequest request) {

        Grammar grammar = grammarRepository
                .findById(request.getGrammarId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Grammar not found with id: "
                                        + request.getGrammarId()
                        )
                );

        grammar.setTitle(request.getTitle().trim());
        grammar.setDescription(request.getDescription());
        grammar.setStructure(request.getStructure().trim());

        return mapGrammarToResponse(grammar);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GrammarResponse> getAllGrammarByLesson(Long lessonId) {
        List<Grammar> grammars = this.grammarRepository.findByLessons_LessonId(lessonId);
        List<GrammarResponse> grammarResponses = new ArrayList<>();
        for (Grammar grammar : grammars) {
            GrammarResponse grammarResponse = this.mapGrammarToResponse(grammar);
            grammarResponses.add(grammarResponse);
        }
        return grammarResponses;
    }


    @Override
    public BookResponse mappingBookToBookResponse(Books book) {

        List<ImageDTO> images = imageRepository.findByBooks_BookId(book.getBookId()).stream().map(this::mapImageToDTO).toList();

        return mapBook(book, images);
    }

    /* =========================================================
                            PRIVATE METHODS
       ========================================================= */

    private Books getBookById(Long bookId) {

        return bookRepository.findById(bookId).orElseThrow(() -> new RuntimeException("Book not found"));
    }

    private Levels getLevelById(Long levelId) {

        return levelsRepository.findById(levelId).orElseThrow(() -> new RuntimeException("Level not found"));
    }

    private Types getTypeById(Long typeId) {

        return typeRepository.findById(typeId).orElseThrow(() -> new RuntimeException("Type not found"));
    }

    private void saveImages(Books book, List<String> urls) {

        if (urls == null || urls.isEmpty()) {
            return;
        }

        List<Images> images = urls.stream().map(url -> {
            Images image = new Images();
            image.setBooks(book);
            image.setUrl(url);
            return image;
        }).toList();

        imageRepository.saveAll(images);
    }

    private BookResponse mapBook(Books book, List<ImageDTO> images) {

        BookResponse response = new BookResponse();

        response.setBookId(book.getBookId());
        response.setBookName(book.getBookName());
        response.setLevelName(book.getLevel().getLevelName());
        response.setTypeName(book.getTypes().getTypeName());
        response.setImageUrls(images == null ? new ArrayList<>() : images);

        return response;
    }

    private LessonResponse mapLessonToResponse(Lessons lesson) {

        LessonResponse response = new LessonResponse();

        response.setLessonId(lesson.getLessonId());
        response.setName(lesson.getName());
        response.setDescription(lesson.getDescription());
        response.setBookId(lesson.getBook().getBookId());

        return response;
    }

    private GrammarResponse mapGrammarToResponse(Grammar grammar) {

        GrammarResponse response = new GrammarResponse();

        response.setGrammarId(grammar.getGrammarId());
        response.setTitle(grammar.getTitle());
        response.setDescription(grammar.getDescription());
        response.setLessonId(grammar.getLessons().getLessonId());
        response.setStructure(grammar.getStructure());
        return response;
    }

    private ImageDTO mapImageToDTO(Images image) {

        ImageDTO dto = new ImageDTO();

        dto.setImageId(image.getImageId());
        dto.setImgUrl(image.getUrl());

        return dto;
    }

    /**
     * Tối ưu tránh N+1 query images
     */
    private Map<Long, List<ImageDTO>> getImageMap(List<Books> books) {
        if (books.isEmpty()) {
            return Map.of();
        }
        List<Long> bookIds = books.stream().map(Books::getBookId).toList();

        return imageRepository.findByBooks_BookIdIn(bookIds).stream().collect(Collectors.groupingBy(image -> image.getBooks().getBookId(), Collectors.mapping(this::mapImageToDTO, Collectors.toList())));
    }
}
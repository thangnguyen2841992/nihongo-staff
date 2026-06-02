package com.nihongo.staff.service;

import com.nihongo.staff.controller.ResourceNotFoundException;
import com.nihongo.staff.model.*;
import com.nihongo.staff.model.dto.*;
import com.nihongo.staff.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StaffServiceImpl implements IStaffService {

    private final IBookRepository bookRepository;
    private final ILessonsRepository lessonsRepository;
    private final ITypeRepository typeRepository;
    private final ILevelsRepository levelsRepository;
    private final IImageRepository imageRepository;
    private final IGrammarRepository grammarRepository;
    private final IExampleRepository exampleRepository;

    /* =========================================================
                            BOOK
       ========================================================= */

    @Override
    public BookResponse createNewBook(CreateNewBookRequest request) {

        Books book = new Books();
        book.setBookName(request.getBookName().trim());
        book.setLevel(getLevelById(request.getLevelId()));
        book.setTypes(getTypeById(request.getTypeId()));

        Books savedBook = bookRepository.save(book);

        saveImages(savedBook, request.getUrls());

        return mappingBookToBookResponse(savedBook);
    }

    @Override
    public BookResponse updateBook(UpdateBookRequest request) {

        Books book = getBookById(request.getBookId());

        book.setBookName(request.getBookName().trim());
        book.setLevel(getLevelById(request.getLevelId()));
        book.setTypes(getTypeById(request.getTypeId()));

        return mappingBookToBookResponse(book);
    }

    @Override
    @Transactional(readOnly = true)
    public BookResponse getBookDetail(Long bookId) {
        return mappingBookToBookResponse(getBookById(bookId));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable("books")
    public List<BookResponse> getBooks() {

        List<Books> books = bookRepository.findAllWithRelations();

        return mapBooks(books);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookResponse> getBooksByLevelAndType(Long levelId, Long typeId) {

        List<Books> books =
                bookRepository.findByLevel_LevelIdAndTypes_TypeId(levelId, typeId);

        return mapBooks(books);
    }

    @Override
    public List<ImageDTO> updateImagesOfBooks(UpdateImageOfBookRequest request) {

        Books book = getBookById(request.getBookId());

        Optional.ofNullable(request.getListDeleteImg())
                .filter(list -> !list.isEmpty())
                .ifPresent(imageRepository::deleteAllById);

        saveImages(book, request.getListAddImg());

        return imageRepository.findByBooks_BookId(book.getBookId())
                .stream()
                .map(this::mapImageToDTO)
                .toList();
    }

    /* =========================================================
                            LESSON
       ========================================================= */

    @Override
    @Transactional(readOnly = true)
    public List<LessonResponse> getAllLessonByBook(Long bookId) {

        return lessonsRepository.findByBook_BookId(bookId)
                .stream()
                .map(this::mapLessonToResponse)
                .toList();
    }

    @Override
    public LessonResponse createNewLesson(CreateNewLessonRequest request) {

        Lessons lesson = new Lessons();

        lesson.setName(request.getName().trim());
        lesson.setDescription(request.getDescription());
        lesson.setBook(getBookById(request.getBookId()));

        return mapLessonToResponse(
                lessonsRepository.save(lesson)
        );
    }

    /* =========================================================
                            GRAMMAR
       ========================================================= */

    @Override
    public GrammarResponse createNewGrammar(GrammarRequest request) {

        Grammar grammar = new Grammar();

        grammar.setTitle(request.getTitle().trim());
        grammar.setDescription(request.getDescription());
        grammar.setStructure(request.getStructure().trim());
        grammar.setLessons(getLessonById(request.getLessonId()));

        return mapGrammarToResponse(
                grammarRepository.save(grammar)
        );
    }

    @Override
    public GrammarResponse updateGrammar(GrammarRequest request) {

        Grammar grammar = getGrammarById(request.getGrammarId());

        grammar.setTitle(request.getTitle().trim());
        grammar.setDescription(request.getDescription());
        grammar.setStructure(request.getStructure().trim());

        return mapGrammarToResponse(grammar);
    }

    @Override
    public void deleteGrammar(Long grammarId) {

        grammarRepository.delete(getGrammarById(grammarId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GrammarResponse> getAllGrammarByLesson(Long lessonId) {

        return grammarRepository.findByLessons_LessonId(lessonId)
                .stream()
                .map(this::mapGrammarToResponse)
                .toList();
    }

    /* =========================================================
                            EXAMPLE
       ========================================================= */

    @Override
    public ExampleResponse createNewExample(ExampleRequest request) {

        Example example = new Example();

        example.setNihongo(request.getNihongo().trim());
        example.setVietnamese(request.getVietnamese().trim());
        example.setGrammar(getGrammarById(request.getGrammarId()));

        return mapExampleToDTO(
                exampleRepository.save(example)
        );
    }

    @Override
    @Transactional
    public ExampleResponse updateExample(ExampleRequest request) {
        Example example = this.exampleRepository.findById(request.getExampleId()).orElseThrow(() -> new ResourceNotFoundException("Example not found"));
        example.setNihongo(request.getNihongo());
        example.setVietnamese(request.getVietnamese());
        return mapExampleToDTO(example);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExampleResponse> findAllExampleOfGrammar(Long grammarId) {

        return exampleRepository.findByGrammar_GrammarId(grammarId)
                .stream()
                .map(this::mapExampleToDTO)
                .toList();
    }

    /* =========================================================
                            TYPE / LEVEL
       ========================================================= */

    @Override
    @Transactional(readOnly = true)
    @Cacheable("types")
    public List<Types> getTypes() {
        return typeRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable("levels")
    public List<Levels> getLevels() {
        return levelsRepository.findAll();
    }

    /* =========================================================
                            MAPPING
       ========================================================= */

    @Override
    @Transactional(readOnly = true)
    public BookResponse mappingBookToBookResponse(Books book) {

        List<ImageDTO> images = imageRepository
                .findByBooks_BookId(book.getBookId())
                .stream()
                .map(this::mapImageToDTO)
                .toList();

        return mapBook(book, images);
    }

    private List<BookResponse> mapBooks(List<Books> books) {

        Map<Long, List<ImageDTO>> imageMap = getImageMap(books);

        return books.stream()
                .map(book ->
                        mapBook(
                                book,
                                imageMap.getOrDefault(
                                        book.getBookId(),
                                        Collections.emptyList()
                                )
                        )
                )
                .toList();
    }

    private BookResponse mapBook(Books book, List<ImageDTO> images) {

        BookResponse response = new BookResponse();

        response.setBookId(book.getBookId());
        response.setBookName(book.getBookName());
        response.setLevelName(book.getLevel().getLevelName());
        response.setTypeName(book.getTypes().getTypeName());
        response.setImageUrls(images);

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
        response.setStructure(grammar.getStructure());
        response.setLessonId(grammar.getLessons().getLessonId());

        return response;
    }

    private ImageDTO mapImageToDTO(Images image) {

        ImageDTO dto = new ImageDTO();

        dto.setImageId(image.getImageId());
        dto.setImgUrl(image.getUrl());

        return dto;
    }

    private ExampleResponse mapExampleToDTO(Example example) {

        ExampleResponse response = new ExampleResponse();

        response.setExampleId(example.getExampleId());
        response.setNihongo(example.getNihongo());
        response.setVietnamese(example.getVietnamese());
        response.setGrammarId(example.getGrammar().getGrammarId());

        return response;
    }

    /* =========================================================
                            PRIVATE METHODS
       ========================================================= */

    private Books getBookById(Long bookId) {

        return bookRepository.findById(bookId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Book not found with id: " + bookId
                        )
                );
    }

    private Lessons getLessonById(Long lessonId) {

        return lessonsRepository.findById(lessonId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Lesson not found with id: " + lessonId
                        )
                );
    }

    private Grammar getGrammarById(Long grammarId) {

        return grammarRepository.findById(grammarId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Grammar not found with id: " + grammarId
                        )
                );
    }

    private Levels getLevelById(Long levelId) {

        return levelsRepository.findById(levelId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Level not found with id: " + levelId
                        )
                );
    }

    private Types getTypeById(Long typeId) {

        return typeRepository.findById(typeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Type not found with id: " + typeId
                        )
                );
    }

    private void saveImages(Books book, List<String> urls) {

        if (urls == null || urls.isEmpty()) {
            return;
        }

        List<Images> images = urls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .distinct()
                .map(url -> {
                    Images image = new Images();
                    image.setBooks(book);
                    image.setUrl(url);
                    return image;
                })
                .toList();

        imageRepository.saveAll(images);
    }

    /**
     * Tránh N+1 query images
     */
    private Map<Long, List<ImageDTO>> getImageMap(List<Books> books) {

        if (books.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> bookIds = books.stream()
                .map(Books::getBookId)
                .toList();

        return imageRepository.findByBooks_BookIdIn(bookIds)
                .stream()
                .collect(Collectors.groupingBy(
                        image -> image.getBooks().getBookId(),
                        Collectors.mapping(
                                this::mapImageToDTO,
                                Collectors.toList()
                        )
                ));
    }
}
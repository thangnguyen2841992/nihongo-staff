package com.nihongo.staff.service;

import com.nihongo.staff.controller.ResourceNotFoundException;
import com.nihongo.staff.model.*;
import com.nihongo.staff.model.dto.*;
import com.nihongo.staff.repository.*;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import com.nihongo.staff.security.ContentHtml;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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
    private final IExerciseKeywordRepository excersiceKeywordRepository;
    private final IExerciseTypeRepository exerciseTypeRepository;
    /* =========================================================
                            BOOK
       ========================================================= */

    @Override
    @CacheEvict(value = "books", allEntries = true)
    @Transactional
    public BookResponse createNewBook(CreateNewBookRequest request) {

        Books book = new Books();
        book.setBookName(request.getBookName().trim());
        book.setPublicationStatus(PublicationStatus.DRAFT);
        book.setLevel(getLevelById(request.getLevelId()));
        book.setTypes(getTypeById(request.getTypeId()));
        Books savedBook = bookRepository.save(book);
        saveImages(savedBook, request.getUrls());
        return mappingBookToBookResponse(savedBook);
    }


    @Override
    @CacheEvict(value = "books", allEntries = true)
    @Transactional
    public BookResponse updateBook(UpdateBookRequest request) {

        Books book = getBookById(request.getBookId());
        ensureDraft(book);
        book.setBookName(request.getBookName().trim());
        book.setLevel(getLevelById(request.getLevelId()));
        book.setTypes(getTypeById(request.getTypeId()));
        return mappingBookToBookResponse(book);
    }


    @Override
    @Transactional(readOnly = true)
    public BookResponse getBookDetail(
            Long bookId
    ) {

        return mappingBookToBookResponse(
                getBookById(bookId)
        );
    }


    @Override
    @Transactional(readOnly = true)
    @Cacheable("books")
    public List<BookResponse> getBooks() {

        List<Books> books =
                bookRepository.findAllWithRelations();

        return mapBooks(books);
    }


    @Override
    @Transactional(readOnly = true)
    public List<BookResponse>
    getBooksByLevelAndType(
            Long levelId,
            Long typeId
    ) {

        List<Books> books =
                bookRepository
                        .findByLevel_LevelIdAndTypes_TypeId(
                                levelId,
                                typeId
                        );

        return mapBooks(books);
    }


    @Override
    @CacheEvict(value = "books", allEntries = true)
    @Transactional
    public List<ImageDTO>
    updateImagesOfBooks(
            UpdateImageOfBookRequest request
    ) {

        Books book =
                getBookById(
                        request.getBookId()
                );
        ensureDraft(book);

        if (request.getListDeleteImg() != null && !request.getListDeleteImg().isEmpty()) {
            Set<Long> ownedIds = imageRepository.findByBooks_BookId(book.getBookId()).stream()
                    .map(Images::getImageId).collect(Collectors.toSet());
            if (!ownedIds.containsAll(request.getListDeleteImg())) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Ảnh cần xóa không thuộc sách này");
            }
        }

        Optional.ofNullable(
                        request.getListDeleteImg()
                )
                .filter(
                        list -> !list.isEmpty()
                )
                .ifPresent(
                        imageRepository::deleteAllById
                );

        saveImages(
                book,
                request.getListAddImg()
        );

        return imageRepository
                .findByBooks_BookId(
                        book.getBookId()
                )
                .stream()
                .map(this::mapImageToDTO)
                .toList();
    }


    /* =========================================================
                            LESSON
       ========================================================= */

    @Override
    @Transactional(readOnly = true)
    public List<LessonResponse>
    getAllLessonByBook(
            Long bookId
    ) {

        return lessonsRepository
                .findByBook_BookId(bookId)
                .stream()
                .map(this::mapLessonToResponse)
                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    public List<BookResponse>
    getBooksByLevel(
            Long levelId
    ) {

        return mapBooks(bookRepository.findByLevel_LevelId(levelId));
    }


    @Override
    @Transactional
    public LessonResponse createNewLesson(
            CreateNewLessonRequest request
    ) {

        Lessons lesson =
                new Lessons();

        lesson.setName(
                request.getName().trim()
        );

        lesson.setDescription(
                request.getDescription()
        );

        Books book = getBookById(request.getBookId());
        ensureDraft(book);
        lesson.setBook(book);

        lesson.setReading(
                ContentHtml.clean(request.getReading())
        );
        lesson.setAudioTrack(validAudioTrack(request.getAudioTrack()));

        return mapLessonToResponse(
                lessonsRepository.save(
                        lesson
                )
        );
    }


    @Override
    @Transactional
    public LessonResponse updateLesson(
            CreateNewLessonRequest request
    ) {

        Lessons lessons =
                this.lessonsRepository
                        .findById(
                                request.getLessonId()
                        )
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Lessons not found"
                                        )
                        );
        ensureDraft(lessons.getBook());

        lessons.setName(
                request.getName().trim()
        );

        lessons.setReading(
                ContentHtml.clean(request.getReading())
        );
        if (request.getAudioTrack() != null) lessons.setAudioTrack(validAudioTrack(request.getAudioTrack()));

        lessons.setDescription(
                request.getDescription()
        );

        return mapLessonToResponse(
                lessons
        );
    }


    @Override
    public LessonResponse getLessonByIdAPI(
            Long lessonId
    ) {

        return mapLessonToResponse(
                getLessonById(lessonId)
        );
    }


    @Override
    @Transactional
    public void deleteLesson(
            Long lessonId
    ) {

        Lessons lessons =
                this.lessonsRepository
                        .findById(lessonId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Lessons not found"
                                        )
                        );
        ensureDraft(lessons.getBook());

        this.lessonsRepository.deleteById(
                lessons.getLessonId()
        );
    }


    /* =========================================================
                            GRAMMAR
       ========================================================= */

    @Override
    @Transactional
    public GrammarResponse createNewGrammar(
            GrammarRequest request
    ) {

        Grammar grammar =
                new Grammar();

        grammar.setTitle(
                request.getTitle().trim()
        );

        grammar.setDescription(
                request.getDescription()
        );

        Lessons lesson = getLessonById(request.getLessonId());
        ensureDraft(lesson.getBook());
        grammar.setLessons(lesson);

        grammar.setImageUrl(
                request.getImageUrl().trim()
        );

        return mapGrammarToResponse(
                grammarRepository.save(
                        grammar
                )
        );
    }


    @Override
    @Transactional
    public GrammarResponse updateGrammar(
            GrammarRequest request
    ) {

        Grammar grammar =
                getGrammarById(
                        request.getGrammarId()
                );
        ensureDraft(grammar.getLessons().getBook());

        grammar.setTitle(
                request.getTitle().trim()
        );

        grammar.setDescription(
                request.getDescription()
        );

        grammar.setImageUrl(
                request.getImageUrl().trim()
        );

        return mapGrammarToResponse(
                grammar
        );
    }


    @Override
    @Transactional
    public void deleteGrammar(
            Long grammarId
    ) {

        Grammar grammar = getGrammarById(grammarId);
        ensureDraft(grammar.getLessons().getBook());
        grammarRepository.delete(grammar);
    }


    @Override
    @Transactional(readOnly = true)
    public List<GrammarResponse>
    getAllGrammarByLesson(
            Long lessonId
    ) {

        return grammarRepository
                .findByLessons_LessonId(lessonId)
                .stream()
                .map(this::mapGrammarToResponse)
                .toList();
    }


    /* =========================================================
                            EXAMPLE
       ========================================================= */

    @Override
    @Transactional
    public ExampleResponse createNewExample(
            ExampleRequest request
    ) {

        Example example =
                new Example();

        example.setNihongo(
                ContentHtml.clean(request.getNihongo().trim())
        );

        example.setVietnamese(
                ContentHtml.clean(request.getVietnamese().trim())
        );

        Grammar grammar = getGrammarById(request.getGrammarId());
        ensureDraft(grammar.getLessons().getBook());
        example.setGrammar(grammar);

        return mapExampleToDTO(
                exampleRepository.save(
                        example
                )
        );
    }


    @Override
    @Transactional
    public ExampleResponse updateExample(
            ExampleRequest request
    ) {

        Example example =
                this.exampleRepository
                        .findById(
                                request.getExampleId()
                        )
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Example not found"
                                        )
                        );
        ensureDraft(example.getGrammar().getLessons().getBook());

        example.setNihongo(
                ContentHtml.clean(request.getNihongo())
        );

        example.setVietnamese(
                ContentHtml.clean(request.getVietnamese())
        );

        return mapExampleToDTO(
                example
        );
    }


    @Override
    @Transactional(readOnly = true)
    public List<ExampleResponse>
    findAllExampleOfGrammar(
            Long grammarId
    ) {

        return exampleRepository
                .findByGrammar_GrammarId(grammarId)
                .stream()
                .map(this::mapExampleToDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ContentLocationResponse getBookLocation(Long bookId) {
        return bookRepository.findLocationById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + bookId));
    }

    @Override
    @Transactional(readOnly = true)
    public ContentLocationResponse getLessonLocation(Long lessonId) {
        return lessonsRepository.findLocationById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id: " + lessonId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExampleResponse> findAllExamplesOfLesson(Long lessonId) {
        return exampleRepository.findByLessonIdWithGrammar(lessonId)
                .stream().map(this::mapExampleToDTO).toList();
    }


    /* =========================================================
                         EXERCISE
       ========================================================= */

    @Override
    @Transactional
    public ExerciseKeywordDTO createNewExcercise(
            ExerciseKeywordDTO dto
    ) {

        Lessons lesson =
                getLessonById(
                        dto.getLessonId()
                );
        ensureDraft(lesson.getBook());

        ExerciseType exerciseType =
                getExerciseTypeById(
                        dto.getExerciseTypeId()
                );

        return mapExerciseToDTO(
                excersiceKeywordRepository.save(
                        mapToEntity(
                                dto,
                                lesson,
                                exerciseType
                        )
                )
        );
    }


    @Override
    @Transactional
    public ExerciseKeywordDTO updateExcercise(
            ExerciseKeywordDTO dto
    ) {

        ExersiceKeyword entity =
                excersiceKeywordRepository
                        .findById(
                                dto.getExerciseKeywordId()
                        )
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Exercise not found"
                                        )
                        );
        ensureDraft(entity.getLessons().getBook());
        ensureDraft(getLessonById(dto.getLessonId()).getBook());

        ExersiceKeyword updated =
                mapToEntity(
                        dto,
                        getLessonById(
                                dto.getLessonId()
                        ),
                        getExerciseTypeById(
                                dto.getExerciseTypeId()
                        )
                );

        entity.setContentNihongo(
                updated.getContentNihongo()
        );

        entity.setAnswerA(
                updated.getAnswerA()
        );

        entity.setAnswerB(
                updated.getAnswerB()
        );

        entity.setAnswerC(
                updated.getAnswerC()
        );

        entity.setAnswerD(
                updated.getAnswerD()
        );

        entity.setCorrectAnswer(
                updated.getCorrectAnswer()
        );

        entity.setLessons(
                updated.getLessons()
        );

        entity.setExerciseType(
                updated.getExerciseType()
        );

        return mapExerciseToDTO(
                entity
        );
    }


    @Override
    public List<ExerciseKeywordDTO>
    getAllExcercisesKeywordOfLesson(
            Long lessonId
    ) {

        List<ExersiceKeyword>
                exersiceKeywords =
                this.excersiceKeywordRepository
                        .findByLessons_LessonId(
                                lessonId
                        );

        List<ExerciseKeywordDTO> dtos =
                new ArrayList<>();

        for (
                ExersiceKeyword exersiceKeyword
                : exersiceKeywords
        ) {

            dtos.add(
                    mapExerciseToDTO(
                            exersiceKeyword
                    )
            );
        }

        return dtos;
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


    @Override
    @Transactional(readOnly = true)
    @Cacheable("exerciseTypes")
    public List<ExerciseType>
    getExerciseTypes() {

        return this.exerciseTypeRepository
                .findAll();
    }


    /* =========================================================
                            MAPPING
       ========================================================= */

    @Override
    @Transactional(readOnly = true)
    public BookResponse
    mappingBookToBookResponse(
            Books book
    ) {

        List<ImageDTO> images =
                imageRepository
                        .findByBooks_BookId(
                                book.getBookId()
                        )
                        .stream()
                        .map(this::mapImageToDTO)
                        .toList();

        return mapBook(
                book,
                images
        );
    }


    private void validateKeyword(
            String contentNihongo
    ) {

        if (
                contentNihongo == null ||
                        contentNihongo.isBlank()
        ) {

            throw new RuntimeException(
                    "Nội dung tiếng Nhật không được để trống"
            );
        }

        Document doc =
                Jsoup.parse(
                        contentNihongo
                );

        Elements keywords =
                doc.select("u");

        if (keywords.size() != 1) {

            throw new RuntimeException(
                    "Phải gạch chân đúng 1 keyword"
            );
        }
    }


    private ExersiceKeyword mapToEntity(
            ExerciseKeywordDTO dto,
            Lessons lesson,
            ExerciseType exerciseType
    ) {

        validateKeyword(
                ContentHtml.clean(dto.getContentNihongo())
        );

        return ExersiceKeyword.builder()
                .exerciseKeywordId(
                        dto.getExerciseKeywordId()
                )
                .contentNihongo(
                        ContentHtml.clean(dto.getContentNihongo())
                )
                .answerA(
                        dto.getAnswerA()
                )
                .answerB(
                        dto.getAnswerB()
                )
                .answerC(
                        dto.getAnswerC()
                )
                .answerD(
                        dto.getAnswerD()
                )
                .correctAnswer(
                        dto.getCorrectAnswer()
                )
                .lessons(
                        lesson
                )
                .exerciseType(
                        exerciseType
                )
                .build();
    }


    private ExerciseKeywordDTO mapExerciseToDTO(
            ExersiceKeyword entity
    ) {

        return ExerciseKeywordDTO.builder()
                .audioUrl(entity.getAudioAssetId() == null ? null : "/api/staff/imported-audio/exercises/" + entity.getExerciseKeywordId())
                .exerciseKeywordId(
                        entity.getExerciseKeywordId()
                )
                .contentNihongo(
                        ContentHtml.clean(entity.getContentNihongo())
                )
                .answerA(
                        entity.getAnswerA()
                )
                .answerB(
                        entity.getAnswerB()
                )
                .answerC(
                        entity.getAnswerC()
                )
                .answerD(
                        entity.getAnswerD()
                )
                .correctAnswer(
                        entity.getCorrectAnswer()
                )
                .lessonId(
                        entity.getLessons()
                                .getLessonId()
                )
                .exerciseTypeId(
                        entity.getExerciseType()
                                .getExerciseTypeId()
                )
                .exerciseTypeName(
                        entity.getExerciseType()
                                .getName()
                )
                .build();
    }


    private List<BookResponse> mapBooks(
            List<Books> books
    ) {

        Map<Long, List<ImageDTO>> imageMap =
                getImageMap(books);

        return books.stream()
                .map(
                        book ->
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


    private BookResponse mapBook(
            Books book,
            List<ImageDTO> images
    ) {

        BookResponse response =
                new BookResponse();

        response.setBookId(
                book.getBookId()
        );

        response.setBookName(
                book.getBookName()
        );
        response.setPublicationStatus(book.getPublicationStatus() == null ? "PUBLISHED" : book.getPublicationStatus().name());

        response.setLevelName(
                book.getLevel().getLevelName()
        );

        response.setTypeName(
                book.getTypes().getTypeName()
        );

        response.setImageUrls(
                images
        );

        return response;
    }


    private LessonResponse mapLessonToResponse(
            Lessons lesson
    ) {

        LessonResponse response =
                new LessonResponse();

        response.setLessonId(
                lesson.getLessonId()
        );

        response.setName(
                lesson.getName()
        );

        response.setDescription(
                lesson.getDescription()
        );

        response.setBookId(
                lesson.getBook().getBookId()
        );

        response.setReading(
                ContentHtml.clean(lesson.getReading())
        );
        response.setAudioTrack(lesson.getAudioTrack());
        response.setAudioUrl(lesson.getAudioAssetId() == null ? null : "/api/staff/imported-audio/lessons/" + lesson.getLessonId());

        return response;
    }

    private String validAudioTrack(String track) {
        if (track == null || track.isBlank()) return null;
        if (!track.matches("(?:0[1-9]|[1-5][0-9]|6[0-4])"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Số CD phải nằm trong 01–64.");
        return track;
    }


    private GrammarResponse mapGrammarToResponse(
            Grammar grammar
    ) {

        GrammarResponse response =
                new GrammarResponse();

        response.setGrammarId(
                grammar.getGrammarId()
        );

        response.setTitle(
                grammar.getTitle()
        );

        response.setDescription(
                grammar.getDescription()
        );

        response.setLessonId(
                grammar.getLessons()
                        .getLessonId()
        );

        response.setImageUrl(
                grammar.getImageUrl()
        );

        return response;
    }


    private ImageDTO mapImageToDTO(
            Images image
    ) {

        ImageDTO dto =
                new ImageDTO();

        dto.setImageId(
                image.getImageId()
        );

        dto.setImgUrl(
                image.getUrl()
        );

        return dto;
    }


    private ExampleResponse mapExampleToDTO(
            Example example
    ) {

        ExampleResponse response =
                new ExampleResponse();

        response.setExampleId(
                example.getExampleId()
        );

        response.setNihongo(
                ContentHtml.clean(example.getNihongo())
        );

        response.setVietnamese(
                ContentHtml.clean(example.getVietnamese())
        );

        response.setGrammarId(
                example.getGrammar()
                        .getGrammarId()
        );

        return response;
    }


    /* =========================================================
                         PRIVATE METHODS
       ========================================================= */

    private void ensureDraft(Books book) {
        if (book.getPublicationStatus() != PublicationStatus.DRAFT)
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Chỉ được sửa sách ở trạng thái bản nháp. Hãy chuyển về bản nháp trước.");
    }

    private Books getBookById(
            Long bookId
    ) {

        return bookRepository
                .findById(bookId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Book not found with id: "
                                                + bookId
                                )
                );
    }


    private Lessons getLessonById(
            Long lessonId
    ) {

        return lessonsRepository
                .findById(lessonId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Lesson not found with id: "
                                                + lessonId
                                )
                );
    }


    private ExerciseType getExerciseTypeById(
            Long exerciseTypeId
    ) {

        return exerciseTypeRepository
                .findById(exerciseTypeId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Exercise not found with id: "
                                                + exerciseTypeId
                                )
                );
    }


    private Grammar getGrammarById(
            Long grammarId
    ) {

        return grammarRepository
                .findById(grammarId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Grammar not found with id: "
                                                + grammarId
                                )
                );
    }


    private Levels getLevelById(
            Long levelId
    ) {

        return levelsRepository
                .findById(levelId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Level not found with id: "
                                                + levelId
                                )
                );
    }


    private Types getTypeById(
            Long typeId
    ) {

        return typeRepository
                .findById(typeId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Type not found with id: "
                                                + typeId
                                )
                );
    }


    private void saveImages(
            Books book,
            List<String> urls
    ) {

        if (
                urls == null ||
                        urls.isEmpty()
        ) {
            return;
        }

        List<Images> images =
                urls.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(
                                url -> !url.isBlank()
                        )
                        .distinct()
                        .map(
                                url -> {

                                    Images image =
                                            new Images();

                                    image.setBooks(
                                            book
                                    );

                                    image.setUrl(
                                            url
                                    );

                                    return image;
                                }
                        )
                        .toList();

        imageRepository.saveAll(
                images
        );
    }


    /**
     * Tránh N+1 query images.
     */
    private Map<Long, List<ImageDTO>>
    getImageMap(
            List<Books> books
    ) {

        if (books.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> bookIds =
                books.stream()
                        .map(Books::getBookId)
                        .toList();

        return imageRepository
                .findByBooks_BookIdIn(bookIds)
                .stream()
                .collect(
                        Collectors.groupingBy(
                                image ->
                                        image.getBooks()
                                                .getBookId(),
                                Collectors.mapping(
                                        this::mapImageToDTO,
                                        Collectors.toList()
                                )
                        )
                );
    }


}

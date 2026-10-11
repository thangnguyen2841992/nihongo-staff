package com.nihongo.staff;
import com.nihongo.staff.model.*;
import com.nihongo.staff.model.dto.UpdateBookRequest;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.*;
import com.nihongo.staff.security.ContentAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.config.import=","spring.datasource.url=jdbc:h2:mem:content_ops;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=ContentOperationsPersistenceTest.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ContentOperationsPersistenceTest {
 @TestConfiguration @EntityScan(basePackageClasses=Books.class) @EnableJpaRepositories(basePackageClasses=IBookRepository.class)
 @Import({ContentOperationsService.class,ContentRevisionService.class,StaffServiceImpl.class,BookPublicationService.class}) static class Config {@Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}}
 @Autowired ContentOperationsService operations;@Autowired ContentRevisionService revisions;@Autowired IStaffService content;@Autowired BookPublicationService publication;
 @Autowired IBookRepository books;@Autowired ILessonsRepository lessons;@Autowired IExerciseKeywordRepository questions;@Autowired ILevelsRepository levels;@Autowired ITypeRepository types;@Autowired IExerciseTypeRepository exerciseTypes;
 @Autowired ContentIssueRepository issues;@Autowired ContentRevisionRepository revisionRows;@Autowired ExamTemplateRepository exams;@Autowired QuestionMetadataRepository metadata;@Autowired BookReviewTaskRepository tasks;@Autowired BookReviewEntryRepository reviewEntries;@Autowired ContentOperationAuditRepository audit;
 @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
 @MockitoBean ContentAccess access;@MockitoBean StaffDirectoryClient directory;
 Long bookId,lessonId,questionId,levelId,typeId,exerciseTypeId;JwtAuthenticationToken auth;
 @BeforeEach void setup(){
  audit.deleteAll();issues.deleteAll();revisionRows.deleteAll();exams.deleteAll();metadata.deleteAll();tasks.deleteAll();reviewEntries.deleteAll();questions.deleteAll();lessons.deleteAll();books.deleteAll();levels.deleteAll();types.deleteAll();exerciseTypes.deleteAll();
  var level=new Levels();level.setLevelName("N3");levelId=levels.saveAndFlush(level).getLevelId();var type=new Types();type.setTypeName("TRY");typeId=types.saveAndFlush(type).getTypeId();
  var book=new Books();book.setBookName("Sách gốc");book.setLevel(level);book.setTypes(type);book.setPublicationStatus(PublicationStatus.DRAFT);bookId=books.saveAndFlush(book).getBookId();
  var lesson=new Lessons();lesson.setBook(book);lesson.setName("Bài 1");lesson.setReading("本文");lessonId=lessons.saveAndFlush(lesson).getLessonId();
  var exerciseType=new ExerciseType();exerciseType.setName("Ngữ pháp");exerciseTypeId=exerciseTypes.saveAndFlush(exerciseType).getExerciseTypeId();
  var q=new ExersiceKeyword();q.setLessons(lesson);q.setExerciseType(exerciseType);q.setContentNihongo("日本は？");q.setAnswerA("Tokyo");q.setAnswerB("Osaka");q.setAnswerC("Nhật Bản");q.setAnswerD("Kyoto");q.setCorrectAnswer("C");questionId=questions.saveAndFlush(q).getExerciseKeywordId();
  auth=new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg","none").subject("editor").build(),List.of(new SimpleGrantedAuthority("ROLE_STAFF")));SecurityContextHolder.getContext().setAuthentication(auth);
  when(access.lesson(anyLong(),any())).thenReturn(true);when(directory.list()).thenReturn(List.of(new StaffDirectoryClient.Member("editor","Biên tập viên","STAFF")));
 }
 @AfterEach void clearSecurity(){SecurityContextHolder.clearContext();}
 @Test void issueValidatesLessonAndAssigneeAndRejectsStaleUpdate(){
  var issue=operations.report("learner",auth,new ContentOperationsService.Report(lessonId,questionId,"ANSWER","Đáp án câu này cần kiểm tra"));
  assertEquals("OPEN",issue.getStatus());assertEquals(1,operations.mine("learner",0).getTotalElements());assertEquals(0,operations.mine("other",0).getTotalElements());
  assertThrows(ResponseStatusException.class,()->operations.updateIssue(issue.getId(),new ContentOperationsService.IssueUpdate("IN_PROGRESS","unknown",null,issue.getVersion())));
  operations.updateIssue(issue.getId(),new ContentOperationsService.IssueUpdate("IN_PROGRESS","editor",null,issue.getVersion()));
  assertThrows(ResponseStatusException.class,()->operations.updateIssue(issue.getId(),new ContentOperationsService.IssueUpdate("RESOLVED","editor","Đã sửa",issue.getVersion())));
  assertEquals("IN_PROGRESS",issues.findById(issue.getId()).orElseThrow().getStatus());assertEquals(1,audit.count());
  when(access.lesson(lessonId,auth)).thenReturn(false);assertThrows(ResponseStatusException.class,()->operations.report("learner",auth,new ContentOperationsService.Report(lessonId,null,"AUDIO","Không nghe được file âm thanh")));assertEquals(1,issues.count());
 }
 @Test void issueCannotReferenceAQuestionFromAnotherLesson(){assertThrows(ResponseStatusException.class,()->operations.report("learner",auth,new ContentOperationsService.Report(lessonId+100,questionId,"ANSWER","Cần kiểm tra đáp án câu hỏi")));assertEquals(0,issues.count());}
 @Test void reviewFeedbackReturnsBookToDraftAndStaffCannotPublish(){
  publication.submit(bookId);assertEquals(1,operations.reviewQueue("IN_REVIEW",0).getTotalElements());
  assertThrows(ResponseStatusException.class,()->operations.review("editor",false,bookId,new ContentOperationsService.ReviewAction("PUBLISH",null,null,"IN_REVIEW")));
  operations.review("admin",true,bookId,new ContentOperationsService.ReviewAction("ASSIGN",null,"editor","IN_REVIEW"));assertEquals("editor",tasks.findById(bookId).orElseThrow().getAssignee());
  operations.review("admin",true,bookId,new ContentOperationsService.ReviewAction("REQUEST_CHANGES","Bổ sung giải thích cho đáp án câu hỏi",null,"IN_REVIEW"));
  assertEquals(PublicationStatus.DRAFT,books.findById(bookId).orElseThrow().getPublicationStatus());assertTrue(reviewEntries.findAll().stream().anyMatch(e->e.getNote()!=null&&e.getNote().contains("Bổ sung")));
  assertThrows(ResponseStatusException.class,()->operations.review("admin",true,bookId,new ContentOperationsService.ReviewAction("PUBLISH",null,null,"IN_REVIEW")));
 }
 @Test void questionBankFiltersAndExamSnapshotSurvivesLaterQuestionEdit(){
  assertEquals(1,operations.bank(levelId,exerciseTypeId,"NORMAL","日本",0).getTotalElements());assertEquals(0,operations.bank(levelId,exerciseTypeId,"HARD","",0).getTotalElements());
  operations.difficulty(questionId,"HARD");assertEquals(1,operations.bank(levelId,exerciseTypeId,"HARD","",0).getTotalElements());
  var exam=operations.createExam("editor",new ContentOperationsService.NewExam("Đề N3",List.of(questionId)));assertEquals("日本は？",exam.questions().get(0).content());
  var q=operations.bank(levelId,exerciseTypeId,"HARD","",0).getContent().get(0);var dto=new ExerciseKeywordDTO();dto.setExerciseKeywordId(q.id());dto.setLessonId(lessonId);dto.setExerciseTypeId(exerciseTypeId);dto.setContentNihongo("<u>Câu đã sửa</u>");dto.setAnswerA("A");dto.setAnswerB("B");dto.setAnswerC("C");dto.setAnswerD("D");dto.setCorrectAnswer("A");content.updateExcercise(dto);
  assertEquals("日本は？",operations.exam(exam.id()).questions().get(0).content());assertEquals("C",operations.exam(exam.id()).questions().get(0).correctAnswer());assertEquals(1,revisionRows.count());
  assertThrows(ResponseStatusException.class,()->operations.createExam("editor",new ContentOperationsService.NewExam("Trùng",List.of(questionId,questionId))));assertEquals(1,exams.count());
 }
 @Test void examReadsQuestionsAndMetadataInTwoQueriesRegardlessOfQuestionCount(){
  List<Long> ids=new ArrayList<>();
  for(int i=0;i<25;i++){
   var book=new Books();book.setBookName("Book "+i);book.setLevel(levels.findById(levelId).orElseThrow());book.setTypes(types.findById(typeId).orElseThrow());book=books.saveAndFlush(book);
   var lesson=new Lessons();lesson.setBook(book);lesson.setName("Lesson "+i);lesson=lessons.saveAndFlush(lesson);
   var exerciseType=new ExerciseType();exerciseType.setName("Type "+i);exerciseType=exerciseTypes.saveAndFlush(exerciseType);
   var q=new ExersiceKeyword();q.setLessons(lesson);q.setExerciseType(exerciseType);q.setContentNihongo("Question "+i);q.setCorrectAnswer("A");q=questions.saveAndFlush(q);ids.add(q.getExerciseKeywordId());
   if(i%2==0){var m=new QuestionMetadata();m.setExerciseId(q.getExerciseKeywordId());m.setDifficulty("HARD");metadata.saveAndFlush(m);}
  }
  Collections.reverse(ids);
  var statistics=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();statistics.setStatisticsEnabled(true);statistics.clear();
  try{
   var exam=operations.createExam("editor",new ContentOperationsService.NewExam("Ordered snapshot",ids));
   assertEquals(4,statistics.getPrepareStatementCount(),"Two batch reads plus the exam and audit inserts; no per-question queries");
   assertEquals(ids,exam.questions().stream().map(ContentOperationsService.Question::id).toList());
   for(int i=0;i<25;i++){
    var question=exam.questions().get(i);int source=24-i;
    assertEquals("Book "+source,question.bookName());assertEquals("Lesson "+source,question.lessonName());assertEquals("Type "+source,question.typeName());
    assertEquals(source%2==0?"HARD":"NORMAL",question.difficulty());
   }
  }finally{statistics.setStatisticsEnabled(false);}
 }
 @Test void publicationChecksAllLessonsWithOneLightweightQuestionQuery(){
  List<String> expectedErrors=new ArrayList<>();List<String> expectedWarnings=new ArrayList<>();
  for(int i=0;i<25;i++){
   var lesson=new Lessons();lesson.setBook(books.findById(bookId).orElseThrow());lesson.setName("Lesson "+i);lesson.setReading("本文");lesson=lessons.saveAndFlush(lesson);
   if(i%3==0){expectedWarnings.add("Bài "+lesson.getLessonId()+" chưa có bài tập.");continue;}
   var q=new ExersiceKeyword();q.setLessons(lesson);q.setExerciseType(exerciseTypes.findById(exerciseTypeId).orElseThrow());q.setContentNihongo("Question "+i);q.setCorrectAnswer(i%3==1?null:"B");q=questions.saveAndFlush(q);
   if(i%3==1)expectedErrors.add("Câu hỏi "+q.getExerciseKeywordId()+" chưa có đáp án hợp lệ.");
  }
  var statistics=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();statistics.setStatisticsEnabled(true);statistics.clear();
  try{
   var report=publication.inspect(bookId);
   assertEquals(3,statistics.getPrepareStatementCount(),"Book, lessons, and answer projections only");
   assertEquals(0,statistics.getEntityStatistics(ExersiceKeyword.class.getName()).getLoadCount(),"Publication checks must not load full question bodies");
   assertEquals(expectedErrors,report.errors());assertEquals(expectedWarnings,report.warnings());
  }finally{statistics.setStatisticsEnabled(false);}
 }
 @Test void editsCheckpointOldContentAndRestoreCanBeUndone(){
  content.updateBook(new UpdateBookRequest(bookId,"Tên mới","",levelId,typeId));var history=revisions.history("BOOK",bookId,0);assertTrue(history.current().contains("Tên mới"));assertTrue(history.revisions().getContent().get(0).getSnapshot().contains("Sách gốc"));assertEquals("editor",history.revisions().getContent().get(0).getActor());
  revisions.restore("BOOK",bookId,history.revisions().getContent().get(0).getId(),history.token());assertEquals("Sách gốc",books.findById(bookId).orElseThrow().getBookName());assertEquals(2,revisionRows.count());
  var restored=revisions.history("BOOK",bookId,0);revisions.restore("BOOK",bookId,restored.revisions().getContent().get(0).getId(),restored.token());assertEquals("Tên mới",books.findById(bookId).orElseThrow().getBookName());
 }
 @Test void staleRestoreAndPublishedBookCannotOverwriteContentOrCreateGhostHistory(){
  content.updateBook(new UpdateBookRequest(bookId,"Lần một","",levelId,typeId));var old=revisions.history("BOOK",bookId,0);
  content.updateBook(new UpdateBookRequest(bookId,"Lần hai","",levelId,typeId));long before=revisionRows.count();
  assertThrows(ResponseStatusException.class,()->revisions.restore("BOOK",bookId,old.revisions().getContent().get(0).getId(),old.token()));assertEquals(before,revisionRows.count());assertEquals("Lần hai",books.findById(bookId).orElseThrow().getBookName());
  publication.submit(bookId);publication.publish(bookId);var latest=revisions.history("BOOK",bookId,0);
  assertThrows(ResponseStatusException.class,()->revisions.restore("BOOK",bookId,old.revisions().getContent().get(0).getId(),latest.token()));assertEquals(before,revisionRows.count());
 }
}

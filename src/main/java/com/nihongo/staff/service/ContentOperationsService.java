package com.nihongo.staff.service;
import com.nihongo.staff.model.*;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.security.ContentAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ContentOperationsService {
 private final ContentIssueRepository issues;private final IExerciseKeywordRepository questions;private final QuestionMetadataRepository metadata;
 private final ExamTemplateRepository exams;private final IBookRepository books;private final BookReviewEntryRepository entries;private final BookReviewTaskRepository tasks;
 private final BookPublicationService publication;private final ContentAccess access;private final StaffDirectoryClient directory;private final EntityManager em;private final ObjectMapper json;
 private final ContentOperationAuditRepository audits;
 public record Report(@NotNull @Positive Long lessonId,Long exerciseId,@NotBlank String category,@NotBlank @Size(min=10,max=2000) String detail) {}
 public record IssueUpdate(@NotBlank String status,String assignee,@Size(max=2000) String resolution,long version) {}
 public record ReviewAction(@NotBlank String action,@Size(max=2000) String note,String assignee,@NotBlank String expectedStatus) {}
 public record ReviewBook(Long bookId,String name,PublicationStatus status,String assignee) {}
 public record Question(Long id,Long lessonId,String lessonName,Long bookId,String bookName,Long levelId,String levelName,Long typeId,String typeName,String difficulty,String content,String answerA,String answerB,String answerC,String answerD,String correctAnswer) {}
 public record NewExam(@NotBlank @Size(max=200) String title,@NotEmpty @Size(max=100) List<@NotNull @Positive Long> questionIds) {}
 public record Exam(Long id,String title,String owner,LocalDateTime createdAt,List<Question> questions) {}
 public static Pageable page(int page){if(page<0)throw bad("Trang không hợp lệ");return PageRequest.of(page,20);}
 @Transactional public ContentIssue report(String reporter,Authentication auth,Report input){
  if(!access.lesson(input.lessonId(),auth))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn chưa có quyền truy cập bài học");
  if(!List.of("ANSWER","GRAMMAR","AUDIO","OTHER").contains(input.category()))throw bad("Loại báo lỗi không hợp lệ");
  if(input.detail().trim().length()<10)throw bad("Mô tả lỗi ít nhất 10 ký tự");
  if(input.exerciseId()!=null){var q=questions.findById(input.exerciseId()).orElseThrow(()->bad("Không tìm thấy câu hỏi"));if(!q.getLessons().getLessonId().equals(input.lessonId()))throw bad("Câu hỏi không thuộc bài học");}
  if(issues.countByReporterAndCreatedAtAfter(reporter,LocalDateTime.now().minusDays(1))>=10)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Tối đa 10 báo lỗi mỗi ngày");
  var i=new ContentIssue();i.setReporter(reporter);i.setLessonId(input.lessonId());i.setExerciseId(input.exerciseId());i.setCategory(input.category());i.setDetail(input.detail().trim());i.setCreatedAt(LocalDateTime.now());i.setUpdatedAt(i.getCreatedAt());return issues.save(i);
 }
 public Page<ContentIssue> issues(String status,String assignee,int page){if(status!=null&&!statuses().contains(status))throw bad("Trạng thái không hợp lệ");return issues.search(status,assignee,page(page));}
 public Page<ContentIssue> mine(String reporter,int page){return issues.findByReporterOrderByCreatedAtDescIdDesc(reporter,page(page));}
 private List<String> statuses(){return List.of("OPEN","IN_PROGRESS","RESOLVED","DISMISSED");}
 private void assignee(String id){if(id!=null&&!directory.list().stream().anyMatch(u->u.userId().equals(id)))throw bad("Người được giao phải là staff/admin đang hoạt động");}
 @Transactional public ContentIssue updateIssue(Long id,IssueUpdate input){
  if(!statuses().contains(input.status()))throw bad("Trạng thái không hợp lệ");assignee(input.assignee());
  ContentIssue i=em.find(ContentIssue.class,id,LockModeType.PESSIMISTIC_WRITE);if(i==null)throw missing();if(i.getVersion()!=input.version())throw conflict("Báo lỗi đã thay đổi; hãy tải lại");
  if(List.of("RESOLVED","DISMISSED").contains(input.status())&&(input.resolution()==null||input.resolution().isBlank()))throw bad("Nhập cách xử lý hoặc lý do đóng báo lỗi");
  String previous=i.getStatus();i.setStatus(input.status());i.setAssignee(input.assignee());i.setResolution(input.resolution());i.setUpdatedAt(LocalDateTime.now());em.flush();audit("ISSUE_UPDATE","report:"+id,previous+" → "+input.status()+" / giao "+input.assignee()+" / "+input.resolution());return i;
 }
 public Page<ReviewBook> reviewQueue(String status,int page){
  PublicationStatus state;try{state=PublicationStatus.valueOf(status);}catch(Exception ex){throw bad("Trạng thái sách không hợp lệ");}
  var rows=books.findByPublicationStatusOrderByBookIdDesc(state,page(page));Map<Long,String> assignments=new HashMap<>();tasks.findAllById(rows.map(Books::getBookId).toList()).forEach(t->assignments.put(t.getBookId(),t.getAssignee()));
  return rows.map(b->new ReviewBook(b.getBookId(),b.getBookName(),publication.status(b),assignments.get(b.getBookId())));
 }
 @Transactional @CacheEvict(value="books",allEntries=true)
 public BookPublicationService.Review review(String actor,boolean admin,Long id,ReviewAction input){
  var b=em.find(Books.class,id,LockModeType.PESSIMISTIC_WRITE);if(b==null)throw missing();
  if(!publication.status(b).name().equals(input.expectedStatus()))throw conflict("Trạng thái sách đã thay đổi; hãy tải lại");
  if(!input.action().equals("SUBMIT")&&!admin)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ admin được duyệt và phân công sách");
  BookPublicationService.Review result;
  switch(input.action()){
   case "SUBMIT"->result=publication.submit(id);
   case "PUBLISH"->result=publication.publish(id);
   case "REQUEST_CHANGES"->{if(publication.status(b)!=PublicationStatus.IN_REVIEW)throw conflict("Sách chưa chờ duyệt");if(input.note()==null||input.note().trim().length()<10)throw bad("Lý do trả bài ít nhất 10 ký tự");result=publication.returnToDraft(id);}
   case "ASSIGN"->{assignee(input.assignee());var t=tasks.findById(id).orElseGet(BookReviewTask::new);t.setBookId(id);t.setAssignee(input.assignee());tasks.save(t);result=publication.inspect(id);}
   default->throw bad("Thao tác duyệt không hợp lệ");
  }
  if(List.of("ASSIGN","REQUEST_CHANGES").contains(input.action())||input.note()!=null&&!input.note().isBlank()){
   var e=new BookReviewEntry();e.setBookId(id);e.setActor(actor);e.setAction(List.of("ASSIGN","REQUEST_CHANGES").contains(input.action())?input.action():"REVIEW_FEEDBACK");e.setNote(input.note());e.setAssignee(input.assignee());e.setCreatedAt(LocalDateTime.now());entries.save(e);
  }
  return result;
 }
 public Page<BookReviewEntry> reviewHistory(Long bookId,int page){return entries.findByBookIdOrderByCreatedAtDescIdDesc(bookId,page(page));}
 public Page<BookReviewEntry> reviewAudit(int page){return entries.findAllByOrderByCreatedAtDescIdDesc(page(page));}
 public Page<Question> bank(Long level,Long type,String difficulty,String search,int page){
  if(difficulty!=null&&!List.of("EASY","NORMAL","HARD").contains(difficulty))throw bad("Độ khó không hợp lệ");
  if(search!=null&&search.length()>200)throw bad("Từ khóa quá dài");
  var rows=questions.bank(level,type,difficulty,"%"+(search==null?"":search.toLowerCase(Locale.ROOT))+"%",page(page));
  Map<Long,String> difficulties=new HashMap<>();metadata.findAllById(rows.map(ExersiceKeyword::getExerciseKeywordId).toList()).forEach(m->difficulties.put(m.getExerciseId(),m.getDifficulty()));
  return rows.map(q->question(q,difficulties.getOrDefault(q.getExerciseKeywordId(),"NORMAL")));
 }
 private Question question(ExersiceKeyword q,String difficulty){var l=q.getLessons();var b=l.getBook();return new Question(q.getExerciseKeywordId(),l.getLessonId(),l.getName(),b.getBookId(),b.getBookName(),b.getLevel().getLevelId(),b.getLevel().getLevelName(),q.getExerciseType().getExerciseTypeId(),q.getExerciseType().getName(),difficulty,q.getContentNihongo(),q.getAnswerA(),q.getAnswerB(),q.getAnswerC(),q.getAnswerD(),q.getCorrectAnswer());}
 @Transactional public void difficulty(Long id,String difficulty){
  if(difficulty==null||!List.of("EASY","NORMAL","HARD").contains(difficulty))throw bad("Độ khó không hợp lệ");
  if(em.find(ExersiceKeyword.class,id,LockModeType.PESSIMISTIC_WRITE)==null)throw missing();var m=metadata.findById(id).orElseGet(QuestionMetadata::new);String before=m.getDifficulty();m.setExerciseId(id);m.setDifficulty(difficulty);metadata.save(m);audit("QUESTION_DIFFICULTY","question:"+id,before+" → "+difficulty);
 }
 @Transactional public Exam createExam(String owner,NewExam input){
  if(new HashSet<>(input.questionIds()).size()!=input.questionIds().size())throw bad("Danh sách câu hỏi bị trùng");
  Map<Long,ExersiceKeyword> source=new HashMap<>();questions.findForExam(input.questionIds()).forEach(q->source.put(q.getExerciseKeywordId(),q));
  if(source.size()!=input.questionIds().size())throw bad("Một số câu hỏi đã bị xóa");
  Map<Long,String> difficulties=new HashMap<>();metadata.findAllById(input.questionIds()).forEach(m->difficulties.put(m.getExerciseId(),m.getDifficulty()));
  List<Question> snapshot=input.questionIds().stream().map(id->{var q=source.get(id);if(q.getCorrectAnswer()==null||!q.getCorrectAnswer().matches("[A-D]"))throw bad("Câu hỏi chưa có đáp án hợp lệ");return question(q,difficulties.getOrDefault(id,"NORMAL"));}).toList();
  var e=new ExamTemplate();e.setOwner(owner);e.setTitle(input.title().trim());e.setCreatedAt(LocalDateTime.now());try{e.setSnapshot(json.writeValueAsString(snapshot));}catch(Exception ex){throw new IllegalStateException(ex);}e=exams.save(e);audit("EXAM_CREATE","exam:"+e.getId(),e.getTitle()+" / "+snapshot.size()+" câu");return exam(e);
 }
 public Page<Exam> exams(int page){return exams.findAllByOrderByCreatedAtDescIdDesc(page(page)).map(this::exam);}
 public Exam exam(Long id){return exam(exams.findById(id).orElseThrow(ContentOperationsService::missing));}
 private Exam exam(ExamTemplate e){try{return new Exam(e.getId(),e.getTitle(),e.getOwner(),e.getCreatedAt(),json.readValue(e.getSnapshot(),json.getTypeFactory().constructCollectionType(List.class,Question.class)));}catch(Exception ex){throw new IllegalStateException(ex);}}
 public Page<ContentOperationAudit> audit(int page){return audits.findAllByOrderByCreatedAtDescIdDesc(page(page));}
 private void audit(String action,String target,String detail){var a=new ContentOperationAudit();var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();a.setActor(auth!=null&&auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt?jwt.getSubject():"system");a.setAction(action);a.setTarget(target);a.setDetail(detail);a.setCreatedAt(LocalDateTime.now());audits.save(a);}
 private static ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
 private static ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
 private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy dữ liệu");}
}

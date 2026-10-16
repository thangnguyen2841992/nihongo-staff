package com.nihongo.staff.service;
import com.nihongo.staff.model.*;
import com.nihongo.staff.repository.ContentRevisionRepository;
import com.nihongo.staff.security.ContentHtml;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.LocalDateTime;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service @RequiredArgsConstructor
public class ContentRevisionService {
 private final EntityManager em;private final ObjectMapper json;private final ContentRevisionRepository revisions;
 public record History(String current,String token,Page<ContentRevision> revisions) {}
 private Class<?> type(String kind){return switch(kind){case "BOOK"->Books.class;case "LESSON"->Lessons.class;case "GRAMMAR"->Grammar.class;case "EXAMPLE"->Example.class;case "EXERCISE"->ExersiceKeyword.class;default->throw bad("Loại nội dung không hợp lệ");};}
 private Object resource(String kind,Long id){if(id==null||id<=0)throw bad("Mã nội dung không hợp lệ");var e=em.find(type(kind),id);if(e==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Nội dung không còn tồn tại");return e;}
 private Books book(Object e){if(e instanceof Books b)return b;if(e instanceof Lessons l)return l.getBook();if(e instanceof Grammar g)return g.getLessons().getBook();if(e instanceof Example x)return x.getGrammar().getLessons().getBook();return ((ExersiceKeyword)e).getLessons().getBook();}
 private String snapshot(Object e){
  Map<String,Object> fields=new LinkedHashMap<>();
  if(e instanceof Books b){fields.put("bookName",b.getBookName());fields.put("description",b.getDescription());fields.put("levelId",b.getLevel().getLevelId());fields.put("typeId",b.getTypes().getTypeId());}
  if(e instanceof Lessons l){fields.put("name",l.getName());fields.put("description",l.getDescription());fields.put("reading",l.getReading());fields.put("audioTrack",l.getAudioTrack());fields.put("audioImportId",l.getAudioImportId());fields.put("audioAssetId",l.getAudioAssetId());}
  if(e instanceof Grammar g){fields.put("title",g.getTitle());fields.put("description",g.getDescription());fields.put("imageUrl",g.getImageUrl());}
  if(e instanceof Example x){fields.put("nihongo",x.getNihongo());fields.put("vietnamese",x.getVietnamese());}
  if(e instanceof ExersiceKeyword q){fields.put("contentNihongo",q.getContentNihongo());fields.put("answerA",q.getAnswerA());fields.put("answerB",q.getAnswerB());fields.put("answerC",q.getAnswerC());fields.put("answerD",q.getAnswerD());fields.put("correctAnswer",q.getCorrectAnswer());fields.put("lessonId",q.getLessons().getLessonId());fields.put("exerciseTypeId",q.getExerciseType().getExerciseTypeId());fields.put("audioImportId",q.getAudioImportId());fields.put("audioAssetId",q.getAudioAssetId());}
  try{return json.writeValueAsString(fields);}catch(Exception ex){throw new IllegalStateException(ex);}
 }
 private String token(String snapshot){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(snapshot.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
 @Transactional public void checkpoint(String kind,Long id){capture(kind,id,"EDIT");}
 private Object capture(String kind,Long id,String action){
  Object e=resource(kind,id);Books b=book(e);em.refresh(b,LockModeType.PESSIMISTIC_WRITE);
  if(b.getPublicationStatus()!=PublicationStatus.DRAFT)throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ sửa hoặc khôi phục sách ở trạng thái bản nháp");
  if(e!=b)em.refresh(e,LockModeType.PESSIMISTIC_WRITE);
  var r=new ContentRevision();r.setKind(kind);r.setResourceId(id);r.setBookId(b.getBookId());r.setSnapshot(snapshot(e));r.setAction(action);r.setCreatedAt(LocalDateTime.now());
  var auth=SecurityContextHolder.getContext().getAuthentication();r.setActor(auth!=null&&auth.getPrincipal() instanceof Jwt jwt?jwt.getSubject():"system");revisions.save(r);return e;
 }
 @Transactional(readOnly=true) public History history(String kind,Long id,int page){if(page<0)throw bad("Trang không hợp lệ");String current=snapshot(resource(kind,id));return new History(current,token(current),revisions.findByKindAndResourceIdOrderByCreatedAtDescIdDesc(kind,id,PageRequest.of(page,20)));}
    @Transactional @CacheEvict(value={"books","lessonsByBook","grammarsByLesson","examplesByLesson","lessonGrammarContent"},allEntries=true)
 public void restore(String kind,Long id,Long revisionId,String expectedToken){
  var r=revisions.findById(revisionId).orElseThrow(()->bad("Không tìm thấy phiên bản"));
  if(!r.getKind().equals(kind)||!r.getResourceId().equals(id))throw bad("Phiên bản không thuộc nội dung này");
  Object e=capture(kind,id,"RESTORE");
  if(!token(snapshot(e)).equals(expectedToken))throw new ResponseStatusException(HttpStatus.CONFLICT,"Nội dung đã thay đổi; hãy tải lại trước khi khôi phục");
  try{
   var s=json.readTree(r.getSnapshot());
   if(e instanceof Books b){b.setBookName(s.path("bookName").asText());b.setDescription(text(s,"description"));b.setLevel(em.getReference(Levels.class,s.path("levelId").asLong()));b.setTypes(em.getReference(Types.class,s.path("typeId").asLong()));}
   if(e instanceof Lessons l){l.setName(s.path("name").asText());l.setDescription(text(s,"description"));l.setReading(ContentHtml.clean(text(s,"reading")));l.setAudioTrack(text(s,"audioTrack"));l.setAudioImportId(text(s,"audioImportId"));l.setAudioAssetId(text(s,"audioAssetId"));}
   if(e instanceof Grammar g){g.setTitle(s.path("title").asText());g.setDescription(ContentHtml.clean(text(s,"description")));g.setImageUrl(text(s,"imageUrl"));}
   if(e instanceof Example x){x.setNihongo(ContentHtml.clean(text(s,"nihongo")));x.setVietnamese(ContentHtml.clean(text(s,"vietnamese")));}
   if(e instanceof ExersiceKeyword q){
    Lessons target=em.find(Lessons.class,s.path("lessonId").asLong());if(target==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Bài gốc không còn tồn tại");em.refresh(target.getBook(),LockModeType.PESSIMISTIC_WRITE);if(target.getBook().getPublicationStatus()!=PublicationStatus.DRAFT)throw new ResponseStatusException(HttpStatus.CONFLICT,"Bài gốc không còn ở bản nháp");
    q.setLessons(target);q.setExerciseType(em.getReference(ExerciseType.class,s.path("exerciseTypeId").asLong()));q.setContentNihongo(ContentHtml.clean(text(s,"contentNihongo")));q.setAnswerA(ContentHtml.clean(text(s,"answerA")));q.setAnswerB(ContentHtml.clean(text(s,"answerB")));q.setAnswerC(ContentHtml.clean(text(s,"answerC")));q.setAnswerD(ContentHtml.clean(text(s,"answerD")));q.setCorrectAnswer(text(s,"correctAnswer"));q.setAudioImportId(text(s,"audioImportId"));q.setAudioAssetId(text(s,"audioAssetId"));
   }
   em.flush();
  }catch(ResponseStatusException ex){throw ex;}catch(Exception ex){throw new IllegalStateException("Không khôi phục được nội dung",ex);}
 }
 private String text(com.fasterxml.jackson.databind.JsonNode s,String key){var n=s.get(key);return n==null||n.isNull()?null:n.asText();}
 @Transactional(readOnly=true) public Page<ContentRevision> audit(int page){if(page<0)throw bad("Trang không hợp lệ");return revisions.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page,20));}
 private static ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
}

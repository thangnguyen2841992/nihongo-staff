package com.nihongo.staff.service;
import com.nihongo.staff.repository.IExerciseKeywordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service @RequiredArgsConstructor
public class ExerciseGradingService {
 private final IExerciseKeywordRepository exercises;
 private final BookAiSolutionService aiSolutions;
 /** Answers and explanations of one lesson; evicted with the other learner caches on every content write. */
 record AnswerKey(Map<Long,String> correctAnswers,Map<Long,BookAiSolutionService.Solution> solutions) {}
 private org.springframework.cache.CacheManager caches;
 @org.springframework.beans.factory.annotation.Autowired(required=false) public void setCacheManager(org.springframework.cache.CacheManager caches){this.caches=caches;}

 // Not transactional: a cached answer key grades a submission without touching the database.
 public Grade grade(Long lessonId,Map<Long,String> answers) {
  var key=answerKey(lessonId);
  if(answers==null || answers.size()>2000) throw invalid();
  var correctAnswers=key.correctAnswers();
  int correct=0;
  for(var entry:answers.entrySet()) {
   if(!correctAnswers.containsKey(entry.getKey()) || entry.getValue()==null || !entry.getValue().matches("[ABCD]")) throw invalid();
   if(entry.getValue().equals(correctAnswers.get(entry.getKey()))) correct++;
  }
  int total=correctAnswers.size();
  return new Grade(total,correct,total-correct,correctAnswers,key.solutions());
 }
 private AnswerKey answerKey(Long lessonId) {
  var cache=caches==null?null:caches.getCache("answerKeys");
  if(cache!=null&&lessonId!=null){var hit=cache.get(lessonId,AnswerKey.class);if(hit!=null)return hit;}
  var rows=lessonId==null?List.<com.nihongo.staff.model.ExersiceKeyword>of():exercises.findByLessons_LessonId(lessonId);
  if(rows.isEmpty()) throw invalid();
  var correctAnswers=new HashMap<Long,String>();
  var solutions=new HashMap<Long,BookAiSolutionService.Solution>();
  for(var row:rows) {
   String key=row.getCorrectAnswer();
   if(key==null||!key.matches("[ABCD]")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Bài tập chưa có đáp án hợp lệ");
   correctAnswers.put(row.getExerciseKeywordId(),key);
   var solution=aiSolutions.find(row);if(solution!=null)solutions.put(row.getExerciseKeywordId(),solution);
  }
  var key=new AnswerKey(Map.copyOf(correctAnswers),Map.copyOf(solutions));
  if(cache!=null) cache.put(lessonId,key);
  return key;
 }
 private ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Câu trả lời không hợp lệ hoặc bài tập trống"); }
 public record Grade(int totalQuestion,int correctCount,int wrongCount,Map<Long,String> correctAnswers,Map<Long,BookAiSolutionService.Solution> aiSolutions) {}
}

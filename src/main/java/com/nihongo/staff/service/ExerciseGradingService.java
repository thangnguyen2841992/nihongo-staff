package com.nihongo.staff.service;
import com.nihongo.staff.repository.IExerciseKeywordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service @RequiredArgsConstructor
public class ExerciseGradingService {
 private final IExerciseKeywordRepository exercises;
 @Transactional(readOnly=true)
 public Grade grade(Long lessonId,Map<Long,String> answers) {
  var rows=exercises.findByLessons_LessonId(lessonId);
  if(rows.isEmpty() || answers==null || answers.size()>2000) throw invalid();
  var correctAnswers=new HashMap<Long,String>();
  for(var row:rows) {
   String key=row.getCorrectAnswer();
   if(key==null||!key.matches("[ABCD]")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Bài tập chưa có đáp án hợp lệ");
   correctAnswers.put(row.getExerciseKeywordId(),key);
  }
  int correct=0;
  for(var entry:answers.entrySet()) {
   if(!correctAnswers.containsKey(entry.getKey()) || entry.getValue()==null || !entry.getValue().matches("[ABCD]")) throw invalid();
   if(entry.getValue().equals(correctAnswers.get(entry.getKey()))) correct++;
  }
  return new Grade(rows.size(),correct,rows.size()-correct,correctAnswers);
 }
 private ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Câu trả lời không hợp lệ hoặc bài tập trống"); }
 public record Grade(int totalQuestion,int correctCount,int wrongCount,Map<Long,String> correctAnswers) {}
}

package com.nihongo.staff.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.ExerciseKeywordDTO;
import com.nihongo.staff.model.ExersiceKeyword;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;

@Service
public class BookAiSolutionService {
 public record Solution(String correctAnswer, String contentNihongo, List<String> choices,
                        String explanation, String evidence, String source) {}
 private final Map<Long, Solution> solutions;
 public BookAiSolutionService(ObjectMapper mapper) throws IOException {
  try(var input=new ClassPathResource("imports/book-import/try-n4-ai-answers.json").getInputStream()) {
   solutions=Map.copyOf(mapper.readValue(input,new TypeReference<Map<Long,Solution>>(){}));
  }
 }
 public Solution find(ExerciseKeywordDTO q) {
  return find(q.getExerciseKeywordId(),q.getContentNihongo(),Arrays.asList(q.getAnswerA(),q.getAnswerB(),q.getAnswerC(),q.getAnswerD()),q.getCorrectAnswer());
 }
 public Solution find(ExersiceKeyword q) {
  return find(q.getExerciseKeywordId(),q.getContentNihongo(),Arrays.asList(q.getAnswerA(),q.getAnswerB(),q.getAnswerC(),q.getAnswerD()),q.getCorrectAnswer());
 }
 private Solution find(Long id,String content,List<String> choices,String key) {
  var solution=solutions.get(id);
  return solution!=null&&Objects.equals(solution.contentNihongo(),content)&&solution.choices().equals(choices)&&
    Objects.equals(solution.correctAnswer(),key==null?"":key)?solution:null;
 }
 // In a graded lesson, explanations are returned with the grade, never in the learner's initial fetch.
 public void attachForReading(List<ExerciseKeywordDTO> rows,boolean manager) {
  boolean practice=rows.stream().anyMatch(q->q.getCorrectAnswer()==null||!q.getCorrectAnswer().matches("[ABCD]"));
  rows.forEach(q->q.setAiSolution(manager||practice?find(q):null));
 }
}

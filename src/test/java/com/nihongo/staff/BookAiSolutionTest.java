package com.nihongo.staff;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.ExerciseKeywordDTO;
import com.nihongo.staff.service.BookAiSolutionService;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BookAiSolutionTest {
 private ExerciseKeywordDTO question() throws Exception {
  var m=new ObjectMapper();Map<Long,BookAiSolutionService.Solution> catalog;
  try(var input=new ClassPathResource("imports/book-import/try-n4-ai-answers.json").getInputStream()) {catalog=m.readValue(input,new TypeReference<>(){});}
  var entry=catalog.entrySet().stream().filter(e->!e.getValue().correctAnswer().isBlank()).findFirst().orElseThrow();var a=entry.getValue();
  return ExerciseKeywordDTO.builder().exerciseKeywordId(entry.getKey()).contentNihongo(a.contentNihongo()).answerA(a.choices().get(0)).answerB(a.choices().get(1)).answerC(a.choices().get(2)).answerD(a.choices().get(3)).correctAnswer(a.correctAnswer()).build();
 }
 @Test void learnerCannotReadSolutionsBeforeGradingButStaffCan()throws Exception {
  var service=new BookAiSolutionService(new ObjectMapper());var q=question();
  service.attachForReading(List.of(q),true);assertNotNull(q.getAiSolution());
  service.attachForReading(List.of(q),false);assertNull(q.getAiSolution());assertNotNull(service.find(q));
 }
 @Test void practiceMayShowAvailableSolutionsAndEditedQuestionsHideStaleSolutions()throws Exception {
  var service=new BookAiSolutionService(new ObjectMapper());var q=question();var pending=ExerciseKeywordDTO.builder().exerciseKeywordId(-1L).build();
  service.attachForReading(List.of(q,pending),false);assertNotNull(q.getAiSolution());
  q.setAnswerA("Edited");assertNull(service.find(q));q=question();q.setContentNihongo("Edited");assertNull(service.find(q));q=question();q.setCorrectAnswer(null);assertNull(service.find(q));
 }
}

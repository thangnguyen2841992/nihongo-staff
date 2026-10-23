package com.nihongo.staff;
import com.nihongo.staff.service.ExerciseGradingService;
import com.nihongo.staff.model.ExersiceKeyword;
import com.nihongo.staff.repository.IExerciseKeywordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ExerciseGradingTest {
 IExerciseKeywordRepository repo=mock(IExerciseKeywordRepository.class);
 ExerciseGradingService service=new ExerciseGradingService(repo,mock(com.nihongo.staff.service.BookAiSolutionService.class));
 void setup(){when(repo.findByLessons_LessonId(1L)).thenReturn(List.of(
  ExersiceKeyword.builder().exerciseKeywordId(10L).correctAnswer("A").build(),
  ExersiceKeyword.builder().exerciseKeywordId(11L).correctAnswer("B").build()));}
 @Test void serverCountsUnansweredAsWrong(){setup();var grade=service.grade(1L,Map.of(10L,"A"));assertEquals(2,grade.totalQuestion());assertEquals(1,grade.correctCount());assertEquals(1,grade.wrongCount());}
 @Test void rejectsQuestionsFromAnotherLessonAndInvalidChoices(){setup();assertThrows(ResponseStatusException.class,()->service.grade(1L,Map.of(99L,"A")));assertThrows(ResponseStatusException.class,()->service.grade(1L,Map.of(10L,"Z")));}
 @Test void emptyLessonCannotProduceNaNScore(){when(repo.findByLessons_LessonId(1L)).thenReturn(List.of());assertThrows(ResponseStatusException.class,()->service.grade(1L,Map.of()));}
 @Test void answerKeyIsReusedUntilTheCacheIsEvicted(){setup();var caches=new org.springframework.cache.caffeine.CaffeineCacheManager("answerKeys");service.setCacheManager(caches);
  assertEquals(1,service.grade(1L,Map.of(10L,"A")).correctCount());assertEquals(1,service.grade(1L,Map.of(11L,"B")).correctCount());
  verify(repo,times(1)).findByLessons_LessonId(1L);
  caches.getCache("answerKeys").clear();service.grade(1L,Map.of());verify(repo,times(2)).findByLessons_LessonId(1L);}
}

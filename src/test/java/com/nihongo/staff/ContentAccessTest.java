package com.nihongo.staff;
import com.nihongo.staff.security.*;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.model.*;
import com.nihongo.staff.model.dto.ContentLocationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ContentAccessTest {
 IBookRepository books=mock(IBookRepository.class); ILessonsRepository lessons=mock(ILessonsRepository.class);
 IGrammarRepository grammars=mock(IGrammarRepository.class); SubscriptionClient subscriptions=mock(SubscriptionClient.class);
 ContentAccess access=new ContentAccess(books,lessons,grammars,subscriptions);
 org.springframework.security.core.Authentication auth(String role){return new UsernamePasswordAuthenticationToken("u","",List.of(new SimpleGrantedAuthority("ROLE_"+role)));}
 @Test void bookLevelRequiresCurrentSubscription(){
  when(books.findLocationById(1L)).thenReturn(Optional.of(new ContentLocationResponse(1L,3L,"Book")));
  when(subscriptions.hasAccess(3L)).thenReturn(false);assertFalse(access.book(1L,auth("USER")));
  when(subscriptions.hasAccess(3L)).thenReturn(true);assertTrue(access.book(1L,auth("USER")));
 }
 @Test void lessonLevelRequiresCurrentSubscription(){
  when(lessons.findLocationById(2L)).thenReturn(Optional.of(new ContentLocationResponse(1L,3L,"Lesson")));
  when(subscriptions.hasAccess(3L)).thenReturn(false);assertFalse(access.lesson(2L,auth("USER")));
  when(subscriptions.hasAccess(3L)).thenReturn(true);assertTrue(access.lesson(2L,auth("USER")));
 }
 @Test void managersCanEditWithoutBuyingAndMissingContentIsDenied(){
  assertTrue(access.lesson(1L,auth("STAFF")));verifyNoInteractions(subscriptions);
  assertFalse(access.lesson(100L,auth("USER")));assertFalse(access.level(3L,null));
 }
 @Test void subscriptionServiceFailureNeverGrantsAccess(){
  when(subscriptions.hasAccess(3L)).thenThrow(new IllegalStateException("offline"));
  assertThrows(IllegalStateException.class,()->access.level(3L,auth("USER")));
 }
}

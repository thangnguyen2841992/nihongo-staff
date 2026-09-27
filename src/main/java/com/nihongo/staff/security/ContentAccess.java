package com.nihongo.staff.security;
import com.nihongo.staff.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
@Component("contentAccess") @RequiredArgsConstructor @Transactional(readOnly=true)
public class ContentAccess {
 private final IBookRepository books;
 private final ILessonsRepository lessons;
 private final IGrammarRepository grammars;
 private final SubscriptionClient subscriptions;
 public boolean manager(Authentication auth) { return auth!=null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")||a.getAuthority().equals("ROLE_STAFF")); }
 public boolean level(Long id,Authentication auth) {
  if(manager(auth)) return true;
  if(id==null || auth==null || !auth.isAuthenticated()) return false;
  return Boolean.TRUE.equals(subscriptions.hasAccess(id));
 }
 public boolean book(Long id,Authentication auth) {
  if(manager(auth)) return true;
  return id!=null && books.findById(id).map(b->level(b.getLevel().getLevelId(),auth)).orElse(false);
 }
 public boolean lesson(Long id,Authentication auth) {
  if(manager(auth)) return true;
  return id!=null && lessons.findById(id).map(l->level(l.getBook().getLevel().getLevelId(),auth)).orElse(false);
 }
 public boolean grammar(Long id,Authentication auth) {
  if(manager(auth)) return true;
  return id!=null && grammars.findById(id).map(g->lesson(g.getLessons().getLessonId(),auth)).orElse(false);
 }
}

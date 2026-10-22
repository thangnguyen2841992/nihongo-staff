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
 // Positive entitlement answers are reused briefly: one learner page load otherwise asks nihongo-user several times.
 // Denials and failures are never cached, so a new purchase works immediately; revocation applies within the TTL.
 private final com.github.benmanes.caffeine.cache.Cache<String,Boolean> granted=com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
   .maximumSize(20_000).expireAfterWrite(java.time.Duration.ofSeconds(30)).build();
 public boolean manager(Authentication auth) { return auth!=null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")||a.getAuthority().equals("ROLE_STAFF")); }
 public boolean level(Long id,Authentication auth) {
  if(manager(auth)) return true;
  if(id==null || auth==null || !auth.isAuthenticated()) return false;
  String key=auth.getName()+":"+id;
  if(granted.getIfPresent(key)!=null) return true;
  boolean allowed=Boolean.TRUE.equals(subscriptions.hasAccess(id));
  if(allowed) granted.put(key,Boolean.TRUE);
  return allowed;
 }
 public boolean book(Long id,Authentication auth) {
  if(manager(auth)) return true;
  return id!=null && books.findById(id).map(b ->
          (b.getPublicationStatus()==null || b.getPublicationStatus()==com.nihongo.staff.model.PublicationStatus.PUBLISHED)
          && level(b.getLevel().getLevelId(),auth)).orElse(false);
 }
 public boolean lesson(Long id,Authentication auth) {
  if(manager(auth)) return true;
  return id!=null && lessons.findLocationById(id).map(l->book(l.bookId(),auth)).orElse(false);
 }
 public boolean grammar(Long id,Authentication auth) {
  if(manager(auth)) return true;
  return id!=null && grammars.findById(id).map(g->lesson(g.getLessons().getLessonId(),auth)).orElse(false);
 }
}

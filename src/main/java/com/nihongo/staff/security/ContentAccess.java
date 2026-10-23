package com.nihongo.staff.security;
import com.nihongo.staff.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import java.util.Optional;
import java.util.function.Supplier;
// Not transactional: an access check must never hold a pooled connection while asking nihongo-user.
@Component("contentAccess") @RequiredArgsConstructor
public class ContentAccess {
 private final IBookRepository books;
 private final ILessonsRepository lessons;
 private final IGrammarRepository grammars;
 private final SubscriptionClient subscriptions;
 // Positive entitlement answers are reused briefly: one learner page load otherwise asks nihongo-user several times.
 // Denials and failures are never cached, so a new purchase works immediately; revocation applies within the TTL.
 private final com.github.benmanes.caffeine.cache.Cache<String,Boolean> granted=com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
   .maximumSize(20_000).expireAfterWrite(java.time.Duration.ofSeconds(30)).build();
 /** Where a book sits and whether learners may see it; changes only through content writes, which evict it. */
 public record BookAccess(Long levelId, boolean published) {}
 // Content structure lookups (book → level/status, lesson → book, grammar → lesson) live in the shared content
 // caches: every content write evicts them (ContentCacheConfig.LEARNER_CACHES), unpublishing included.
 private CacheManager caches;
 @org.springframework.beans.factory.annotation.Autowired(required=false) public void setCacheManager(CacheManager caches){this.caches=caches;}

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
  return id!=null && cached("bookAccess",id,()->books.findById(id).map(b->new BookAccess(b.getLevel().getLevelId(),
          b.getPublicationStatus()==null || b.getPublicationStatus()==com.nihongo.staff.model.PublicationStatus.PUBLISHED)))
          .map(b->b.published() && level(b.levelId(),auth)).orElse(false);
 }
 public boolean lesson(Long id,Authentication auth) {
  if(manager(auth)) return true;
  return id!=null && cached("lessonBook",id,()->lessons.findLocationById(id).map(l->l.bookId())).map(bookId->book(bookId,auth)).orElse(false);
 }
 public boolean grammar(Long id,Authentication auth) {
  if(manager(auth)) return true;
  return id!=null && cached("grammarLesson",id,()->grammars.findById(id).map(g->g.getLessons().getLessonId())).map(lessonId->lesson(lessonId,auth)).orElse(false);
 }
 // Missing content is not cached (a later create must not be hidden); found values are.
 @SuppressWarnings("unchecked")
 private <T> Optional<T> cached(String name,Long key,Supplier<Optional<T>> loader) {
  Cache cache=caches==null?null:caches.getCache(name);
  if(cache!=null){var hit=cache.get(key);if(hit!=null&&hit.get()!=null)return Optional.of((T)hit.get());}
  Optional<T> loaded=loader.get();
  if(cache!=null) loaded.ifPresent(value->cache.put(key,value));
  return loaded;
 }
}

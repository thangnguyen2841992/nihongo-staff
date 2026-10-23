package com.nihongo.staff.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContentCacheConfig {
    /**
     * Caches serving learner reads and access checks. Every content write evicts all of them (see
     * ContentCacheEvictionTest), so they never need their own invalidation rules.
     */
    public static final java.util.List<String> LEARNER_CACHES = java.util.List.of(
            "bookAccess", "lessonBook", "grammarLesson", "bookDetail", "booksByLevel", "lessonById",
            "bookLocation", "lessonLocation", "exercisesByLesson", "answerKeys");

    @Bean
    public CacheManager cacheManager(
            @Value("${content-cache.maximum-size:2000}") long maximumSize,
            @Value("${content-cache.expire-after-write:PT5M}") Duration expireAfterWrite) {
        var names = new java.util.ArrayList<>(java.util.List.of("books", "types", "levels", "exerciseTypes", "lessonsByBook",
                "grammarsByLesson", "examplesByLesson", "lessonGrammarContent"));
        names.addAll(LEARNER_CACHES);
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(names.toArray(String[]::new));
        cacheManager.setAllowNullValues(false);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWrite));

        return new TransactionAwareCacheManagerProxy(cacheManager);
    }
}

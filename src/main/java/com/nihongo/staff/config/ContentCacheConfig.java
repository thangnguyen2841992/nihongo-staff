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
    @Bean
    public CacheManager cacheManager(
            @Value("${content-cache.maximum-size:2000}") long maximumSize,
            @Value("${content-cache.expire-after-write:PT5M}") Duration expireAfterWrite) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "books", "types", "levels", "exerciseTypes", "lessonsByBook",
                "grammarsByLesson", "examplesByLesson", "lessonGrammarContent");
        cacheManager.setAllowNullValues(false);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWrite));

        return new TransactionAwareCacheManagerProxy(cacheManager);
    }
}

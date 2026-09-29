package com.nihongo.staff.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContentCacheConfig {
    @Bean
    public CacheManager cacheManager() {
        return new TransactionAwareCacheManagerProxy(
                new ConcurrentMapCacheManager("books", "types", "levels", "exerciseTypes"));
    }
}

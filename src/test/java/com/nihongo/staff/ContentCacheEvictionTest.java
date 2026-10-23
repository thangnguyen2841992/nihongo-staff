package com.nihongo.staff;

import com.nihongo.staff.config.ContentCacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Learner caches (access checks, book/lesson detail, exercises, answer keys) rely on every content write evicting them. */
class ContentCacheEvictionTest {
    static final List<Class<?>> WRITERS = List.of(
            com.nihongo.staff.service.StaffServiceImpl.class,
            com.nihongo.staff.service.ContentRevisionService.class,
            com.nihongo.staff.service.BookPublicationService.class,
            com.nihongo.staff.service.ContentOperationsService.class,
            com.nihongo.staff.service.imports.BookImportService.class,
            com.nihongo.staff.service.imports.TryN3ImportService.class,
            com.nihongo.staff.service.imports.TryN3BookImportService.class);

    @Test void everyContentEvictionAlsoEvictsAllLearnerCaches() {
        int checked = 0;
        for (Class<?> type : WRITERS) for (Method method : type.getDeclaredMethods()) {
            CacheEvict evict = method.getAnnotation(CacheEvict.class);
            if (evict == null) continue;
            checked++;
            var names = new HashSet<>(List.of(evict.value()));
            names.addAll(List.of(evict.cacheNames()));
            assertTrue(evict.allEntries(), type.getSimpleName() + "." + method.getName());
            for (String cache : ContentCacheConfig.LEARNER_CACHES)
                assertTrue(names.contains(cache), type.getSimpleName() + "." + method.getName() + " must evict " + cache);
        }
        assertTrue(checked >= 20, "expected the content write methods to be found, got " + checked);
    }

    @Test void everyStaffContentWriteEvicts() {
        for (Method method : com.nihongo.staff.service.StaffServiceImpl.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) continue;
            if (!method.getName().matches("(create|update|delete).*")) continue;
            assertNotNull(method.getAnnotation(CacheEvict.class), "StaffServiceImpl." + method.getName() + " writes content without evicting caches");
        }
    }
}

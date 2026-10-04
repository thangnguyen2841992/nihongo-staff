package com.nihongo.staff.controller;

import com.nihongo.staff.service.imports.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/staff/imports/try-n3/book")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')") @RequiredArgsConstructor
public class TryN3BookImportController {
    private final TryN3BookImportService service;
    private final TryN3BookData data;
    @GetMapping public TryN3BookImportService.Preview preview() { return service.preview(); }
    @GetMapping("/chapters/{number}") public Object draft(@PathVariable int number) throws Exception { return service.draft(number); }
    @PutMapping("/chapters/{number}/review")
    public TryN3BookImportService.Preview review(@PathVariable int number, @RequestBody TryN3BookImportService.Review request) throws Exception { return service.review(number, request); }
    @PostMapping("/chapters/{number}/import")
    public TryN3BookImportService.Preview importChapter(@PathVariable int number) throws Exception { return service.importChapter(number); }
    @GetMapping("/pages/{page}")
    public ResponseEntity<Resource> page(@PathVariable int page) {
        boolean indexed = data.book().answerPdfPages().contains(page) || data.book().chapters().stream().anyMatch(c -> c.sourcePdfPages().contains(page));
        if (!indexed) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(new ClassPathResource("imports/try-n3/pages/" + page + ".png"));
    }
}

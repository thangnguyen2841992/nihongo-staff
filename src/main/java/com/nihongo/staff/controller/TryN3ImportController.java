package com.nihongo.staff.controller;

import com.nihongo.staff.service.imports.TryN3ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/staff/imports/try-n3/chapter-1")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
public class TryN3ImportController {
    private final TryN3ImportService service;

    @GetMapping
    public TryN3ImportService.Preview preview() { return service.preview(); }

    @PostMapping
    public ResponseEntity<TryN3ImportService.Result> importChapter() {
        try {
            var result = service.importChapter();
            return ResponseEntity.status(result.alreadyImported() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Có thao tác nhập đồng thời hoặc dữ liệu chưa hợp lệ. Vui lòng tải lại để kiểm tra.");
        }
    }

    @GetMapping("/pages/{page}")
    public ResponseEntity<Resource> sourcePage(@PathVariable int page) {
        if (page < 15 || page > 29) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(new ClassPathResource("imports/try-n3/pages/" + page + ".png"));
    }
}

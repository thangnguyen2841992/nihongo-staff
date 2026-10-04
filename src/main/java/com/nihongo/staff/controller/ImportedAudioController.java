package com.nihongo.staff.controller;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.imports.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/staff/imported-audio") @RequiredArgsConstructor
public class ImportedAudioController {
    private final ILessonsRepository lessons;private final IExerciseKeywordRepository exercises;private final BookImportService service;private final BookImportStorage storage;
    @GetMapping("/lessons/{id}") @PreAuthorize("@contentAccess.lesson(#id,authentication)")
    public ResponseEntity<Resource> lesson(@PathVariable Long id) {
        var l=lessons.findById(id).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));return audio(l.getAudioImportId(),l.getAudioAssetId());
    }
    @GetMapping("/exercises/{id}") @PreAuthorize("@importedAudioAccess.exercise(#id,authentication)")
    public ResponseEntity<Resource> exercise(@PathVariable Long id) {
        var q=exercises.findById(id).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));return audio(q.getAudioImportId(),q.getAudioAssetId());
    }
    private ResponseEntity<Resource> audio(String session,String asset) {
        if(session==null || asset==null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        var a=service.asset(session,asset);return ResponseEntity.ok().contentType(MediaType.parseMediaType(a.mediaType())).cacheControl(CacheControl.noStore()).body(new FileSystemResource(storage.audio(session,a)));
    }
}

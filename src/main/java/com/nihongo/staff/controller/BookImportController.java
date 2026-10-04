package com.nihongo.staff.controller;
import com.nihongo.staff.service.imports.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.util.List;

@RestController @RequestMapping("/api/staff/book-imports") @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @RequiredArgsConstructor
public class BookImportController {
    private final BookImportService service;private final BookImportProcessor processor;private final BookImportStorage storage;private final BookImportAutomation automation;
    private final BookImportQuestionAssistant questionAssistant;
    @PostMapping("/{id}/assist-question") public BookImportQuestionAssistant.Suggestion assistQuestion(@PathVariable String id,@RequestBody BookImportQuestionAssistant.Request request) {return questionAssistant.suggest(id,request);}
    @GetMapping("/capabilities") public java.util.Map<String,Boolean> capabilities() {return java.util.Map.of("geminiConfigured",automation.configured());}
    @GetMapping public List<BookImportService.Summary> list() { return service.list(); }
    @GetMapping("/{id}") public BookImportService.Detail get(@PathVariable String id) { return service.get(id); }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookImportService.Detail> create(@RequestPart MultipartFile pdf,@RequestParam String bookName,@RequestParam Long levelId,@RequestParam Long typeId,@RequestParam(defaultValue="false") boolean automatic) throws Exception {
        var result=service.create(pdf,bookName,levelId,typeId,automatic);if("QUEUED".equals(result.summary().state())) processor.start(result.summary().id());return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
    @PostMapping("/{id}/automate") public BookImportService.Detail automate(@PathVariable String id,@RequestBody BookImportService.Publish request) {var d=service.automate(id,request);processor.start(id);return d;}
    @PostMapping("/{id}/manual") public BookImportService.Detail manual(@PathVariable String id,@RequestBody BookImportService.Publish request) {return service.manual(id,request);}
    @PostMapping(value="/{id}/audio",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public BookImportService.Detail audio(@PathVariable String id,@RequestPart List<MultipartFile> files) throws Exception { return service.addAudio(id,files); }
    @PutMapping("/{id}") public BookImportService.Detail save(@PathVariable String id,@RequestBody BookImportService.Save request) throws Exception { return service.save(id,request); }
    @PostMapping("/{id}/publish") public BookImportService.Detail publish(@PathVariable String id,@RequestBody BookImportService.Publish request) { return service.publish(id,request); }
    @PostMapping("/{id}/retry") public BookImportService.Detail retry(@PathVariable String id) { service.get(id);processor.retry(id);return service.get(id); }
    @GetMapping("/{id}/pages/{page}") public ResponseEntity<Resource> page(@PathVariable String id,@PathVariable int page) {
        var s=service.find(id);if(page<1 || page>s.getPageCount()) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        var path=storage.image(id,page);if(!Files.exists(path)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Trang sách chưa được xử lý.");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(new FileSystemResource(path));
    }
    @GetMapping("/{id}/audio/{assetId}") public ResponseEntity<Resource> audio(@PathVariable String id,@PathVariable String assetId) {
        var a=service.asset(id,assetId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(a.mediaType())).cacheControl(CacheControl.noStore()).body(new FileSystemResource(storage.audio(id,a)));
    }
}

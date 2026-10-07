package com.nihongo.staff.controller;

import com.nihongo.staff.service.BookPublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/staff/books/{bookId}/publication") @RequiredArgsConstructor
public class BookPublicationController {
    private final BookPublicationService publication;
    @GetMapping @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public BookPublicationService.Review inspect(@PathVariable Long bookId) { return publication.inspect(bookId); }
    @PostMapping("/submit") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public BookPublicationService.Review submit(@PathVariable Long bookId) { return publication.submit(bookId); }
    @PostMapping("/publish") @PreAuthorize("hasRole('ADMIN')")
    public BookPublicationService.Review publish(@PathVariable Long bookId) { return publication.publish(bookId); }
    @PostMapping("/draft") @PreAuthorize("hasRole('ADMIN')")
    public BookPublicationService.Review draft(@PathVariable Long bookId) { return publication.returnToDraft(bookId); }
}

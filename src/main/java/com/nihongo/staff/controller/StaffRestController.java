package com.nihongo.staff.controller;

import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
import com.nihongo.staff.model.dto.*;
import com.nihongo.staff.service.IStaffService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
public class StaffRestController {
    private final IStaffService staffService;

    public StaffRestController(IStaffService staffService) {
        this.staffService = staffService;
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @GetMapping("/types")
    public ResponseEntity<List<Types>> getAllTypes() {
        return ResponseEntity.ok(this.staffService.getTypes());
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @GetMapping("/levels")
    public ResponseEntity<List<Levels>> getAllLevels() {
        return ResponseEntity.ok(this.staffService.getLevels());
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @GetMapping("/books")
    public ResponseEntity<List<BookResponse>> getAllBooks() {
        return ResponseEntity.ok(this.staffService.getBooks());
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @GetMapping("/getBooksByLevelAndType")
    public ResponseEntity<List<BookResponse>> getBooksByLevelAndType(@RequestParam Long levelId, @RequestParam Long typeId) {
        return ResponseEntity.ok(this.staffService.getBooksByLevelAndType(levelId, typeId));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/books")
    public ResponseEntity<BookResponse> createNewBook(@RequestBody CreateNewBookRequest bookRequest) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(this.staffService.createNewBook(bookRequest));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PutMapping("/books")
    public ResponseEntity<BookResponse> updateBook(@RequestBody UpdateBookRequest bookRequest) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(this.staffService.updateBook(bookRequest));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping("/images")
    public ResponseEntity<List<ImageDTO>> updateImagesOfBook(@RequestBody UpdateImageOfBookRequest imageOfBookRequest) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(this.staffService.updateImagesOfBooks(imageOfBookRequest));
    }
}

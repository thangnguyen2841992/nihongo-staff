package com.nihongo.staff.controller;

import com.nihongo.staff.model.Books;
import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
import com.nihongo.staff.service.IStaffService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<List<Books>> getAllBooks() {
        return ResponseEntity.ok(this.staffService.getBooks());
    }
}

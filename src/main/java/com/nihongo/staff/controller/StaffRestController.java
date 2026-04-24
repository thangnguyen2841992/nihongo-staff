package com.nihongo.staff.controller;

import com.nihongo.staff.service.IStaffService;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffRestController {
    private final IStaffService staffService;

    public StaffRestController(IStaffService staffService) {
        this.staffService = staffService;
    }
}

package com.nihongo.staff.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CreateNewBookRequest {
    private Long typeId;

    private Long levelId;

    private String bookName;

    private List<String> urls;

}

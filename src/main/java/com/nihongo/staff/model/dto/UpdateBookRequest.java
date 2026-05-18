package com.nihongo.staff.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UpdateBookRequest {
    private  Long bookId;

    private String bookName;

    private Long levelId;

    private Long typeId;
}

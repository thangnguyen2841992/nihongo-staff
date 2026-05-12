package com.nihongo.staff.model.dto;

import com.nihongo.staff.model.Images;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BookResponse {

    private Long bookId;

    private String bookName;

    private List<ImageDTO> imageUrls;

    private String typeName;

    private String levelName;
}
package com.nihongo.staff.model.dto;

import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
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
    private Types types;

    private Levels levels;

    private String bookName;

    private List<String> urls;

}

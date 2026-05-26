package com.nihongo.staff.model.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ExampleRequest {
    private Long exampleId;

    private String nihongo;

    private String vietnamese;

    private Long grammarId;
}

package com.nihongo.staff.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class LessonResponse {
    private Long lessonId;

    private Long bookId;

    private String name;

    private String description;

    private String reading;
    private String audioTrack;
    private String audioUrl;
    /** Labelled listening tracks of the lesson (ことば, 文型, 例文, 練習 …); empty when the lesson has none. */
    private java.util.List<Track> tracks = java.util.List.of();

    public record Track(Long id, String label, String url) {}
}

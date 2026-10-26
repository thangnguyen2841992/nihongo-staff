package com.nihongo.staff.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One of several labelled listening tracks of a lesson (e.g. Minna no Nihongo: ことば, 文型, 例文, 練習 A1…C3).
 * The audio file stays in the book import storage; this row only points at it.
 */
@Entity
@Table(name = "lesson_audio_track", indexes = @Index(name = "idx_lesson_audio_track_lesson", columnList = "lesson_id, position"))
@Getter
@Setter
@NoArgsConstructor
public class LessonAudioTrack {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "audio_import_id", nullable = false, length = 36)
    private String audioImportId;

    @Column(name = "audio_asset_id", nullable = false, length = 36)
    private String audioAssetId;
}

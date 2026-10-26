package com.nihongo.staff.repository;

import com.nihongo.staff.model.LessonAudioTrack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LessonAudioTrackRepository extends JpaRepository<LessonAudioTrack, Long> {
    List<LessonAudioTrack> findByLessonIdOrderByPositionAsc(Long lessonId);
    List<LessonAudioTrack> findByLessonIdInOrderByLessonIdAscPositionAsc(Collection<Long> lessonIds);
}

package com.nihongo.staff.security;
import com.nihongo.staff.repository.IExerciseKeywordRepository;
import com.nihongo.staff.repository.LessonAudioTrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
@Component("importedAudioAccess") @RequiredArgsConstructor
public class ImportedAudioAccess {
    private final IExerciseKeywordRepository exercises;private final LessonAudioTrackRepository tracks;private final ContentAccess access;
    @Transactional(readOnly=true) public boolean exercise(Long id,Authentication auth) {
        return id!=null && exercises.findById(id).map(q->access.lesson(q.getLessons().getLessonId(),auth)).orElse(false);
    }
    @Transactional(readOnly=true) public boolean lessonTrack(Long id,Authentication auth) {
        return id!=null && tracks.findById(id).map(t->access.lesson(t.getLessonId(),auth)).orElse(false);
    }
}

package com.nihongo.staff.config;

import com.nihongo.staff.model.ExerciseType;
import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
import com.nihongo.staff.repository.IExerciseTypeRepository;
import com.nihongo.staff.repository.ILevelsRepository;
import com.nihongo.staff.repository.ITypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {
    private final ITypeRepository typeRepository;

    private final ILevelsRepository levelsRepository;

    private final IExerciseTypeRepository exerciseTypeRepository;


    @Bean
    CommandLineRunner initData() {
        return args -> {

            // 🔥 LEVELS
            List<String> levels = List.of("N1", "N2", "N3", "N4", "N5");

            for (String lvl : levels) {
                if (!levelsRepository.existsByLevelName(lvl)) {
                    levelsRepository.save(new Levels(null, lvl, null, null));
                }
            }

            // 🔥 TYPES
            List<String> types = List.of(
                    "Từ Vựng",
                    "Ngữ Pháp",
                    "Kanji",
                    "Đọc Hiểu",
                    "Nghe Hiểu"
            );

            for (String t : types) {
                if (!typeRepository.existsByTypeName(t)) {
                    typeRepository.save(new Types(null, t, null, null));
                }
            }

            //Exercise Type
            List<String> exerciseTypes = List.of(
                    "＿＿＿のことばはひらがなでどう書きますか。1・2・3・4から一番いいものを一つ選んでください。",
                    "＿＿＿のことばはどう書きますか。1・2・3・4から一番いいものを一つ選んでください。",
                    "(____)に なにを いれますか。1・2・3・4から 一番 いい ものを 一つ えらんでください。",
                    "___の ぶんと だいたい おなじ いみの ぶんが あります。1・2・3・4から 一番 いいものを 一つ えらんで ください",
                    "つぎの ことばの つかいかたで 一番 いい ものを 1・2・3・4 から 一つえらんでください。"
            );

            for (String t : exerciseTypes) {
                if (!this.exerciseTypeRepository.existsByName(t)) {
                    this.exerciseTypeRepository.save(new ExerciseType(null, t, null, null));
                }
            }
        };
    }
}

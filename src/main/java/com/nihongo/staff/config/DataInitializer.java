package com.nihongo.staff.config;

import com.nihongo.staff.model.Levels;
import com.nihongo.staff.model.Types;
import com.nihongo.staff.repository.ILevelsRepository;
import com.nihongo.staff.repository.ITypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DataInitializer {
    private final ITypeRepository typeRepository;

    private final ILevelsRepository levelsRepository;

    public DataInitializer(ITypeRepository typeRepository, ILevelsRepository levelsRepository) {
        this.typeRepository = typeRepository;
        this.levelsRepository = levelsRepository;
    }


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
        };
    }
}

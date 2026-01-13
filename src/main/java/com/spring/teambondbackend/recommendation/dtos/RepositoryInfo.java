package com.spring.teambondbackend.recommendation.dtos;
// For the first api i.e get all repos is handled by this
import lombok.*;

import java.util.List;
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class RepositoryInfo {
    private String name;
    private String defaultBranch;
    private List<String> commitShas;
    private List<Language> topLanguages;

    @Getter
    @Setter
    public static class Language {
        private String name;
        private long size;

        // Constructor
        public Language(String name, long size) {
            this.name = name;
            this.size = size;
        }
    }
}

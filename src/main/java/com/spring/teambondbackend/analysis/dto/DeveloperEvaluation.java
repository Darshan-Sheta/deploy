package com.spring.teambondbackend.analysis.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DeveloperEvaluation {
    private String developer_type;
    private List<SkillLevel> levels_array;
    private Map<String, String> levels;
    private Scores scores;
    private Analysis analysis;

    @Data
    public static class SkillLevel {
        private String skill;
        private String level;
    }

    @Data
    public static class Scores {
        private int skill;
        private int consistency;
        private int hackathon_fit;
    }

    @Data
    public static class Analysis {
        private List<String> languages;
        private List<String> frameworks;
        private List<String> libraries;
        private List<String> tools;
        private List<String> technologies;
        private String repo_breakdown;
        private List<String> project_types;
        private List<String> tech_stack_summary;
    }
}

package com.spring.teambondbackend.recommendation.dtos;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter @Setter @ToString @AllArgsConstructor @NoArgsConstructor
public  class FrameworkResult {
    private List<String> frameworks;
    private Map<String, Integer> frameworkFileCounts;
}

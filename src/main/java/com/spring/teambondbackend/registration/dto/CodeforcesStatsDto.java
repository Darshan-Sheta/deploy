package com.spring.teambondbackend.registration.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeforcesStatsDto {
    private String currentRating;
    private String maxRating;
    private String rank; // e.g., "Specialist"
    private String maxRank;
}

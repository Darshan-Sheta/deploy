package com.spring.teambondbackend.registration.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeChefStatsDto {
    private String stars;
    private String currentRating;
    private String highestRating;
    private String globalRank;
    private String countryRank;
    private String countryFlag;
}

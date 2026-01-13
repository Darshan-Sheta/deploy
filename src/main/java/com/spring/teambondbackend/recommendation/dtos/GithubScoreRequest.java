package com.spring.teambondbackend.recommendation.dtos;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public  class GithubScoreRequest {
    private String username;
    private String email;
    private String accessToken;
}

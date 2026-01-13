package com.spring.teambondbackend.analysis.controller;

import com.spring.teambondbackend.analysis.dto.DeveloperEvaluation;
import com.spring.teambondbackend.analysis.service.GithubAnalysisService;
import com.spring.teambondbackend.registration.model.User;
import com.spring.teambondbackend.registration.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class GithubAnalysisController {

    private final GithubAnalysisService githubAnalysisService;
    private final com.spring.teambondbackend.recommendation.services.FrameworkAnalysisService frameworkAnalysisService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/github/{username}")
    public ResponseEntity<?> analyzeGithubProfile(@PathVariable String username) {
        try {
            // First, check if analysis exists in database
            Optional<User> userOpt = userRepository.findByGithubUsername(username);
            
            if (userOpt.isPresent() && userOpt.get().getGeminiAnalysis() != null 
                    && !userOpt.get().getGeminiAnalysis().trim().isEmpty()) {
                // Return cached analysis from database
                System.out.println("Returning cached Gemini analysis for: " + username);
                DeveloperEvaluation cachedEval = objectMapper.readValue(
                    userOpt.get().getGeminiAnalysis(), 
                    DeveloperEvaluation.class
                );
                return ResponseEntity.ok(cachedEval);
            }

            // If not found in DB, generate new analysis
            System.out.println("Generating new Gemini analysis for: " + username);
            DeveloperEvaluation evaluation = githubAnalysisService.analyzeDeveloper(username);
            
            // Store in database if user exists
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                String analysisJson = objectMapper.writeValueAsString(evaluation);
                user.setGeminiAnalysis(analysisJson);
                userRepository.save(user);
                System.out.println("Stored Gemini analysis in database for: " + username);

                 // Also update Framework Stats for Recommendations
                 if (user.getGithubAccessToken() != null && !user.getGithubAccessToken().isEmpty()) {
                     try {
                         com.spring.teambondbackend.recommendation.dtos.GithubScoreRequest scoreRequest = 
                             new com.spring.teambondbackend.recommendation.dtos.GithubScoreRequest();
                         scoreRequest.setUsername(user.getUsername());
                         scoreRequest.setEmail(user.getEmail());
                         scoreRequest.setAccessToken(user.getGithubAccessToken());
                         
                         // Run asynchronously or directly? Directly for now to ensure it completes.
                         System.out.println("Triggering Framework Analysis for: " + username);
                         frameworkAnalysisService.analyseUserFrameworkStats(scoreRequest);
                         System.out.println("Framework Analysis completed for: " + username);
                     } catch (Exception e) {
                         System.err.println("Failed to update framework stats: " + e.getMessage());
                         // Don't fail the whole request, just log
                     }
                 } else {
                     System.out.println("Skipping Framework Analysis - No Access Token for: " + username);
                 }
            }
            
            return ResponseEntity.ok(evaluation);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Analysis failed: " + e.getMessage());
        }
    }
}

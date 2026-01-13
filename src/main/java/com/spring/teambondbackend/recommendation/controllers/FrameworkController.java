package com.spring.teambondbackend.recommendation.controllers;

import com.spring.teambondbackend.recommendation.dtos.GithubScoreRequest;
import com.spring.teambondbackend.recommendation.models.UserFrameworkStats;
import com.spring.teambondbackend.recommendation.services.FrameworkAnalysisService;
import com.spring.teambondbackend.recommendation.utils.ApiException;
import com.spring.teambondbackend.recommendation.utils.ApiResponse;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/frameworks")
@RequiredArgsConstructor
public class FrameworkController {

    private final FrameworkAnalysisService frameworkAnalysisService;
    private static Logger logger = LoggerFactory.getLogger(FrameworkController.class);

    /**
     * Endpoint to fetch GitHub repositories, detect frameworks, and count associated files for scoring.
     * @param request Request body containing GitHub username, email, and access token
     * @return ResponseEntity containing an ApiResponse with a map of frameworks to their file counts
     */
    public void setGithubScore(@RequestBody GithubScoreRequest request) {
        try {
            System.out.println(request.getUsername());
            frameworkAnalysisService.analyseUserFrameworkStats(request);
        } catch (ApiException e) {
            logger.error(e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }
        @GetMapping("/users")
        public ResponseEntity<ApiResponse> getUsers(@RequestParam(required = true) String username) {
            try{

                UserFrameworkStats userFrameworkStats = this.frameworkAnalysisService.getUserFrameworkStats(username);
                if(userFrameworkStats == null){
                    throw  new ApiException(400,"No frameworks found for the user");
                }
                return ResponseEntity.status(200).body(new ApiResponse(200,userFrameworkStats,"Successfully retrieved the user-frameworks"));
            }catch (ApiException e){
                logger.error(e.getMessage());
                return ResponseEntity.status(e.getStatusCode()).body(new ApiResponse(e.getStatusCode(),null,e.getMessage()));
            }
        }
}

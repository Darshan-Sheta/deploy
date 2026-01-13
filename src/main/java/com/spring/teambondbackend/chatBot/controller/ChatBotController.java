package com.spring.teambondbackend.chatBot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chatbot")
public class ChatBotController {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper; // Add ObjectMapper for JSON serialization

    @Value("${flask_url}")
    private String flaskUrl;

    @PostMapping("/test")
    public ResponseEntity<?> testConnectionToChatBot(@RequestBody Map<String, String> requestBody) {
        try {
            // Build the Flask API URL
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(flaskUrl)
                    .path("/api/api/chat")
                    .build()
                    .toUri();

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Convert Map to JSON
            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            // Create HttpEntity with body and headers
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

            // Send POST request to Flask
            ResponseEntity<String> response = restTemplate.postForEntity(uri, request, String.class);

            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error calling Flask API: " + e.getMessage());
        }
    }

}

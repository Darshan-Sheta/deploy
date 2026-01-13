package com.spring.teambondbackend.hackathon.controller;

import com.cloudinary.Cloudinary;
import com.spring.teambondbackend.hackathon.dto.HackathonDTO;
import com.spring.teambondbackend.hackathon.model.Hackathon;
import com.spring.teambondbackend.hackathon.service.HackathonService;
import com.spring.teambondbackend.hackathon.service.MailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.teambondbackend.recommendation.utils.ApiResponse;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hackathons")
@RequiredArgsConstructor
public class HackathonController {

    private final HackathonService hackathonService;
    private final Cloudinary cloudinary;
    private final ObjectMapper objectMapper;
    private final MailService mailService;

    @PostMapping
    public ResponseEntity<Hackathon> createHackathon(@RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam("data") String jsonData) {
        try {
            // Configure date format for parsing
            objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
            HackathonDTO hackathonDTO = objectMapper.readValue(jsonData, HackathonDTO.class);

            // Process the logo file if it exists
            if (logo != null && !logo.isEmpty()) {
                // Handle file upload
                // image-upload
                Map data = this.cloudinary.uploader().upload(logo.getBytes(), Map.of());
                String url = data.get("url").toString();
                hackathonDTO.setLogo(url);
            }
            return ResponseEntity.ok(hackathonService.createHackathon(hackathonDTO));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping
    public ResponseEntity<List<Hackathon>> getAllActiveHackathons() {
        return ResponseEntity.ok(hackathonService.getAllActiveHackathons());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<Hackathon>> getUpcomingHackathons() {
        return ResponseEntity.ok(hackathonService.getUpcomingHackathons());
    }

    @GetMapping("/ongoing")
    public ResponseEntity<List<Hackathon>> getOngoingHackathons() {
        return ResponseEntity.ok(hackathonService.getOngoingHackathons());
    }

    @GetMapping("/past")
    public ResponseEntity<List<Hackathon>> getPastHackathons() {
        return ResponseEntity.ok(hackathonService.getPastHackathons());
    }

    @GetMapping("/my-hackathons")
    public ResponseEntity<List<Hackathon>> getMyHackathons(@RequestParam String username) {
        return ResponseEntity.ok(hackathonService.getMyHackathons(username));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Hackathon> getHackathonById(@PathVariable String id) {
        return ResponseEntity.ok(hackathonService.getHackathonById(id));
    }

    // for testing
    @PostMapping("/upload/img")
    public ResponseEntity<?> uploadHackathonImg(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(hackathonService.uploadImage(file));
    }

    // for testing
    @PostMapping("/mail/send")
    public ResponseEntity<String> sendHackathonMail(@RequestParam String to, @RequestParam String subject,
            @RequestParam String body) {
        try {
            mailService.sendEmail(to, subject, body);
            return new ResponseEntity<>("Email sent successfully to " + to, HttpStatus.OK);
        } catch (MessagingException e) {
            return new ResponseEntity<>("Failed to send email: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasAuthority('PAID')")
    @GetMapping("/nearby-hackathons")
    public ResponseEntity<List<Hackathon>> getNearbyHackathons(@RequestParam(required = true) Double latitude,
            @RequestParam(required = true) Double longitude,
            @RequestParam(required = false) Double radius) {
        List<Hackathon> activeHackathons = this.hackathonService.findNearbyHackathons(latitude, longitude, radius);
        return ResponseEntity.ok(activeHackathons);
    }

    @PreAuthorize("hasAuthority('PAID')")
    @GetMapping("/recommended-hackathons")
    public ResponseEntity<List<HackathonService.ScoredHackathon>> recommendHackathonsToUser(
            @RequestParam(required = true) String username) {
        return ResponseEntity.ok(this.hackathonService.recommendHackathons(username));
    }

    @GetMapping("/{id}/recommended-users")
    public ResponseEntity<?> getRecommendedUsers(@PathVariable String id) {
        try {
            System.out.println("========================================");
            System.out.println("📥 CONTROLLER: Received request for recommended users");
            System.out.println("📥 Hackathon ID: " + id);
            System.out.println("📥 Endpoint: /api/hackathons/" + id + "/recommended-users");

            // Validate input
            if (id == null || id.trim().isEmpty()) {
                System.out.println("⚠️ CONTROLLER: Invalid hackathon ID");
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<HackathonService.ScoredUser> recommendedUsers;
            try {
                recommendedUsers = this.hackathonService.recommendUsersForHackathon(id);
            } catch (Throwable t) { // Catch ALL errors including OutOfMemoryError, etc.
                System.err.println("💥 SERVICE ERROR (caught in controller):");
                System.err.println("💥 Error: " + t.getMessage());
                System.err.println("💥 Class: " + t.getClass().getName());
                t.printStackTrace();
                return ResponseEntity.ok(Collections.emptyList());
            }

            if (recommendedUsers == null) {
                System.out.println("⚠️ CONTROLLER: Service returned null, returning empty list");
                return ResponseEntity.ok(Collections.emptyList());
            }

            System.out.println("✅ CONTROLLER: Returning " + recommendedUsers.size() + " recommended users");
            System.out.println("========================================");

            // Try to serialize to catch any serialization issues early
            try {
                String json = objectMapper.writeValueAsString(recommendedUsers);
                System.out.println("✅ Serialization test passed, JSON length: " + json.length());
            } catch (Exception serializationError) {
                System.err.println("💥 SERIALIZATION ERROR:");
                System.err.println("💥 Error: " + serializationError.getMessage());
                serializationError.printStackTrace();
                // Return empty list if serialization fails
                return ResponseEntity.ok(Collections.emptyList());
            }

            return ResponseEntity.ok(recommendedUsers);
        } catch (Throwable t) { // Catch ALL errors
            System.err.println("========================================");
            System.err.println("💥 CONTROLLER CRITICAL ERROR:");
            System.err.println("💥 Error Message: " + t.getMessage());
            System.err.println("💥 Error Class: " + t.getClass().getName());
            System.err.println("💥 Stack Trace:");
            t.printStackTrace();
            System.err.println("========================================");

            // Return empty list instead of error response to prevent 500
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @PostMapping("/generate-details")
    public ResponseEntity<HackathonDTO> generateHackathonDetails(@RequestBody Map<String, String> payload) {
        String description = payload.get("description");
        if (description == null || description.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(hackathonService.generateHackathonDetails(description));
    }

    @PostMapping("/jen-ai")
    public ResponseEntity<Map<String, String>> chatWithJenAI(@RequestBody Map<String, String> payload) {
        String query = payload.get("query");
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query is required"));
        }

        String response = hackathonService.chatWithJenAI(query);
        return ResponseEntity.ok(Map.of("response", response));
    }
}

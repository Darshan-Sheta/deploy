package com.spring.codeamigosbackend.hackathon.service;

import com.cloudinary.Cloudinary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.spring.codeamigosbackend.geolocation.services.GeolocationService;
import com.spring.codeamigosbackend.hackathon.dto.HackathonDTO;

import com.spring.codeamigosbackend.hackathon.model.Hackathon;
import com.spring.codeamigosbackend.hackathon.model.HackathonRequest;
import com.spring.codeamigosbackend.hackathon.repository.HackathonRepository;
import com.spring.codeamigosbackend.hackathon.exception.ValidationException;
import com.spring.codeamigosbackend.hackathon.repository.HackathonRequestRepository;
import com.spring.codeamigosbackend.recommendation.models.UserFrameworkStats;
import com.spring.codeamigosbackend.recommendation.services.FrameworkAnalysisService;
import com.spring.codeamigosbackend.recommendation.utils.ApiException;
import com.spring.codeamigosbackend.registration.model.User;
import com.spring.codeamigosbackend.registration.service.UserService;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HackathonService {
    private final HackathonRepository hackathonRepository;
    private final Cloudinary cloudinary;
    @Autowired
    private GeolocationService geolocationService;
    private final UserService userService;
    private final FrameworkAnalysisService frameworkAnalysisService;

    private static Logger logger = LoggerFactory.getLogger("HackathonService.class");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    // Gemini AI configuration
    private String geminiApiKey;
    private String geminiModel;

    @jakarta.annotation.PostConstruct
    public void initGemini() {
        String key = null;
        String model = null;
        try {
            // Try loading from .env
            try {
                var dotenv = Dotenv.configure().ignoreIfMissing().load();
                key = dotenv.get("GEMINI_API_KEY");
                model = dotenv.get("GEMINI_MODEL");
            } catch (Exception dotenvError) {
                logger.debug("Failed to load .env file (this is OK if using system env vars): {}",
                        dotenvError.getMessage());
            }
        } catch (Exception e) {
            logger.warn("Error in initGemini: {}", e.getMessage());
        }

        // Fallback to System Environment Variables
        if (key == null || key.isEmpty()) {
            key = System.getenv("GEMINI_API_KEY");
        }
        if (model == null || model.isEmpty()) {
            model = System.getenv("GEMINI_MODEL");
        }

        if (key != null) {
            key = key.trim();
        }
        if (model != null) {
            model = model.trim();
        }

        this.geminiApiKey = key;
        this.geminiModel = (model == null || model.isEmpty()) ? "gemini-2.5-flash" : model;

        String maskedKey = (geminiApiKey != null && geminiApiKey.length() > 5)
                ? geminiApiKey.substring(0, 5) + "..."
                : (geminiApiKey == null ? "NULL" : "***");
        logger.info("Loaded Gemini API Key: {}", maskedKey);
        logger.info("Using Gemini model: {}", this.geminiModel);
    }

    @Transactional
    public Hackathon createHackathon(HackathonDTO request) throws IOException {
        validateRequest(request);
        Hackathon hackathon = new Hackathon();

        hackathon.setLogo(request.getLogo());
        hackathon.setTitle(request.getTitle());
        hackathon.setOrganization(request.getOrganization());
        hackathon.setTheme(request.getTheme());
        hackathon.setMode(request.getMode());
        hackathon.setAbout(request.getAbout());
        hackathon.setLocation(request.getLocation());
        hackathon.setTechStacks(request.getTechStacks());
        List<Double> coordinates = this.geolocationService.getCoordinatesFromLocation(request.getLocation());
        hackathon.setLatitude(coordinates.get(0));
        hackathon.setLongitude(coordinates.get(1));

        if (request.getTeamSize() != null) {
            Hackathon.TeamSize teamSize = new Hackathon.TeamSize();
            teamSize.setMin(request.getTeamSize().getMin());
            teamSize.setMax(request.getTeamSize().getMax());
            hackathon.setTeamSize(teamSize);
        }

        Hackathon.RegistrationDates dates = new Hackathon.RegistrationDates();
        dates.setStart(request.getRegistrationDates().getStart());
        dates.setEnd(request.getRegistrationDates().getEnd());
        hackathon.setRegistrationDates(dates);
        hackathon.setCurrentTeamSize(1);
        Hackathon.HackathonDates hackathonDates = new Hackathon.HackathonDates();
        hackathonDates.setStart(request.getHackathonDates().getStart());
        hackathonDates.setEnd(request.getHackathonDates().getEnd());
        hackathon.setHackathonDates(hackathonDates);
        hackathon.setCreatedAt(LocalDateTime.now());
        hackathon.setUpdatedAt(LocalDateTime.now());
        hackathon.setCreatedBy(request.getCreatedBy());
        hackathon.setCreatedById(request.getCreatedById());
        return hackathonRepository.save(hackathon);
    }

    public List<Hackathon> getAllActiveHackathons() {
        return hackathonRepository
                .findByRegistrationDates_EndAfterOrderByRegistrationDates_StartAsc(LocalDateTime.now());
    }

    public List<Hackathon> getPastHackathons() {
        return hackathonRepository
                .findByRegistrationDates_EndBeforeOrderByRegistrationDates_EndDesc(LocalDateTime.now());
    }

    public List<Hackathon> getUpcomingHackathons() {
        return hackathonRepository
                .findByRegistrationDates_StartAfterOrderByRegistrationDates_StartAsc(LocalDateTime.now());
    }

    public List<Hackathon> getMyHackathons(String username) {
        return hackathonRepository.findByCreatedBy(username);
    }

    public List<Hackathon> getOngoingHackathons() {
        LocalDateTime now = LocalDateTime.now();
        return hackathonRepository
                .findByRegistrationDates_StartBeforeAndRegistrationDates_EndAfterOrderByRegistrationDates_StartAsc(now,
                        now);
    }

    private void validateRequest(HackathonDTO request) {
        if (request.getRegistrationDates().getEnd().isBefore(request.getRegistrationDates().getStart())) {
            throw new ValidationException("End date cannot be before start date");
        }
    }

    public Hackathon getHackathonById(String id) {
        Object cached = redisTemplate.opsForValue().get(id);
        if (cached != null) {
            Hackathon hackathon = objectMapper.convertValue(cached, Hackathon.class);
            return hackathon;
        }

        Hackathon hackathon = hackathonRepository.findById(id).orElse(null);
        if (hackathon != null) {
            redisTemplate.opsForValue().set(id, hackathon, 86400, TimeUnit.SECONDS);
            return hackathon;
        }

        throw new RuntimeException("Hackathon not found with id: " + id);
    }

    // for testing
    public Map uploadImage(MultipartFile file) {
        try {
            Map data = this.cloudinary.uploader().upload(file.getBytes(), Map.of());
            return data;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload image to Cloudinary");
        }
    }

    public List<Hackathon> findNearbyHackathons(Double latitude, Double longitude) {
        return findNearbyHackathons(latitude, longitude, 100.0); // Default radius = 100 km
    }

    // Find all hackathons and use the haversine formula to find the nearby
    // hackathons
    public List<Hackathon> findNearbyHackathons(Double latitude, Double longitude, Double radiusKm) {

        List<Hackathon> activeHackathons = this.getAllActiveHackathons();
        return activeHackathons.stream()
                .filter(h -> {
                    if (h.getLatitude() == null || h.getLongitude() == null)
                        return false;
                    double distance = calculateDistance(latitude, longitude, h.getLatitude(), h.getLongitude());
                    return distance <= radiusKm;
                })
                .collect(Collectors.toList());
    }

    // Haversine formula
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // Radius of Earth in kilometers
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public List<ScoredHackathon> recommendHackathons(String username) {
        // Step 1: Get the user's framework stats
        UserFrameworkStats stats = this.frameworkAnalysisService.getUserFrameworkStats(username);

        if (stats == null || stats.getFrameworkUsage() == null || stats.getFrameworkUsage().isEmpty()) {
            System.out.println("No framework stats found for user:" + username);
            return Collections.emptyList();
        }
        // Step 2: Extract frameworks the user is proficient in
        Map<String, Integer> frameworkUsage = stats.getFrameworkUsage();
        List<String> userFrameworks = new ArrayList<>(frameworkUsage.keySet());
        if (userFrameworks.isEmpty()) {
            System.out.println("No frameworks found in stats for user: {}" + username);
            return Collections.emptyList();
        }

        List<Hackathon> hackathons = this.getAllActiveHackathons();
        // Normalize user frameworks to lowercase for case-insensitive matching
        List<String> normalizedUserFrameworks = userFrameworks.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        System.out.println(hackathons);

        // Step 3 : Rank hackathons based on number of framework matches (primary) and
        // proficiency score (secondary)
        List<ScoredHackathon> scoredHackathons = hackathons.stream()
                .map(hackathon -> {
                    int matchCount = countFrameworkMatches(hackathon, userFrameworks);
                    double proficiencyScore = calculateProficiencyScore(hackathon, frameworkUsage);
                    return new ScoredHackathon(hackathon, matchCount, proficiencyScore);
                })
                .sorted((h1, h2) -> {
                    // Primary sort: Number of matches (descending)
                    int matchCompare = Integer.compare(h2.getMatchCount(), h1.getMatchCount());
                    if (matchCompare != 0) {
                        return matchCompare;
                    }
                    // Secondary sort: Proficiency score (descending)
                    return Double.compare(h2.getProficiencyScore(), h1.getProficiencyScore());
                })
                .collect(Collectors.toList());
        logger.info("Decending order of recommended hackathons: " + scoredHackathons);
        return scoredHackathons.stream().filter(scoredHackathon -> scoredHackathon.getProficiencyScore() > 0)
                .collect(Collectors.toList());
    }

    /**
     * Counts the number of frameworks in the hackathon's tech stack that match the
     * user's frameworks.
     * 
     * @param hackathon                The hackathon to evaluate
     * @param normalizedUserFrameworks List of frameworks the user is proficient in
     * @return Number of matching frameworks
     */
    private int countFrameworkMatches(Hackathon hackathon, List<String> normalizedUserFrameworks) {
        List<String> techStack = hackathon.getTechStacks();
        if (techStack == null || techStack.isEmpty()) {
            return 0;
        }
        // Normalize tech stack to lowercase for case-insensitive matching
        int matches = (int) techStack.stream()
                .map(String::toLowerCase)
                .filter(normalizedUserFrameworks::contains)
                .count();
        return (int) techStack.stream()
                .filter(normalizedUserFrameworks::contains)
                .count();
    }

    /**
     * Calculates a proficiency score for a hackathon based on the user's
     * proficiency in its required tech stack.
     * 
     * @param hackathon      The hackathon to score
     * @param frameworkUsage The user's framework usage stats
     * @return Average proficiency score for matched frameworks
     */
    private double calculateProficiencyScore(Hackathon hackathon, Map<String, Integer> frameworkUsage) {
        logger.info(frameworkUsage.toString());
        List<String> techStack = hackathon.getTechStacks();
        if (techStack == null || techStack.isEmpty()) {
            return 0.0;
        }
        logger.info(" Hackathon Tech stacks: " + techStack);

        // Normalize tech stack and keys for case-insensitive match
        Set<String> userFrameworksLower = frameworkUsage.keySet().stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .map(x -> x.replaceAll(" ", ""))
                .collect(Collectors.toSet());
        logger.info(" User Framework Usage: " + userFrameworksLower);
        double totalScore = 0.0;

        for (String framework : techStack) {
            logger.info(" Framework: " + framework);

            String lowerFramework = framework.toLowerCase().trim().replaceAll(" ", "");
            logger.info(" LowerFramework: " + lowerFramework);
            for (String userFramework : userFrameworksLower) {
                if (userFramework.equals(lowerFramework)) {
                    // Case-insensitive match
                    logger.info(" Framework Matches: " + frameworkUsage.get(framework));
                    totalScore += frameworkUsage.getOrDefault(framework, 0);
                    logger.info(" Matched framework " + userFramework);
                    break;
                }
            }
            logger.info("Matched score" + totalScore);
        }

        return totalScore;
    }

    /**
     * Helper class to hold a hackathon, its match count, and proficiency score for
     * ranking.
     */
    @Data
    public static class ScoredHackathon {
        private Hackathon hackathon;
        private int matchCount;
        private double proficiencyScore;

        public ScoredHackathon(Hackathon hackathon, int matchCount, double proficiencyScore) {
            this.hackathon = hackathon;
            this.matchCount = matchCount;
            this.proficiencyScore = proficiencyScore;
        }

        public Hackathon getHackathon() {
            return hackathon;
        }

        public int getMatchCount() {
            return matchCount;
        }

        public double getProficiencyScore() {
            return proficiencyScore;
        }
    }

    /**
     * Simplified POJO for recommended users (runtime-only, NOT stored in DB)
     */
    @Data
    public static class ScoredUser {
        private String userId;
        private String name;
        private double score;
        private List<String> matchedSkills;

        // Default constructor for Jackson
        public ScoredUser() {
            this.matchedSkills = new ArrayList<>();
        }

        public ScoredUser(String userId, String name, double score, List<String> matchedSkills) {
            this.userId = userId;
            this.name = name;
            this.score = score;
            this.matchedSkills = matchedSkills != null ? matchedSkills : new ArrayList<>();
        }
    }

    /**
     * Inner class to hold Gemini AI matching result
     */
    @Data
    private static class MatchingResult {
        private int matchScore;
        private String overallSkillLevel;
        private String matchingReason;

        // Default constructor for Jackson
        public MatchingResult() {
        }

        public MatchingResult(int matchScore, String overallSkillLevel, String matchingReason) {
            this.matchScore = matchScore;
            this.overallSkillLevel = overallSkillLevel;
            this.matchingReason = matchingReason;
        }
    }

    /**
     * Recommends users for a specific hackathon based on their tech stack
     * proficiency using Gemini AI for intelligent matching.
     *
     * @param hackathonId The ID of the hackathon
     * @return List of scored users sorted by match score (0-100) and skill level
     */
    /**
     * Recommends users for a hackathon using Gemini AI
     * Clean architecture: Fetch data → Build prompt → Call Gemini → Parse response
     */
    public List<ScoredUser> recommendUsersForHackathon(String hackathonId) {
        try {
            System.out.println("========================================");
            System.out.println("🚀 recommendUsersForHackathon CALLED");
            System.out.println("🚀 Hackathon ID: " + hackathonId);
            System.out.println("========================================");
            logger.info("🚀 recommendUsersForHackathon called with ID: {}", hackathonId);

            // Step 1: Fetch Hackathon
            Hackathon hackathon = hackathonRepository.findById(hackathonId).orElse(null);
            System.out.println("📋 Hackathon found: " + (hackathon != null ? hackathon.getTitle() : "NULL"));
            if (hackathon == null) {
                logger.warn("Hackathon not found with id: {}", hackathonId);
                return Collections.emptyList();
            }

            List<String> techStacks = hackathon.getTechStacks();
            if (techStacks == null || techStacks.isEmpty()) {
                System.out.println("⚠️ Hackathon has no tech stacks, but will still return users");
                logger.warn("⚠️ Hackathon has no tech stacks, will return users anyway");
                techStacks = new ArrayList<>(); // Use empty list but continue
            }

            // Step 2: Fetch Users (exclude creator and accepted users)
            List<User> allUsers = userService.getAllUsers();
            if (allUsers == null || allUsers.isEmpty()) {
                System.out.println("❌ No users found in database - cannot return recommendations");
                logger.warn("⚠️ No users found in database");
                return Collections.emptyList();
            }

            logger.info("📊 Total users in database: {}", allUsers.size());
            System.out.println("📊 Total users in database: " + allUsers.size());

            // Filter excluded users
            Set<String> excludedUsernames = new HashSet<>();
            if (hackathon.getCreatedBy() != null) {
                excludedUsernames.add(hackathon.getCreatedBy());
                logger.info("🚫 Excluding hackathon creator: {}", hackathon.getCreatedBy());
                System.out.println("🚫 Excluding hackathon creator: " + hackathon.getCreatedBy());
            }
            if (hackathon.getAcceptedUsers() != null && !hackathon.getAcceptedUsers().isEmpty()) {
                excludedUsernames.addAll(hackathon.getAcceptedUsers());
                logger.info("🚫 Excluding {} already accepted users", hackathon.getAcceptedUsers().size());
                System.out.println("🚫 Excluding " + hackathon.getAcceptedUsers().size() + " already accepted users");
            }

            // More lenient filtering - don't require GitHub username (we'll handle that in
            // fallback)
            List<User> eligibleUsers = allUsers.stream()
                    .filter(u -> u != null
                            && u.getUsername() != null
                            && !excludedUsernames.contains(u.getUsername()))
                    .limit(200) // Limit to prevent large payloads
                    .collect(Collectors.toList());

            logger.info("✅ Eligible users after filtering: {} (out of {} total)", eligibleUsers.size(),
                    allUsers.size());
            System.out.println("✅ Eligible users: " + eligibleUsers.size() + " (out of " + allUsers.size() + " total)");

            if (eligibleUsers.isEmpty()) {
                System.out.println("⚠️ No eligible users after filtering - trying to return at least one");
                logger.warn("⚠️ No eligible users found after filtering, but will try to return at least one");
                // Even if all users are excluded, try to return at least one from all users
                if (!allUsers.isEmpty()) {
                    User firstUser = allUsers.get(0);
                    ScoredUser scoredUser = new ScoredUser();
                    scoredUser.setUserId(firstUser.getId());
                    scoredUser.setName(
                            firstUser.getDisplayName() != null ? firstUser.getDisplayName() : firstUser.getUsername());
                    scoredUser.setScore(5.0);
                    scoredUser.setMatchedSkills(new ArrayList<>());
                    System.out.println("✅ Returning at least one user (even if excluded): " + scoredUser.getName());
                    return Collections.singletonList(scoredUser);
                }
                return Collections.emptyList();
            }

            System.out.println("✅ Proceeding with " + eligibleUsers.size() + " eligible users");

            // Step 3: Build Gemini Prompt with clean, minimal data
            String prompt;
            try {
                prompt = buildGeminiPrompt(hackathon, eligibleUsers);
            } catch (Exception e) {
                logger.error("Error building Gemini prompt: {}", e.getMessage(), e);
                // Fallback: return basic recommendations without Gemini
                return getBasicRecommendations(hackathon, eligibleUsers);
            }

            // Step 4: Call Gemini API
            String aiResponse;
            try {
                aiResponse = callGeminiAPI(prompt);
            } catch (Exception e) {
                logger.error("Error calling Gemini API: {}", e.getMessage(), e);
                // Fallback: return basic recommendations without Gemini
                return getBasicRecommendations(hackathon, eligibleUsers);
            }

            // Step 5: Parse response and map to ScoredUser
            List<ScoredUser> scoredUsers;
            try {
                scoredUsers = parseGeminiResponse(aiResponse, eligibleUsers);
            } catch (Exception e) {
                logger.error("Error parsing Gemini response: {}", e.getMessage(), e);
                // Fallback: return basic recommendations without Gemini
                return getBasicRecommendations(hackathon, eligibleUsers);
            }

            // Step 6: Sort by score (descending) - ensure highest score first
            if (scoredUsers != null && !scoredUsers.isEmpty()) {
                scoredUsers.sort((a, b) -> {
                    // Primary: Sort by score descending (highest first)
                    int scoreCompare = Double.compare(b.getScore(), a.getScore());
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    // Secondary: If scores are equal, sort by number of matched skills (more skills
                    // = better)
                    int skillsCompare = Integer.compare(
                            b.getMatchedSkills() != null ? b.getMatchedSkills().size() : 0,
                            a.getMatchedSkills() != null ? a.getMatchedSkills().size() : 0);
                    return skillsCompare;
                });

                logger.info("✅ Found {} recommended users from Gemini for hackathon {} (sorted by score)",
                        scoredUsers.size(), hackathonId);
                return scoredUsers;
            }

            // If Gemini returned empty, use fallback to ensure at least one user
            System.out.println("⚠️ Gemini returned empty, using fallback");
            logger.info("Gemini returned empty results, using basic recommendations to ensure at least one match");
            List<ScoredUser> fallbackResults = getBasicRecommendations(hackathon, eligibleUsers);

            // Ensure we return at least one user (the best match)
            if (fallbackResults != null && !fallbackResults.isEmpty()) {
                System.out.println("✅ Fallback returned " + fallbackResults.size() + " users");
                System.out.println("✅ First user: " + fallbackResults.get(0).getName() + " (Score: "
                        + fallbackResults.get(0).getScore() + ")");
                System.out.println("========================================");
                logger.info("✅ Fallback: Found {} recommended users for hackathon {} (sorted by score)",
                        fallbackResults.size(), hackathonId);
                return fallbackResults;
            }

            // Last resort: return at least one user from eligible users
            System.out.println("⚠️ Fallback returned empty, using last resort");
            System.out.println("⚠️ Eligible users count: " + eligibleUsers.size());
            System.out.println("⚠️ All users count: " + allUsers.size());
            logger.warn("⚠️ No recommendations found even with fallback, using last resort");
            List<ScoredUser> lastResort = getAtLeastOneUser(eligibleUsers);

            // If last resort also fails, try with all users (even excluded ones)
            if (lastResort.isEmpty() && !allUsers.isEmpty()) {
                System.out.println("⚠️ Last resort with eligible users failed, trying with ALL users");
                lastResort = getAtLeastOneUser(allUsers);
            }

            System.out.println("========================================");
            if (lastResort.isEmpty()) {
                System.out.println("❌ FINAL RESULT: Returning empty list (no users available)");
            } else {
                System.out.println("✅ FINAL RESULT: Returning " + lastResort.size() + " user(s)");
                System.out.println("✅ User: " + lastResort.get(0).getName());
            }
            return lastResort;

        } catch (Throwable e) { // Catch ALL exceptions including Errors
            System.err.println("========================================");
            System.err.println("💥 CRITICAL ERROR in recommendUsersForHackathon");
            System.err.println("💥 Hackathon ID: " + hackathonId);
            System.err.println("💥 Error: " + e.getMessage());
            System.err.println("💥 Class: " + e.getClass().getName());
            e.printStackTrace();
            System.err.println("========================================");
            logger.error("💥 CRITICAL Error recommending users for hackathon {}: {}", hackathonId, e.getMessage(), e);

            // Even on error, try to return at least one user
            try {
                Hackathon hackathon = hackathonRepository.findById(hackathonId).orElse(null);
                if (hackathon != null) {
                    List<User> allUsers = userService.getAllUsers();
                    if (allUsers != null && !allUsers.isEmpty()) {
                        List<User> eligibleUsers = allUsers.stream()
                                .filter(u -> u != null && u.getUsername() != null)
                                .limit(10)
                                .collect(Collectors.toList());
                        if (!eligibleUsers.isEmpty()) {
                            System.out.println("🆘 Error occurred, but returning at least one user as fallback");
                            return getAtLeastOneUser(eligibleUsers);
                        }
                    }
                }
            } catch (Exception fallbackError) {
                System.err.println("💥 Even fallback failed: " + fallbackError.getMessage());
            }

            return Collections.emptyList();
        }
    }

    /**
     * Fallback method: Returns basic recommendations without Gemini AI
     * This ensures the endpoint always works even if Gemini fails
     * ALWAYS returns at least one user (the best available match)
     */
    private List<ScoredUser> getBasicRecommendations(Hackathon hackathon, List<User> users) {
        try {
            System.out.println("========================================");
            System.out.println("🔄 FALLBACK: getBasicRecommendations called");
            System.out.println("🔄 Users to process: " + (users != null ? users.size() : "null"));
            logger.info("🔄 Using fallback method for recommendations");
            List<String> techStacks = hackathon.getTechStacks();
            if (techStacks == null || techStacks.isEmpty()) {
                System.out.println("⚠️ Hackathon has no tech stacks - returning users anyway");
                logger.warn("⚠️ Hackathon has no tech stacks in fallback");
                // Return at least one user even without tech stack matching
                List<ScoredUser> result = getAtLeastOneUser(users);
                System.out.println("✅ Returning " + result.size() + " users (no tech stack matching)");
                System.out.println("========================================");
                return result;
            }

            System.out.println("📋 Tech stacks to match: " + techStacks);

            List<ScoredUser> recommendations = new ArrayList<>();
            List<ScoredUser> usersWithoutStats = new ArrayList<>(); // Store users without framework stats as backup

            logger.info("📊 Processing {} users for fallback recommendations", users.size());

            for (User user : users) {
                try {
                    if (user == null || user.getId() == null || user.getUsername() == null) {
                        continue;
                    }

                    // Get user's skills
                    List<String> matchedSkills = new ArrayList<>();
                    double score = 0.0;
                    boolean hasFrameworkStats = false;

                    try {
                        UserFrameworkStats stats = frameworkAnalysisService.getUserFrameworkStats(user.getUsername());
                        if (stats != null && stats.getFrameworkUsage() != null
                                && !stats.getFrameworkUsage().isEmpty()) {
                            hasFrameworkStats = true;
                            Map<String, Integer> frameworkUsage = stats.getFrameworkUsage();

                            // Calculate matches
                            for (String hackathonTech : techStacks) {
                                String normalizedHackathonTech = hackathonTech.toLowerCase().trim().replaceAll(" ", "");
                                for (String userFramework : frameworkUsage.keySet()) {
                                    String normalizedUserFramework = userFramework.toLowerCase().trim().replaceAll(" ",
                                            "");
                                    if (normalizedUserFramework.equals(normalizedHackathonTech)) {
                                        matchedSkills.add(userFramework);
                                        score += frameworkUsage.get(userFramework);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (java.util.NoSuchElementException e) {
                        // User has no framework stats - store for backup
                        hasFrameworkStats = false;
                        logger.debug("User {} has no framework stats, will use as backup", user.getUsername());
                    } catch (Exception e) {
                        logger.debug("Could not get framework stats for user {}: {}", user.getUsername(),
                                e.getMessage());
                        hasFrameworkStats = false;
                    }

                    // Include users with at least one match
                    if (!matchedSkills.isEmpty() && score > 0) {
                        // Normalize score to 0-100
                        double normalizedScore = Math.min(100, score / 10);

                        ScoredUser scoredUser = new ScoredUser();
                        scoredUser.setUserId(user.getId());
                        scoredUser.setName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
                        scoredUser.setScore(normalizedScore);
                        scoredUser.setMatchedSkills(matchedSkills);

                        recommendations.add(scoredUser);
                    } else if (!hasFrameworkStats) {
                        // Store users without framework stats as backup (give them a low score)
                        ScoredUser backupUser = new ScoredUser();
                        backupUser.setUserId(user.getId());
                        backupUser.setName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
                        backupUser.setScore(10.0); // Low score but still included
                        backupUser.setMatchedSkills(new ArrayList<>());
                        usersWithoutStats.add(backupUser);
                    }
                } catch (Exception e) {
                    logger.debug("Error processing user {}: {}", user.getUsername(), e.getMessage());
                    // Continue with next user
                }
            }

            logger.info("📈 Found {} users with matches, {} users without stats", recommendations.size(),
                    usersWithoutStats.size());

            // Sort by score descending (highest score first)
            recommendations.sort((a, b) -> {
                // Primary: Sort by score descending
                int scoreCompare = Double.compare(b.getScore(), a.getScore());
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                // Secondary: If scores are equal, sort by number of matched skills
                int skillsCompare = Integer.compare(
                        b.getMatchedSkills() != null ? b.getMatchedSkills().size() : 0,
                        a.getMatchedSkills() != null ? a.getMatchedSkills().size() : 0);
                return skillsCompare;
            });

            // Ensure at least one user is returned
            if (recommendations.isEmpty()) {
                System.out.println("⚠️ No users with matching skills found in fallback");
                System.out.println("⚠️ Users without stats count: " + usersWithoutStats.size());
                logger.warn("⚠️ No users with matching skills found, using users without framework stats");
                if (!usersWithoutStats.isEmpty()) {
                    // Return at least one user even without perfect matches
                    ScoredUser bestBackup = usersWithoutStats.get(0);
                    System.out.println("✅ Returning backup user: " + bestBackup.getName() + " (Score: "
                            + bestBackup.getScore() + ")");
                    logger.info("✅ Returning at least one user (backup): {}", bestBackup.getName());
                    return Collections.singletonList(bestBackup);
                } else {
                    // Last resort: return first eligible user
                    System.out.println("⚠️ No backup users available, using last resort");
                    System.out.println("⚠️ Total users available: " + users.size());
                    List<ScoredUser> lastResort = getAtLeastOneUser(users);
                    if (lastResort.isEmpty()) {
                        System.out.println("❌ Even last resort returned empty!");
                    } else {
                        System.out.println("✅ Last resort returned: " + lastResort.get(0).getName());
                    }
                    return lastResort;
                }
            }

            System.out.println("✅ Basic recommendations: Found " + recommendations.size() + " users");
            if (!recommendations.isEmpty()) {
                System.out.println("✅ First user: " + recommendations.get(0).getName() + " (Score: "
                        + recommendations.get(0).getScore() + ")");
            }
            System.out.println("========================================");
            logger.info("✅ Basic recommendations: Found {} users for hackathon (sorted by score, highest first)",
                    recommendations.size());
            return recommendations;

        } catch (Exception e) {
            System.err.println("💥 Error in getBasicRecommendations: " + e.getMessage());
            e.printStackTrace();
            logger.error("Error in getBasicRecommendations: {}", e.getMessage(), e);
            // Last resort: return at least one user
            System.out.println("🆘 Exception in fallback, using last resort");
            return getAtLeastOneUser(users);
        }
    }

    /**
     * Last resort: Returns at least one user to ensure we always have a result
     * This method GUARANTEES at least one user is returned
     */
    private List<ScoredUser> getAtLeastOneUser(List<User> users) {
        System.out.println("========================================");
        System.out.println("🆘 LAST RESORT: getAtLeastOneUser called");
        System.out.println("🆘 Users list size: " + (users != null ? users.size() : "null"));

        if (users == null || users.isEmpty()) {
            logger.warn("⚠️ No users available to return in last resort");
            System.out.println("⚠️ No users available");
            System.out.println("========================================");
            return Collections.emptyList();
        }

        // Find first valid user
        for (User user : users) {
            if (user != null && user.getId() != null && user.getUsername() != null) {
                ScoredUser scoredUser = new ScoredUser();
                scoredUser.setUserId(user.getId());
                scoredUser.setName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
                scoredUser.setScore(10.0); // Give a minimum score so it shows up
                scoredUser.setMatchedSkills(new ArrayList<>());

                System.out.println("✅ Returning user (last resort): " + scoredUser.getName());
                System.out.println("✅ User ID: " + scoredUser.getUserId());
                System.out.println("✅ Score: " + scoredUser.getScore());
                System.out.println("========================================");
                logger.info("✅ Returning at least one user (last resort): {} (ID: {})", scoredUser.getName(),
                        scoredUser.getUserId());
                return Collections.singletonList(scoredUser);
            }
        }

        System.out.println("❌ No valid user found in list");
        System.out.println("========================================");
        logger.warn("⚠️ No valid user found in users list");
        return Collections.emptyList();
    }

    /**
     * Builds a clean Gemini prompt with minimal, safe user data
     */
    private String buildGeminiPrompt(Hackathon hackathon, List<User> users) {
        StringBuilder usersJson = new StringBuilder();

        for (User user : users) {
            try {
                // Get user's framework stats (skills) - handle Optional safely
                List<String> skills = new ArrayList<>();
                try {
                    // Check if user exists first
                    if (user.getUsername() == null || user.getUsername().isEmpty()) {
                        continue; // Skip users without username
                    }

                    // Try to get framework stats, but don't fail if not found
                    try {
                        UserFrameworkStats stats = frameworkAnalysisService.getUserFrameworkStats(user.getUsername());
                        if (stats != null && stats.getFrameworkUsage() != null
                                && !stats.getFrameworkUsage().isEmpty()) {
                            skills = new ArrayList<>(stats.getFrameworkUsage().keySet());
                        }
                    } catch (java.util.NoSuchElementException e) {
                        // User doesn't have framework stats - that's OK, use empty skills list
                        logger.debug("User {} has no framework stats, using empty skills", user.getUsername());
                    } catch (Exception e) {
                        // Any other error - log but continue
                        logger.debug("Could not get framework stats for user {}: {}", user.getUsername(),
                                e.getMessage());
                    }
                } catch (Exception e) {
                    logger.warn("Error processing user {} for framework stats: {}", user.getUsername(), e.getMessage());
                    // Continue with empty skills
                }

                // Build clean user JSON (NO passwords, emails, tokens)
                String userJson = String.format(
                        """
                                {
                                  "id": "%s",
                                  "name": "%s",
                                  "skills": %s,
                                  "bio": "%s"
                                }""",
                        user.getId() != null ? user.getId() : "",
                        user.getDisplayName() != null ? user.getDisplayName().replace("\"", "\\\"")
                                : user.getUsername(),
                        objectMapper.writeValueAsString(skills),
                        user.getBio() != null ? user.getBio().replace("\"", "\\\"").replace("\n", " ") : "");

                if (usersJson.length() > 0) {
                    usersJson.append(",\n");
                }
                usersJson.append(userJson);
            } catch (Exception e) {
                logger.warn("Error building user data for prompt: {}", e.getMessage());
                // Skip this user
            }
        }

        String prompt = String.format(
                """
                        You are an AI hackathon recruiter.

                        Hackathon:
                        Title: %s
                        Theme: %s
                        Organization: %s
                        Required Tech Stacks: %s
                        Mode: %s
                        Location: %s

                        Users:
                        [%s]

                        Task:
                        - Score each user from 0 to 100 based on how well they match the hackathon requirements
                        - Consider: tech stack alignment, experience level, and relevance to theme
                        - Return ONLY a JSON array in this exact format:
                        [{"userId": "user123", "score": 85, "matchedSkills": ["Java", "Spring Boot"]}]

                        CRITICAL REQUIREMENTS:
                        1. You MUST return AT LEAST ONE user (the one with the best match, even if score is low)
                        2. Include ALL users who have at least one matching skill
                        3. Sort the array by score in DESCENDING order (highest score first)
                        4. matchedSkills should be a list of skills from the user's skills that match the hackathon's required tech stacks
                        5. Score should reflect: number of matching skills (primary), proficiency level (secondary), and overall fit

                        Rules:
                        - Return ONLY the JSON array, no explanation text
                        - Array must be sorted by score (highest to lowest)
                        - Minimum 1 user must be returned (the best match)
                        - matchedSkills must only include skills that are in the hackathon's required tech stacks
                        """,
                hackathon.getTitle() != null ? hackathon.getTitle() : "",
                hackathon.getTheme() != null ? hackathon.getTheme() : "",
                hackathon.getOrganization() != null ? hackathon.getOrganization() : "",
                hackathon.getTechStacks() != null ? String.join(", ", hackathon.getTechStacks()) : "",
                hackathon.getMode() != null ? hackathon.getMode() : "",
                hackathon.getLocation() != null ? hackathon.getLocation() : "",
                usersJson.toString());

        return prompt;
    }

    /**
     * Calls Gemini API using RestTemplate
     */
    private String callGeminiAPI(String prompt) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            logger.warn("Gemini API key not configured, returning empty response");
            return "[]";
        }

        try {
            String url = "https://generativelanguage.googleapis.com/v1/models/" + geminiModel + ":generateContent?key="
                    + geminiApiKey;

            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);
            content.put("parts", Collections.singletonList(part));
            requestBody.put("contents", Collections.singletonList(content));

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(
                    requestBody, headers);

            @SuppressWarnings("unchecked")
            org.springframework.http.ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url,
                    org.springframework.http.HttpMethod.POST, entity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getBody() != null) {
                return objectMapper.writeValueAsString(response.getBody());
            }

            return "[]";
        } catch (Exception e) {
            logger.error("Error calling Gemini API: {}", e.getMessage(), e);
            return "[]";
        }
    }

    /**
     * Parses Gemini response and maps to ScoredUser list
     */
    private List<ScoredUser> parseGeminiResponse(String response, List<User> users) {
        try {
            // Handle empty response
            if (response == null || response.trim().isEmpty() || response.equals("[]")) {
                logger.warn("Empty or invalid Gemini response");
                return Collections.emptyList();
            }

            // Parse the response JSON
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response);
            if (root == null) {
                logger.warn("Could not parse Gemini response as JSON");
                return Collections.emptyList();
            }

            com.fasterxml.jackson.databind.JsonNode candidates = root.get("candidates");

            if (candidates == null || !candidates.isArray() || candidates.size() == 0) {
                logger.warn("No candidates in Gemini response");
                return Collections.emptyList();
            }

            com.fasterxml.jackson.databind.JsonNode firstCandidate = candidates.get(0);
            if (firstCandidate == null) {
                logger.warn("First candidate is null");
                return Collections.emptyList();
            }

            com.fasterxml.jackson.databind.JsonNode content = firstCandidate.get("content");
            if (content == null) {
                logger.warn("Content is null in Gemini response");
                return Collections.emptyList();
            }

            com.fasterxml.jackson.databind.JsonNode parts = content.get("parts");
            if (parts == null || !parts.isArray() || parts.size() == 0) {
                logger.warn("Parts is null or empty in Gemini response");
                return Collections.emptyList();
            }

            com.fasterxml.jackson.databind.JsonNode textNode = parts.get(0).get("text");
            if (textNode == null) {
                logger.warn("No text in Gemini response");
                return Collections.emptyList();
            }

            String text = textNode.asText();
            if (text == null || text.trim().isEmpty()) {
                logger.warn("Text is empty in Gemini response");
                return Collections.emptyList();
            }

            // Clean markdown code blocks if present
            text = text.replaceAll("```json", "").replaceAll("```", "").trim();

            // Extract JSON array if wrapped in other text
            int jsonStart = text.indexOf("[");
            int jsonEnd = text.lastIndexOf("]");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                text = text.substring(jsonStart, jsonEnd + 1);
            } else if (jsonStart < 0) {
                logger.warn("No JSON array found in Gemini response text");
                return Collections.emptyList();
            }

            // Parse JSON array to List<ScoredUser>
            com.fasterxml.jackson.core.type.TypeReference<List<ScoredUser>> typeRef = new com.fasterxml.jackson.core.type.TypeReference<List<ScoredUser>>() {
            };
            List<ScoredUser> scoredUsers = objectMapper.readValue(text, typeRef);

            if (scoredUsers == null || scoredUsers.isEmpty()) {
                logger.info("Parsed empty list from Gemini response");
                return Collections.emptyList();
            }

            // Map userId to user name
            Map<String, User> userMap = users.stream()
                    .filter(u -> u != null && u.getId() != null)
                    .collect(Collectors.toMap(User::getId, u -> u, (u1, u2) -> u1));

            for (ScoredUser scoredUser : scoredUsers) {
                if (scoredUser != null && scoredUser.getUserId() != null) {
                    User user = userMap.get(scoredUser.getUserId());
                    if (user != null) {
                        scoredUser.setName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
                    }
                }
            }

            return scoredUsers;

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("JSON parsing error in Gemini response: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("Error parsing Gemini response: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Evaluates a user's match with a hackathon using Gemini AI
     */
    private MatchingResult evaluateUserWithGemini(User user, Hackathon hackathon, Map<String, Integer> frameworkUsage) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            logger.debug("GEMINI_API_KEY is not configured, skipping AI evaluation for user {}", user.getUsername());
            return null;
        }

        try {
            // Build prompt for Gemini
            String prompt = buildMatchingPrompt(user, hackathon, frameworkUsage);
            logger.debug("Calling Gemini AI for user: {}", user.getUsername());

            // Call Gemini API
            Client client = new Client.Builder().apiKey(geminiApiKey).build();
            GenerateContentResponse response = client.models.generateContent(
                    geminiModel,
                    prompt,
                    null);

            if (response == null || response.text() == null || response.text().isEmpty()) {
                logger.warn("Empty response from Gemini AI for user {}", user.getUsername());
                return null;
            }

            String text = response.text();
            logger.debug("Gemini raw response for user {}: {}", user.getUsername(),
                    text.substring(0, Math.min(200, text.length())));

            // Clean markdown code blocks if present
            String jsonText = text.replaceAll("```json", "").replaceAll("```", "").trim();

            // Try to extract JSON if it's wrapped in other text
            int jsonStart = jsonText.indexOf("{");
            int jsonEnd = jsonText.lastIndexOf("}");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                jsonText = jsonText.substring(jsonStart, jsonEnd + 1);
            }

            // Parse JSON response
            MatchingResult result = objectMapper.readValue(jsonText, MatchingResult.class);

            // Validate result
            if (result.getMatchScore() < 0 || result.getMatchScore() > 100) {
                logger.warn("Invalid match score {} from Gemini for user {}, defaulting to 50",
                        result.getMatchScore(), user.getUsername());
                result.setMatchScore(50);
            }

            if (result.getOverallSkillLevel() == null || result.getOverallSkillLevel().isEmpty()) {
                result.setOverallSkillLevel("Intermediate");
            }

            if (result.getMatchingReason() == null || result.getMatchingReason().isEmpty()) {
                result.setMatchingReason("Matched based on tech stack");
            }

            logger.info("Gemini evaluation for user {}: score={}, level={}",
                    user.getUsername(), result.getMatchScore(), result.getOverallSkillLevel());

            return result;
        } catch (Exception e) {
            logger.error("Error calling Gemini AI for user {}: {}", user.getUsername(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Builds a prompt for Gemini AI to evaluate user-hackathon matching
     */
    private String buildMatchingPrompt(User user, Hackathon hackathon, Map<String, Integer> frameworkUsage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(
                "You are an expert at matching developers with hackathons based on their technical skills and experience.\n\n");

        prompt.append("HACKATHON DETAILS:\n");
        prompt.append("- Title: ").append(hackathon.getTitle()).append("\n");
        prompt.append("- Theme: ").append(hackathon.getTheme()).append("\n");
        prompt.append("- Organization: ").append(hackathon.getOrganization()).append("\n");
        if (hackathon.getAbout() != null && !hackathon.getAbout().isEmpty()) {
            prompt.append("- About: ").append(hackathon.getAbout()).append("\n");
        }
        prompt.append("- Required Tech Stacks: ").append(String.join(", ", hackathon.getTechStacks())).append("\n");
        prompt.append("- Mode: ").append(hackathon.getMode()).append("\n");
        prompt.append("- Location: ").append(hackathon.getLocation()).append("\n");

        prompt.append("\nDEVELOPER PROFILE:\n");
        prompt.append("- Username: ").append(user.getUsername()).append("\n");
        prompt.append("- Display Name: ")
                .append(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()).append("\n");
        prompt.append("- GitHub: ").append(user.getGithubUsername()).append("\n");
        if (user.getBio() != null && !user.getBio().isEmpty()) {
            prompt.append("- Bio: ").append(user.getBio()).append("\n");
        }
        prompt.append("- Framework Usage (proficiency scores):\n");
        for (Map.Entry<String, Integer> entry : frameworkUsage.entrySet()) {
            prompt.append("  * ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        prompt.append("\nTASK:\n");
        prompt.append("Evaluate how well this developer matches the hackathon requirements.\n");
        prompt.append("Consider:\n");
        prompt.append("1. Tech stack alignment (how many required technologies match)\n");
        prompt.append("2. Proficiency level in matching technologies\n");
        prompt.append("3. Overall skill level based on framework usage scores\n");
        prompt.append("4. Relevance to hackathon theme and requirements\n\n");

        prompt.append("Return a JSON object with the following structure:\n");
        prompt.append("{\n");
        prompt.append("  \"matchScore\": <integer 0-100>, // Overall match score out of 100\n");
        prompt.append("  \"overallSkillLevel\": \"<Beginner|Intermediate|Advanced|Expert>\", // Overall skill level\n");
        prompt.append("  \"matchingReason\": \"<brief explanation of why this developer is a good match>\"\n");
        prompt.append("}\n\n");
        prompt.append("Only return the JSON object, no additional text.");

        return prompt.toString();
    }

}

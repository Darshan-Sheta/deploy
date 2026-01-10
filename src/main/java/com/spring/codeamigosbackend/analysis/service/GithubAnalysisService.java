package com.spring.codeamigosbackend.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.codeamigosbackend.analysis.dto.DeveloperEvaluation;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GithubAnalysisService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Load API Key & model safely from .env or System Env
    private String geminiApiKey;
    private String geminiModel;

    @jakarta.annotation.PostConstruct
    public void init() {
        String key = null;
        String model = null;
        try {
            // Try loading from .env
            var dotenv = Dotenv.configure().ignoreIfMissing().load();
            key = dotenv.get("GEMINI_API_KEY");
            model = dotenv.get("GEMINI_MODEL");
        } catch (Exception e) {
            System.err.println("WARNING: Failed to load .env file: " + e.getMessage());
        }

        // Fallback to System Environment Variables (e.g., for Docker/Render)
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
        // Default to a current broadly available model if none is configured
        // NOTE: Older 1.5 models like "gemini-1.5-flash" / "gemini-1.5-pro" have been
        // retired.
        this.geminiModel = (model == null || model.isEmpty()) ? "gemini-2.5-flash" : model;

        String maskedKey = (geminiApiKey != null && geminiApiKey.length() > 5)
                ? geminiApiKey.substring(0, 5) + "..."
                : (geminiApiKey == null ? "NULL" : "***");
        System.out.println("DEBUG: Loaded Gemini API Key: " + maskedKey);
        System.out.println("DEBUG: Using Gemini model: " + this.geminiModel);
    }

    public DeveloperEvaluation analyzeDeveloper(String username) throws Exception {
        // 1. Fetch GitHub Data
        Map<String, Object> userData = fetchGithubData(username);

        // 2. Construct Prompt
        String prompt = constructPrompt(userData);

        // 3. Call Gemini
        return callGemini(prompt);
    }

    private Map<String, Object> fetchGithubData(String username) {
        String userUrl = "https://api.github.com/users/" + username;
        String reposUrl = "https://api.github.com/users/" + username + "/repos?per_page=40&sort=updated";
        String orgsUrl = "https://api.github.com/users/" + username + "/orgs";

        Map<String, Object> userData = restTemplate.getForObject(userUrl, Map.class);
        List<Map<String, Object>> repos = restTemplate.getForObject(reposUrl, List.class);
        List<Map<String, Object>> orgs = restTemplate.getForObject(orgsUrl, List.class);

        // Aggregate languages
        Map<String, Integer> languages = new HashMap<>();
        List<Map<String, Object>> repoSummary = new ArrayList<>();

        if (repos != null) {
            for (Map<String, Object> repo : repos) {
                String lang = (String) repo.get("language");
                if (lang != null) {
                    languages.put(lang, languages.getOrDefault(lang, 0) + 1);
                }

                Map<String, Object> summary = new HashMap<>();
                summary.put("n", repo.get("name"));
                summary.put("d", repo.get("description"));
                summary.put("l", lang);
                summary.put("t", repo.get("topics"));
                repoSummary.add(summary);
            }
        }

        Map<String, Object> aggregatedData = new HashMap<>();
        aggregatedData.put("user", userData);
        aggregatedData.put("repos", repoSummary);
        aggregatedData.put("orgs", orgs);
        aggregatedData.put("languages", languages);

        return aggregatedData;
    }

    private String constructPrompt(Map<String, Object> data) {
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        List<Map<String, Object>> orgs = (List<Map<String, Object>>) data.get("orgs");
        String orgsStr = orgs != null
                ? orgs.stream().map(o -> (String) o.get("login")).collect(Collectors.joining(", "))
                : "";

        return String.format(
                """
                        As an elite technical recruiter and AI Judge, analyze this GitHub dataset for "%s".

                        DATA FETCHED FROM GITHUB:
                        User: %s | Bio: %s
                        Location: %s | Blog: %s
                        Stats: %s Repos, %s Followers
                        Organizations: %s
                        Languages Distribution: %s
                        Top Repositories: %s

                        YOU MUST PROVIDE JUDGMENT ON THESE 8 POINTS:
                        1. Languages (Mastery & Variety)
                        2. Frameworks (Identify from context/descriptions)
                        3. Libraries (Detect common deps)
                        4. Tools (Docker, CI/CD, Firebase, Cloud providers, etc.)
                        5. Technologies (Web, Backend, AI/ML, DevOps, Systems, etc.)
                        6. Repositories (Count, type, and quality breakdown)
                        7. Project Types (Frontend, Backend, Fullstack, ML, Research, etc.)
                        8. Tech Stack (A combined high-level view)

                        ALSO GENERATE:
                        - developer_type: Professional title (e.g., Senior Full Stack Architect)
                        - levels_array: List of specific skills and assessment (Beginner/Intermediate/Advanced/Expert). Format: [{"skill": "Java", "level": "Expert"}, ...]
                        - scores: Numeric values (0-100) for 'skill', 'consistency', and 'hackathon_fit'.

                        RETURN VALID JSON ONLY. The JSON structure must strictly match:
                        {
                          "developer_type": "string",
                          "levels_array": [{"skill": "string", "level": "string"}],
                          "scores": {"skill": number, "consistency": number, "hackathon_fit": number},
                          "analysis": {
                            "languages": ["string"],
                            "frameworks": ["string"],
                            "libraries": ["string"],
                            "tools": ["string"],
                            "technologies": ["string"],
                            "repo_breakdown": "string",
                            "project_types": ["string"],
                            "tech_stack_summary": ["string"]
                          }
                        }
                        """,
                user.get("login"),
                user.get("name"), user.get("bio"),
                user.get("location"), user.get("blog"),
                user.get("public_repos"), user.get("followers"),
                orgsStr,
                data.get("languages"),
                data.get("repos"));
    }

    private DeveloperEvaluation callGemini(String prompt) {
        try {
            if (geminiApiKey == null || geminiApiKey.isEmpty()) {
                throw new IllegalStateException("GEMINI_API_KEY is missing. Please check .env or system variables.");
            }

            // Using Google GenAI SDK
            Client client = new Client.Builder().apiKey(geminiApiKey).build();

            GenerateContentResponse response = client.models.generateContent(
                    geminiModel,
                    prompt,
                    null);

            String text = response.text();

            // Clean markdown code blocks if present
            String jsonText = text.replaceAll("```json", "").replaceAll("```", "").trim();

            DeveloperEvaluation eval = objectMapper.readValue(jsonText, DeveloperEvaluation.class);

            // Populate level map for easier frontend use if needed
            if (eval.getLevels_array() != null) {
                Map<String, String> levelsMap = new HashMap<>();
                for (DeveloperEvaluation.SkillLevel sl : eval.getLevels_array()) {
                    levelsMap.put(sl.getSkill(), sl.getLevel());
                }
                eval.setLevels(levelsMap);
            }

            return eval;

        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            System.err.println("CRITICAL ERROR in GitHub Analysis: " + message);

            // Surface a more helpful message for common Gemini configuration issues
            if (message.contains("404 Not Found") && message.contains("models/")) {
                message = """
                        Gemini model not found. The configured model name '%s' is not available for your API key / project (older 1.5 models have likely been retired).
                        Set GEMINI_MODEL to a supported model (for example: 'gemini-2.5-flash' or 'gemini-2.5-pro') and ensure your GEMINI_API_KEY is valid.
                        """
                        .formatted(geminiModel);
            }

            e.printStackTrace();
            throw new RuntimeException("AI Analysis Failed: " + message);
        }
    }
}

package com.spring.teambondbackend.recommendation.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.teambondbackend.recommendation.dtos.RepositoryInfo;
import com.spring.teambondbackend.recommendation.utils.Mappings;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class GithubApiService {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private static final String GITHUB_GRAPHQL_URL = "https://api.github.com/graphql";
    private static Logger logger = LoggerFactory.getLogger(GithubApiService.class);

    public List<RepositoryInfo> getTopRepositories(String username, String email, String accessToken) {
        String query = buildGraphQLQuery(username, email);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        logger.info("Access token: " + accessToken);
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("query", query);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(GITHUB_GRAPHQL_URL, request, JsonNode.class);

        JsonNode data = response.getBody().get("data");
        logger.info("Github API response: " + data.toString());
        if (data == null) {
            System.out.println("No data found in response");
            return new ArrayList<>();
        }

        JsonNode repositories = data.get("user").get("repositories").get("nodes");
        if (repositories == null) {
            System.out.println("No repositories found for user: " + username);
            return new ArrayList<>();
        }

        List<RepositoryInfo> repoInfos = new ArrayList<>();
        for (JsonNode repo : repositories) {
            String name = repo.get("name").asText();
            JsonNode defaultBranchRef = repo.get("defaultBranchRef");
            if (defaultBranchRef == null || defaultBranchRef.get("name") == null) {
                System.out.println("Skipping repo " + name + ": No default branch found");
                continue;
            }
            String defaultBranch = defaultBranchRef.get("name").asText();

            List<String> commitShas = new ArrayList<>();
            JsonNode historyEdges = defaultBranchRef.get("target").get("history").get("edges");
            for (JsonNode edge : historyEdges) {
                commitShas.add(edge.get("node").get("oid").asText());
            }

            List<RepositoryInfo.Language> topLanguages = new ArrayList<>();
            JsonNode languagesNodes = repo.get("languages").get("nodes");
            JsonNode languagesEdges = repo.get("languages").get("edges");
            for (int i = 0; i < languagesNodes.size(); i++) {
                String langName = languagesNodes.get(i).get("name").asText();
                long langSize = languagesEdges.get(i).get("size").asLong();
                topLanguages.add(new RepositoryInfo.Language(langName, langSize));
            }
            repoInfos.add(new RepositoryInfo(name, defaultBranch, commitShas, topLanguages));
        }
        return repoInfos;
    }

    public String buildGraphQLQuery(String username, String email) {
        return String.format("""
        query {
          user(login: "%s") {
            repositories(first: 15, orderBy: {field: PUSHED_AT, direction: DESC}) {
              nodes {
                name
                defaultBranchRef {
                  name
                  target {
                    ... on Commit {
                      history(first: 100, author: {emails: ["%s"]}) {
                        edges {
                          node {
                            oid
                          }
                        }
                        pageInfo {
                          hasNextPage
                          endCursor
                        }
                      }
                    }
                  }
                }
                languages(first: 2) {
                  edges {
                    size
                  }
                  nodes {
                    name
                  }
                }
              }
              pageInfo {
                hasNextPage
                endCursor
              }
            }
          }
        }
        """, username, email);
    }

    /**
     * Processes multiple repositories in parallel to detect frameworks.
     * Uses a thread pool with a capped number of threads for framework detection.
     * @param repositories List of repositories to process
     * @param owner The repository owner
     * @param accessToken GitHub access token for authentication
     * @return Map of repository to its detected frameworks
     */
    public Map<RepositoryInfo, List<String>> getFrameworksForRepositories(List<RepositoryInfo> repositories, String owner, String accessToken) {
        int threadPoolSize = Math.min(repositories.size(), 10); // Cap at 10 threads for framework detection
        ExecutorService frameworkExecutor = Executors.newFixedThreadPool(threadPoolSize);
        Map<RepositoryInfo, List<String>> repoToFrameworks = new ConcurrentHashMap<>();
        List<Future<Void>> frameworkFutures = new ArrayList<>();

        // THis part executed concurrently
        for (RepositoryInfo repo : repositories) {
            Callable<Void> task = () -> {
                try {
                    List<String> frameworks = getFrameworkFromRepository(repo, owner, accessToken);
                    repoToFrameworks.put(repo, frameworks);
                } catch (Exception e) {
                    System.err.println("Error processing repo " + repo.getName() + ": " + e.getMessage());
                    repoToFrameworks.put(repo, Collections.emptyList());
                }
                return null;
            };
            frameworkFutures.add(frameworkExecutor.submit(task));
        }

        // This waiting logic is serializable
        // Once main thread gets the output from the first thread then only does it go for the second one
        for (Future<Void> future : frameworkFutures) {
            try {
                future.get(30, TimeUnit.SECONDS); // Timeout after 30 seconds per task
            } catch (TimeoutException e) {
                System.err.println("Task timed out: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Error retrieving result: " + e.getMessage());
            }
        }

        frameworkExecutor.shutdown(); // This is just a advisory method .
        try {
            if (!frameworkExecutor.awaitTermination(60, TimeUnit.SECONDS)) { // If this returns false then shut down immediately meaning if the task not completed in the given time then just simply shutdown
                frameworkExecutor.shutdownNow();  // This shuts down the frameworkExecutor service then and there
            }
        } catch (InterruptedException e) {
//          // If error then also shut down the frameworkExecutor service then and there .
            frameworkExecutor.shutdownNow();
//            Thread.currentThread().interrupt();
        }
        return repoToFrameworks;
    }

    /**
     * Counts the number of files associated with each framework across repositories.
     * Processes commits in parallel with a thread pool sized based on the number of commits.
     * @param repoToFrameworks Map of repositories to their detected frameworks
     * @param owner The repository owner
     * @param accessToken GitHub access token for authentication
     * @return Map of framework to the count of associated files
     */
    public Map<String, Integer> countFrameworkFiles(Map<RepositoryInfo, List<String>> repoToFrameworks, String owner, String accessToken) {
        Map<String, Set<String>> globalFrameworkToFiles = new ConcurrentHashMap<>();

        for (Map.Entry<RepositoryInfo, List<String>> entry : repoToFrameworks.entrySet()) {
            RepositoryInfo repo = entry.getKey();
            List<String> frameworks = entry.getValue();

            if (frameworks.isEmpty() || repo.getCommitShas().isEmpty()) {
                continue; // Skip repositories with no frameworks or commits
            }

            int commitThreadPoolSize = Math.min(repo.getCommitShas().size(), 100); // Cap at 100 threads for commit processing
            ExecutorService commitExecutor = Executors.newFixedThreadPool(commitThreadPoolSize);
            List<Future<Void>> commitFutures = new ArrayList<>();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            for (String commitSha : repo.getCommitShas()) {
                Callable<Void> task = () -> {
                    String commitUrl = "https://api.github.com/repos/" + owner + "/" + repo.getName() + "/commits/" + commitSha;
                    try {
                        ResponseEntity<JsonNode> response = restTemplate.exchange(commitUrl, HttpMethod.GET, entity, JsonNode.class);
                        JsonNode commitData = response.getBody();
                        JsonNode files = commitData.get("files");

                        if (files == null || !files.isArray()) {
                            return null;
                        }

                        for (JsonNode file : files) {
                            String filename = file.get("filename").asText();
                            String repoFilePath = repo.getName() + "/" + filename;
                            for (String framework : frameworks) {
                                List<String> extensions = Mappings.FRAMEWORK_TO_FILE_EXTENSIONS.getOrDefault(framework, Collections.emptyList());
                                for (String ext : extensions) {
                                    if (filename.endsWith(ext)) {
                                        globalFrameworkToFiles.computeIfAbsent(framework, k -> new HashSet<>()).add(repoFilePath);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error fetching commit " + commitSha + " for repo " + repo.getName() + ": " + e.getMessage());
                    }
                    return null;
                };
                commitFutures.add(commitExecutor.submit(task));
            }

            for (Future<Void> future : commitFutures) {
                try {
                    future.get(30, TimeUnit.SECONDS); // Timeout after 30 seconds per task
                } catch (TimeoutException e) {
                    System.err.println("Task timed out: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Error retrieving result: " + e.getMessage());
                }
            }

            commitExecutor.shutdown();
            try {
                if (!commitExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    commitExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                commitExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        Map<String, Integer> frameworkToFileCounts = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : globalFrameworkToFiles.entrySet()) {
            frameworkToFileCounts.put(entry.getKey(), entry.getValue().size());
        }

        return frameworkToFileCounts;
    }

    public List<String> getFrameworkFromRepository(RepositoryInfo repo, String owner, String accessToken) {
        String url = "https://api.github.com/repos/" + owner + "/" + repo.getName() + "/git/trees/" + repo.getDefaultBranch() + "?recursive=1";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        // Got data of all the files
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
        JsonNode tree = response.getBody().get("tree");

        List<String> configFilesToLookFor = new ArrayList<>();
        for (RepositoryInfo.Language lang : repo.getTopLanguages()) { // Loop max twice only as 2 languages at max
            String languageName = lang.getName();
            if (Mappings.LANGUAGE_TO_CONFIG.containsKey(languageName)) {
                configFilesToLookFor.addAll(Mappings.LANGUAGE_TO_CONFIG.get(languageName));
            }
        }

        List<String> configFilePaths = new ArrayList<>();
        for (JsonNode item : tree) { // For each file ...
            if (item.get("type").asText().equals("blob")) {
                String filePath = item.get("path").asText();
                for (String configFile : configFilesToLookFor) {  // Check if it matches one of the config files we are looking for
                    if (filePath.endsWith(configFile)) {
                        configFilePaths.add(filePath);
                        break;
                    }
                }
            }
        }

        List<String> detectedFrameworks = new ArrayList<>();
        for (String configPath : configFilePaths) {
            String configFileName = configPath.substring(configPath.lastIndexOf("/") + 1);
            if (!Mappings.CONFIG_TO_DEPENDENCY_FRAMEWORK.containsKey(configFileName)) {
                System.out.println("No dependency-framework mapping for: " + configFileName);
                continue;
            }

            String contentUrl = "https://api.github.com/repos/" + owner + "/" + repo.getName() + "/contents/" + configPath + "?ref=" + repo.getDefaultBranch();
            ResponseEntity<JsonNode> contentResponse = restTemplate.exchange(contentUrl, HttpMethod.GET, entity, JsonNode.class);
            JsonNode contentNode = contentResponse.getBody();

            if (!contentNode.has("content")) {
                System.out.println("No content found for file: " + configPath);
                continue;
            }
            String contentBase64 = contentNode.get("content").asText();
            contentBase64 = contentBase64.replaceAll("\n", "");
            String content;
            try {
                content = new String(Base64.getDecoder().decode(contentBase64));
            } catch (IllegalArgumentException e) {
                System.err.println("Failed to decode Base64 content for file " + configPath + ": " + e.getMessage());
                continue;
            }

            List<Mappings.DependencyFramework> dependencyFrameworks = Mappings.CONFIG_TO_DEPENDENCY_FRAMEWORK.get(configFileName);
            for (Mappings.DependencyFramework df : dependencyFrameworks) {
                if (df.getChecker().test(content, df.getDependency())) {
                    detectedFrameworks.add(df.getFramework());
                    System.out.println("Detected framework: " + df.getFramework() + " for config file: " + configPath);
                }
            }
        }

        System.out.println("Detected frameworks: " + detectedFrameworks);
        return detectedFrameworks;
    }
}

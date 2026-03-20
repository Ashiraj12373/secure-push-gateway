package com.securepushgateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.securepushgateway.engine.ScanEngineService;
import com.securepushgateway.model.*;
import com.securepushgateway.model.Scan.ScanStatus;
import com.securepushgateway.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanOrchestrationService {

    private final ScanEngineService scanEngine;
    private final ScanRepository scanRepository;
    private final VulnerabilityResultRepository vulnerabilityRepository;
    private final UserRepository userRepository;
    private final BadgeService badgeService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final RestTemplate restTemplate;

    @Value("${github.token}")
    private String githubToken;

    /**
     * Async entry point — called from webhook controller.
     * Extracts diff, runs scan, persists results, triggers notifications.
     */
    @Async
    public void processPushAsync(JsonNode payload) {
        try {
            String pusherName = payload.path("pusher").path("name").asText();
            String repoName = payload.path("repository").path("full_name").asText();
            String branch = payload.path("ref").asText().replace("refs/heads/", "");

            JsonNode commits = payload.path("commits");
            if (commits.isEmpty()) {
                log.info("No commits in push event — skipping scan");
                return;
            }

            // Find or create developer record
            User developer = userRepository.findByUsername(pusherName)
                .orElseGet(() -> {
                    log.info("Auto-creating developer account for GitHub user: {}", pusherName);
                    return userRepository.save(User.builder()
                        .username(pusherName)
                        .email(pusherName + "@noreply.github.com")
                        .password("OAUTH_ONLY")
                        .role(User.Role.DEVELOPER)
                        .build());
                });

            for (JsonNode commit : commits) {
                String sha = commit.path("id").asText();
                log.info("Processing commit {} for developer {}", sha, pusherName);

                // Fetch the actual diff from GitHub API
                Map<String, String> fileDiffs = fetchCommitDiff(repoName, sha);

                // Run scan across all changed files
                List<VulnerabilityResult> allVulns = new ArrayList<>();
                for (Map.Entry<String, String> entry : fileDiffs.entrySet()) {
                    List<VulnerabilityResult> fileVulns = scanEngine.scan(entry.getKey(), entry.getValue());
                    allVulns.addAll(fileVulns);
                }

                ScanStatus status = allVulns.isEmpty() ? ScanStatus.PASS : ScanStatus.FAIL;

                // Persist scan
                Scan scan = scanRepository.save(Scan.builder()
                    .developer(developer)
                    .repoName(repoName)
                    .commitSha(sha)
                    .branch(branch)
                    .status(status)
                    .totalVulnerabilities(allVulns.size())
                    .build());

                // Persist each vulnerability linked to the scan
                for (VulnerabilityResult vuln : allVulns) {
                    vuln.setScan(scan);
                    vulnerabilityRepository.save(vuln);
                }

                // Notifications, badges, email
                notificationService.createScanNotification(developer, scan);
                emailService.sendScanResultEmail(developer, scan, allVulns);

                if (status == ScanStatus.PASS) {
                    List<Badge> awarded = badgeService.evaluate(developer, scan);
                    for (Badge badge : awarded) {
                        notificationService.createBadgeNotification(developer, badge);
                        emailService.sendBadgeEmail(developer, badge);
                    }
                }

                log.info("Scan {} completed — status: {}, vulnerabilities: {}",
                    scan.getId(), status, allVulns.size());
            }
        } catch (Exception e) {
            log.error("Scan orchestration failed", e);
        }
    }

    /**
     * Calls GitHub API to retrieve file contents from a commit.
     * Returns map of filename → file content.
     */
    private Map<String, String> fetchCommitDiff(String repoName, String sha) {
        Map<String, String> results = new HashMap<>();
        try {
            String url = "https://api.github.com/repos/" + repoName + "/commits/" + sha;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + githubToken);
            headers.set("Accept", "application/vnd.github.v3+json");
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);

            if (response.getBody() == null) return results;

            for (JsonNode file : response.getBody().path("files")) {
                String filename = file.path("filename").asText();
                String patch = file.path("patch").asText();
                // Only scan Java and JS files
                if (filename.endsWith(".java") || filename.endsWith(".js") || filename.endsWith(".ts")) {
                    results.put(filename, patch);
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch commit diff for sha {}: {}", sha, e.getMessage());
        }
        return results;
    }
}

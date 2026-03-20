package com.securepushgateway.controller;

import com.securepushgateway.engine.ScanEngineService;
import com.securepushgateway.model.*;
import com.securepushgateway.repository.*;
import com.securepushgateway.service.BadgeService;
import com.securepushgateway.service.NotificationService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/scans")
@RequiredArgsConstructor
@Slf4j
public class ScanController {

    private final ScanRepository scanRepository;
    private final UserRepository userRepository;
    private final VulnerabilityResultRepository vulnerabilityRepository;
    private final ScanEngineService scanEngineService;
    private final NotificationService notificationService;
    private final BadgeService badgeService;

    @GetMapping
    public ResponseEntity<List<Scan>> getScans(@AuthenticationPrincipal UserDetails principal) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        if (user.getRole() == User.Role.OWNER) {
            return ResponseEntity.ok(scanRepository.findAllByOrderByScannedAtDesc());
        }
        return ResponseEntity.ok(scanRepository.findByDeveloperOrderByScannedAtDesc(user));
    }

    @GetMapping("/{scanId}")
    public ResponseEntity<Map<String, Object>> getScan(@PathVariable Long scanId) {
        return scanRepository.findById(scanId).map(scan -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", scan.getId());
            result.put("repoName", scan.getRepoName());
            result.put("commitSha", scan.getCommitSha());
            result.put("branch", scan.getBranch());
            result.put("status", scan.getStatus());
            result.put("scannedAt", scan.getScannedAt());
            result.put("totalVulnerabilities", scan.getTotalVulnerabilities());
            if (scan.getDeveloper() != null) {
                Map<String, Object> dev = new LinkedHashMap<>();
                dev.put("id", scan.getDeveloper().getId());
                dev.put("username", scan.getDeveloper().getUsername());
                result.put("developer", dev);
            }
            result.put("vulnerabilities", vulnerabilityRepository.findByScan(scan));
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/developer/{developerId}")
    public ResponseEntity<List<Scan>> getByDeveloper(@PathVariable Long developerId) {
        User dev = userRepository.findById(developerId).orElseThrow();
        return ResponseEntity.ok(scanRepository.findByDeveloperOrderByScannedAtDesc(dev));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<Scan> all = scanRepository.findAll();
        long pass = all.stream().filter(s -> s.getStatus() == Scan.ScanStatus.PASS).count();
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", all.size());
        stats.put("pass", pass);
        stats.put("fail", all.size() - pass);
        stats.put("passRate", all.isEmpty() ? 0 : (double) pass / all.size() * 100);
        return ResponseEntity.ok(stats);
    }

    /**
     * Manual scan — submit code directly for vulnerability scanning.
     * Request body: { "fileName": "Example.java", "code": "...", "repoName": "manual-test" }
     */
    @PostMapping("/manual")
    public ResponseEntity<Map<String, Object>> manualScan(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody ManualScanRequest request) {

        try {
            if (request.getFileName() == null || request.getFileName().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "fileName is required"));
            }
            if (request.getCode() == null || request.getCode().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "code is required"));
            }

            User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();

            // Run the scan engine
            log.info("Running manual scan for file: {} by user: {}", request.getFileName(), user.getUsername());
            List<VulnerabilityResult> vulns = scanEngineService.scan(request.getFileName(), request.getCode());
            log.info("Scan complete: {} vulnerabilities found", vulns.size());

            // Persist the scan record
            Scan scan = scanRepository.save(Scan.builder()
                    .developer(user)
                    .repoName(request.getRepoName() != null ? request.getRepoName() : "manual-scan")
                    .commitSha(UUID.randomUUID().toString().substring(0, 7))
                    .branch("manual")
                    .status(vulns.isEmpty() ? Scan.ScanStatus.PASS : Scan.ScanStatus.FAIL)
                    .totalVulnerabilities(vulns.size())
                    .build());

            // Link vulnerabilities to scan and persist
            for (VulnerabilityResult v : vulns) {
                v.setScan(scan);
            }
            if (!vulns.isEmpty()) {
                vulnerabilityRepository.saveAll(vulns);
            }

            // Trigger notifications
            try {
                notificationService.createScanNotification(user, scan);
            } catch (Exception e) {
                log.warn("Failed to create notification: {}", e.getMessage());
            }

            // Evaluate badges if clean
            List<Badge> newBadges = new ArrayList<>();
            if (scan.getStatus() == Scan.ScanStatus.PASS) {
                try {
                    newBadges = badgeService.evaluate(user, scan);
                } catch (Exception e) {
                    log.warn("Failed to evaluate badges: {}", e.getMessage());
                }
            }

            // Build response
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("scanId", scan.getId());
            result.put("status", scan.getStatus());
            result.put("totalVulnerabilities", vulns.size());
            result.put("vulnerabilities", vulns.stream().map(v -> {
                Map<String, Object> vm = new LinkedHashMap<>();
                vm.put("ruleId", v.getRuleId());
                vm.put("severity", v.getSeverity());
                vm.put("fileName", v.getFileName());
                vm.put("lineNumber", v.getLineNumber());
                vm.put("description", v.getDescription());
                vm.put("codeSnippet", v.getCodeSnippet());
                return vm;
            }).toList());
            result.put("newBadges", newBadges.stream().map(b -> b.getBadgeType().name()).toList());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Manual scan failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Scan error: " + e.getMessage()));
        }
    }

    @Data
    @NoArgsConstructor
    public static class ManualScanRequest {
        private String fileName;
        private String code;
        private String repoName;
    }
}

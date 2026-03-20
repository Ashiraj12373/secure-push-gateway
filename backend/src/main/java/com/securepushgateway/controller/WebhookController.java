package com.securepushgateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepushgateway.service.ScanOrchestrationService;
import com.securepushgateway.util.HmacValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final ScanOrchestrationService scanOrchestrationService;
    private final HmacValidator hmacValidator;
    private final ObjectMapper objectMapper;

    /**
     * GitHub Push Webhook endpoint.
     * Returns 200 immediately; scan runs asynchronously via @Async.
     */
    @PostMapping("/github")
    public ResponseEntity<String> receiveGitHubPush(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "push") String event,
            @RequestBody String rawPayload) {

        log.info("GitHub webhook received — event: {}", event);

        // Validate HMAC signature
        if (!hmacValidator.isValid(rawPayload, signature)) {
            log.warn("Invalid webhook signature — rejecting request");
            return ResponseEntity.status(401).body("Invalid signature");
        }

        // Only process push events
        if (!"push".equals(event)) {
            return ResponseEntity.ok("Event ignored: " + event);
        }

        try {
            JsonNode payload = objectMapper.readTree(rawPayload);
            // Fire-and-forget: scan runs async, we return 200 immediately
            scanOrchestrationService.processPushAsync(payload);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload", e);
            return ResponseEntity.badRequest().body("Invalid payload");
        }

        return ResponseEntity.ok("Accepted");
    }
}

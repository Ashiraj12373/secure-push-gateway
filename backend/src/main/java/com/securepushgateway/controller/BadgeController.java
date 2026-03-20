package com.securepushgateway.controller;

import com.securepushgateway.model.*;
import com.securepushgateway.repository.*;
import com.securepushgateway.service.BadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeRepository badgeRepository;
    private final UserRepository userRepository;
    private final BadgeService badgeService;
    private final ScanRepository scanRepository;

    @GetMapping("/{developerId}")
    public ResponseEntity<List<Badge>> getBadges(@PathVariable Long developerId) {
        User dev = userRepository.findById(developerId).orElseThrow();
        return ResponseEntity.ok(badgeRepository.findByDeveloper(dev));
    }

    @PostMapping("/evaluate/{developerId}")
    public ResponseEntity<List<Badge>> evaluateBadges(@PathVariable Long developerId) {
        User dev = userRepository.findById(developerId).orElseThrow();
        List<Scan> scans = scanRepository.findByDeveloperOrderByScannedAtDesc(dev);
        Scan latest = scans.isEmpty() ? null : scans.get(0);
        if (latest == null || latest.getStatus() != Scan.ScanStatus.PASS) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(badgeService.evaluate(dev, latest));
    }
}

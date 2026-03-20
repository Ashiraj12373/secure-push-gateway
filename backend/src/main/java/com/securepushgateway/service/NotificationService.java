package com.securepushgateway.service;

import com.securepushgateway.model.*;
import com.securepushgateway.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void createScanNotification(User developer, Scan scan) {
        boolean pass = scan.getStatus() == Scan.ScanStatus.PASS;
        String msg = pass
            ? "✅ Clean push to " + scan.getRepoName() + " (commit " + scan.getCommitSha().substring(0, 7) + ")"
            : "⚠️ " + scan.getTotalVulnerabilities() + " vulnerabilit"
                + (scan.getTotalVulnerabilities() == 1 ? "y" : "ies")
                + " found in " + scan.getRepoName()
                + " (commit " + scan.getCommitSha().substring(0, 7) + ")";

        notificationRepository.save(Notification.builder()
            .user(developer)
            .message(msg)
            .type(pass ? Notification.NotificationType.SCAN_PASS : Notification.NotificationType.SCAN_FAIL)
            .scanId(scan.getId())
            .read(false)
            .build());

        log.debug("Created scan notification for user {}", developer.getUsername());
    }

    public void createBadgeNotification(User developer, Badge badge) {
        String msg = "🏆 Badge earned: " + badge.getBadgeType().getDisplayName()
            + " — " + badge.getBadgeType().getDescription();

        notificationRepository.save(Notification.builder()
            .user(developer)
            .message(msg)
            .type(Notification.NotificationType.BADGE_AWARDED)
            .read(false)
            .build());

        log.debug("Created badge notification for user {}", developer.getUsername());
    }

    public long countUnread(User user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }
}

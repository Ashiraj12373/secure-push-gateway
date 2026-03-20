package com.securepushgateway.service;

import com.securepushgateway.model.*;
import com.securepushgateway.model.Badge.BadgeType;
import com.securepushgateway.model.Scan.ScanStatus;
import com.securepushgateway.repository.BadgeRepository;
import com.securepushgateway.repository.ScanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeService {

    private final BadgeRepository badgeRepository;
    private final ScanRepository scanRepository;

    /**
     * Evaluates and awards eligible badges after a clean scan.
     * Called only when scan status = PASS.
     */
    public List<Badge> evaluate(User developer, Scan cleanScan) {
        List<Badge> awarded = new ArrayList<>();
        Set<BadgeType> alreadyEarned = badgeRepository.findByDeveloper(developer)
            .stream().map(Badge::getBadgeType).collect(Collectors.toSet());

        List<Scan> allScans = scanRepository.findByDeveloperOrderByScannedAtAsc(developer);

        // FIRST_CLEAN_PUSH
        if (!alreadyEarned.contains(BadgeType.FIRST_CLEAN_PUSH)) {
            awarded.add(awardBadge(developer, BadgeType.FIRST_CLEAN_PUSH, cleanScan));
        }

        // Streak badges
        int streak = computeCurrentStreak(allScans);
        log.debug("Developer {} current streak: {}", developer.getUsername(), streak);

        if (streak >= 3 && !alreadyEarned.contains(BadgeType.STREAK_3)) {
            awarded.add(awardBadge(developer, BadgeType.STREAK_3, cleanScan));
        }
        if (streak >= 5 && !alreadyEarned.contains(BadgeType.STREAK_5)) {
            awarded.add(awardBadge(developer, BadgeType.STREAK_5, cleanScan));
        }
        if (streak >= 10 && !alreadyEarned.contains(BadgeType.STREAK_10)) {
            awarded.add(awardBadge(developer, BadgeType.STREAK_10, cleanScan));
        }

        // CLEAN_WEEK
        if (!alreadyEarned.contains(BadgeType.CLEAN_WEEK) && isCleanWeek(developer, allScans)) {
            awarded.add(awardBadge(developer, BadgeType.CLEAN_WEEK, cleanScan));
        }

        return awarded;
    }

    private Badge awardBadge(User developer, BadgeType type, Scan scan) {
        log.info("Awarding badge {} to developer {}", type, developer.getUsername());
        return badgeRepository.save(Badge.builder()
            .developer(developer)
            .badgeType(type)
            .scan(scan)
            .build());
    }

    /**
     * Counts consecutive PASS scans from the end of the scan history.
     * Streak breaks on first FAIL encountered from the tail.
     */
    private int computeCurrentStreak(List<Scan> scans) {
        int streak = 0;
        for (int i = scans.size() - 1; i >= 0; i--) {
            if (scans.get(i).getStatus() == ScanStatus.PASS) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    /**
     * Returns true if all scans in the current calendar week (Mon–Sun) are PASS.
     */
    private boolean isCleanWeek(User developer, List<Scan> allScans) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .withHour(0).withMinute(0).withSecond(0);
        LocalDateTime weekEnd = weekStart.plusDays(7);

        List<Scan> thisWeek = allScans.stream()
            .filter(s -> s.getScannedAt().isAfter(weekStart) && s.getScannedAt().isBefore(weekEnd))
            .collect(Collectors.toList());

        return !thisWeek.isEmpty()
            && thisWeek.stream().allMatch(s -> s.getStatus() == ScanStatus.PASS);
    }

    public int getCurrentStreak(User developer) {
        List<Scan> scans = scanRepository.findByDeveloperOrderByScannedAtAsc(developer);
        return computeCurrentStreak(scans);
    }
}

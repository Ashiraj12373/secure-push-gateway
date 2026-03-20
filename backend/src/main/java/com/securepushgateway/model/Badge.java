package com.securepushgateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "badges")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Badge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnoreProperties({"password", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "developer_id", nullable = false)
    private User developer;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_type", nullable = false)
    private BadgeType badgeType;

    @Column(name = "awarded_at")
    private LocalDateTime awardedAt;

    @JsonIgnoreProperties({"vulnerabilities", "developer", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id")
    private Scan scan;

    @PrePersist
    protected void onCreate() {
        this.awardedAt = LocalDateTime.now();
    }

    public enum BadgeType {
        FIRST_CLEAN_PUSH("First Clean Push", "Your first vulnerability-free commit!"),
        STREAK_3("On Fire x3", "3 consecutive clean pushes"),
        STREAK_5("Clean Streak x5", "5 consecutive clean pushes"),
        STREAK_10("Untouchable x10", "10 consecutive clean pushes — legendary!"),
        CLEAN_WEEK("Spotless Week", "Every push this calendar week was clean");

        private final String displayName;
        private final String description;

        BadgeType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
}

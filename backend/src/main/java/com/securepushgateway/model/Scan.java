package com.securepushgateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "scans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Scan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnoreProperties({"password", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "developer_id", nullable = false)
    private User developer;

    @Column(name = "repo_name", nullable = false)
    private String repoName;

    @Column(name = "commit_sha", nullable = false)
    private String commitSha;

    @Column(name = "branch")
    private String branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStatus status;

    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;

    @Column(name = "total_vulnerabilities")
    private int totalVulnerabilities;

    @OneToMany(mappedBy = "scan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<VulnerabilityResult> vulnerabilities;

    @PrePersist
    protected void onCreate() {
        this.scannedAt = LocalDateTime.now();
    }

    public enum ScanStatus {
        PASS, FAIL, PENDING
    }
}

package com.securepushgateway.service;

import com.securepushgateway.model.*;
import com.securepushgateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final UserRepository ownerRepository;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Async
    public void sendScanResultEmail(User developer, Scan scan, List<VulnerabilityResult> vulns) {
        try {
            if (scan.getStatus() == Scan.ScanStatus.FAIL) {
                sendFailEmail(developer, scan, vulns);
                notifyOwnerOfFailure(developer, scan, vulns);
            } else {
                sendPassEmail(developer, scan);
            }
        } catch (Exception e) {
            log.error("Failed to send scan result email to {}", developer.getEmail(), e);
        }
    }

    private void sendFailEmail(User developer, Scan scan, List<VulnerabilityResult> vulns)
            throws MessagingException {
        Context ctx = new Context();
        ctx.setVariable("developerName", developer.getUsername());
        ctx.setVariable("repoName", scan.getRepoName());
        ctx.setVariable("commitSha", scan.getCommitSha().substring(0, 7));
        ctx.setVariable("vulnCount", vulns.size());
        ctx.setVariable("criticalCount", countBySeverity(vulns, VulnerabilityResult.Severity.CRITICAL));
        ctx.setVariable("highCount", countBySeverity(vulns, VulnerabilityResult.Severity.HIGH));
        ctx.setVariable("vulnerabilities", vulns);
        ctx.setVariable("scanUrl", "https://your-app/scans/" + scan.getId());

        String html = templateEngine.process("email/scan-fail", ctx);
        sendHtmlEmail(developer.getEmail(), "⚠️ Security Issues Found in Your Push — " + scan.getRepoName(), html);
    }

    private void sendPassEmail(User developer, Scan scan) throws MessagingException {
        Context ctx = new Context();
        ctx.setVariable("developerName", developer.getUsername());
        ctx.setVariable("repoName", scan.getRepoName());
        ctx.setVariable("commitSha", scan.getCommitSha().substring(0, 7));
        ctx.setVariable("scanUrl", "https://your-app/scans/" + scan.getId());

        String html = templateEngine.process("email/scan-pass", ctx);
        sendHtmlEmail(developer.getEmail(), "✅ Clean Push — " + scan.getRepoName(), html);
    }

    private void notifyOwnerOfFailure(User developer, Scan scan, List<VulnerabilityResult> vulns)
            throws MessagingException {
        List<User> owners = ownerRepository.findByRole(User.Role.OWNER);
        for (User owner : owners) {
            Context ctx = new Context();
            ctx.setVariable("ownerName", owner.getUsername());
            ctx.setVariable("developerName", developer.getUsername());
            ctx.setVariable("repoName", scan.getRepoName());
            ctx.setVariable("vulnCount", vulns.size());
            ctx.setVariable("criticalCount", countBySeverity(vulns, VulnerabilityResult.Severity.CRITICAL));
            ctx.setVariable("scanUrl", "https://your-app/scans/" + scan.getId());

            String html = templateEngine.process("email/owner-alert", ctx);
            sendHtmlEmail(owner.getEmail(),
                "🚨 Security Violation by " + developer.getUsername() + " — " + scan.getRepoName(), html);
        }
    }

    @Async
    public void sendBadgeEmail(User developer, Badge badge) {
        try {
            Context ctx = new Context();
            ctx.setVariable("developerName", developer.getUsername());
            ctx.setVariable("badgeName", badge.getBadgeType().getDisplayName());
            ctx.setVariable("badgeDesc", badge.getBadgeType().getDescription());

            String html = templateEngine.process("email/badge-awarded", ctx);
            sendHtmlEmail(developer.getEmail(),
                "🏆 Badge Earned: " + badge.getBadgeType().getDisplayName(), html);
        } catch (Exception e) {
            log.error("Failed to send badge email to {}", developer.getEmail(), e);
        }
    }

    private void sendHtmlEmail(String to, String subject, String html) throws MessagingException {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(msg);
        log.info("Email sent to {} — subject: {}", to, subject);
    }

    private long countBySeverity(List<VulnerabilityResult> vulns, VulnerabilityResult.Severity sev) {
        return vulns.stream().filter(v -> v.getSeverity() == sev).count();
    }
}

package com.securepushgateway.controller;

import com.securepushgateway.model.User;
import com.securepushgateway.repository.UserRepository;
import com.securepushgateway.service.BadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/developers")
@RequiredArgsConstructor
public class DeveloperController {

    private final UserRepository userRepository;
    private final BadgeService badgeService;

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<Map<String, Object>>> getAllDevelopers() {
        List<User> devs = userRepository.findByRole(User.Role.DEVELOPER);
        List<Map<String, Object>> result = devs.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("username", d.getUsername());
            m.put("email", d.getEmail());
            m.put("streak", badgeService.getCurrentStreak(d));
            m.put("createdAt", d.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDeveloper(@PathVariable Long id) {
        return userRepository.findById(id).map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
            m.put("role", u.getRole());
            m.put("createdAt", u.getCreatedAt());
            return ResponseEntity.ok(m);
        }).orElse(ResponseEntity.notFound().build());
    }
}

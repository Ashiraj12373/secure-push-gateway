package com.securepushgateway.controller;

import com.securepushgateway.model.User;
import com.securepushgateway.repository.UserRepository;
import com.securepushgateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> body) {
        authManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                body.get("username"), body.get("password")));
        User user = userRepository.findByUsername(body.get("username")).orElseThrow();
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        return ResponseEntity.ok(Map.of("token", token, "role", user.getRole().name(), "userId", user.getId().toString()));
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody Map<String, String> body) {
        if (userRepository.findByUsername(body.get("username")).isPresent()) {
            return ResponseEntity.badRequest().body("Username already taken");
        }
        userRepository.save(User.builder()
            .username(body.get("username"))
            .email(body.get("email"))
            .password(passwordEncoder.encode(body.get("password")))
            .role(User.Role.DEVELOPER)
            .build());
        return ResponseEntity.ok("Registered");
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal UserDetails principal) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("email", user.getEmail());
        info.put("role", user.getRole());
        return ResponseEntity.ok(info);
    }
}

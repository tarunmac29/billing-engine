package com.paycycle.billing_engine.api.controller;

import com.paycycle.billing_engine.api.dto.request.LoginRequest;
import com.paycycle.billing_engine.api.dto.response.AuthResponse;
import com.paycycle.billing_engine.domain.repository.TenantRepository;
import com.paycycle.billing_engine.infrastructure.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — Login endpoint.
 *
 * POST /v1/auth/login
 *   → email + password + tenantId
 *   → JWT token return karo
 *
 * ============================================================
 * NOTE: Ye simplified version hai.
 * Production mein:
 *   - Password BCrypt se hash hoga
 *   - User table hogi (abhi sirf tenant check kar rahe hain)
 *   - Refresh token bhi hoga
 * ============================================================
 *
 * Dev ke liye ek hardcoded admin user hai:
 *   email: admin@paycycle.com
 *   password: admin123
 */
@Slf4j
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;
    private final TenantRepository tenantRepository;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        log.info("Login attempt for: {} tenant: {}",
            request.getEmail(), request.getTenantId());

        // Tenant exist karta hai?
        tenantRepository.findById(request.getTenantId())
            .orElseThrow(() -> new RuntimeException("Tenant not found"));

        // Dev mein simple password check
        // Production mein: passwordEncoder.matches() use karo
        if (!isValidCredentials(request.getEmail(), request.getPassword())) {
            return ResponseEntity.status(401).build();
        }

        // Role determine karo
        String role = request.getEmail().contains("admin") ? "ADMIN" : "USER";

        // JWT generate karo
        String token = jwtService.generateToken(
            request.getEmail(),
            request.getTenantId(),
            role
        );

        log.info("Login successful for: {}", request.getEmail());

        return ResponseEntity.ok(AuthResponse.builder()
            .accessToken(token)
            .tokenType("Bearer")
            .expiresIn(86400)  // 24 hours in seconds
            .email(request.getEmail())
            .tenantId(request.getTenantId())
            .role(role)
            .build());
    }

    // Dev only — hardcoded credentials
    // Production mein: DB se user load karo, BCrypt verify karo
    private boolean isValidCredentials(String email, String password) {
        return (email.equals("admin@paycycle.com") && password.equals("admin123"))
            || (email.equals("user@paycycle.com") && password.equals("user123"));
    }
}

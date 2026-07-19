package com.paycycle.billing_engine.config;

import com.paycycle.billing_engine.infrastructure.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig — JWT ke saath STATELESS security.
 *
 * ============================================================
 * STATELESS kya matlab hai?
 * ============================================================
 * Traditional: Server session store karta hai
 *   Login → Server: "session-123 = user@email.com" DB mein save
 *   Har request: "session-123 kaun hai?" DB check
 *   Problem: 10 lakh users = 10 lakh DB calls!
 *
 * JWT Stateless: Server kuch store nahi karta
 *   Login → Server: JWT token generate, client ko de do
 *   Har request: Token verify karo (sirf math, no DB)
 *   Benefit: 10 lakh users = 0 extra DB calls!
 * ============================================================
 *
 * Public routes (token nahi chahiye):
 *   - POST /v1/auth/login
 *   - GET  /actuator/health
 *
 * Protected routes (token chahiye):
 *   - /v1/tenants/** (saare business APIs)
 *   - /v1/internal/** (billing trigger)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF disable — REST APIs stateless hote hain
            .csrf(AbstractHttpConfigurer::disable)

            // STATELESS — server koi session nahi rakhega
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Route permissions
            .authorizeHttpRequests(auth -> auth
                // Public routes — token nahi chahiye
                .requestMatchers("/v1/auth/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // Internal billing trigger — ADMIN only
                .requestMatchers("/v1/internal/**").hasRole("ADMIN")
                // Baaki sab — authenticated hona chahiye
                .anyRequest().authenticated()
            )

            // JWT filter add karo — UsernamePasswordAuthFilter SE PEHLE
            // Matlab: pehle JWT check hoga, phir baaki security
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

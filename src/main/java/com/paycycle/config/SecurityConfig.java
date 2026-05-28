package com.paycycle.billing_engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityConfig — Phase 2 ke liye temporary config.
 *
 * Abhi ke liye saare API calls allow karte hain —
 * Phase 3 mein JWT authentication lagayenge.
 *
 * PRODUCTION MEIN YE KABHI MAT KARNA!
 * Ye sirf development aur testing ke liye hai.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF disable — REST APIs mein normally disable hota hai
            .csrf(AbstractHttpConfigurer::disable)
            // Abhi sab allow karo — Phase 3 mein JWT lagayenge
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/v1/**").permitAll()
                .anyRequest().permitAll()
            );

        return http.build();
    }
}

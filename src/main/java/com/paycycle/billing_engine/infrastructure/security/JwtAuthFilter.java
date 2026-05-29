package com.paycycle.billing_engine.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JwtAuthFilter — Har request mein JWT check karo.
 *
 * ============================================================
 * OncePerRequestFilter — ye filter sirf EK baar chalega
 * har request ke liye (Spring guarantee karta hai).
 * ============================================================
 *
 * Flow:
 * 1. Request aati hai
 * 2. "Authorization" header dhundho
 * 3. "Bearer " prefix ke baad token nikalo
 * 4. Token validate karo (JwtService se)
 * 5. Valid? → SecurityContext mein user set karo
 * 6. Invalid? → request aage jaane do (anonymous user)
 *    (SecurityConfig decide karega access dena hai ya nahi)
 *
 * ============================================================
 * SecurityContext kya hai?
 * ============================================================
 * Ek thread-local storage jahan current user ki info hoti hai.
 * Jab hum yahan user set karte hain, Spring Security samajh
 * jaata hai ki ye request authenticated hai.
 * Controller mein @AuthenticationPrincipal se access kar sakte.
 * ============================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Step 1: Authorization header dhundho
        String authHeader = request.getHeader("Authorization");

        // Header nahi hai ya Bearer se start nahi hota?
        // → Skip karo, agle filter pe jaao
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 2: "Bearer " (7 chars) ke baad token nikalo
        String token = authHeader.substring(7);

        try {
            // Step 3: Token validate karo
            if (jwtService.isTokenValid(token)) {

                // Step 4: Claims nikalo
                String email    = jwtService.extractEmail(token);
                String tenantId = jwtService.extractTenantId(token);
                String role     = jwtService.extractRole(token);

                log.debug("JWT valid for: {} tenant: {}", email, tenantId);

                // Step 5: Spring Security ko batao — ye user authenticated hai
                // Authorities = permissions (ROLE_ADMIN, ROLE_USER etc.)
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

                // Authentication object banao
                var authentication = new UsernamePasswordAuthenticationToken(
                    email,       // principal (user identifier)
                    null,        // credentials (password nahi chahiye JWT ke baad)
                    authorities  // permissions
                );

                // Request details add karo (IP address etc.)
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // SecurityContext mein set karo
                // Ab ye request "authenticated" hai
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // TenantId request attribute mein rakho
                // Controllers mein use kar sakte hain
                request.setAttribute("tenantId", tenantId);
            }
        } catch (Exception e) {
            log.warn("JWT processing failed: {}", e.getMessage());
            // Exception aaye toh SecurityContext clear karo
            SecurityContextHolder.clearContext();
        }

        // Agle filter pe jaao
        filterChain.doFilter(request, response);
    }
}

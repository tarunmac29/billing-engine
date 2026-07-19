package com.paycycle.billing_engine.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycycle.billing_engine.api.dto.request.CreatePlanRequest;
import com.paycycle.billing_engine.api.dto.response.PlanResponse;
import com.paycycle.billing_engine.api.exception.ResourceNotFoundException;
import com.paycycle.billing_engine.application.service.PlanService;
import com.paycycle.billing_engine.config.SecurityConfig;
import com.paycycle.billing_engine.domain.enums.BillingInterval;
import com.paycycle.billing_engine.infrastructure.security.JwtAuthFilter;
import com.paycycle.billing_engine.infrastructure.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PlanControllerTest — REST API test.
 *
 * ============================================================
 * @WebMvcTest — sirf web layer load hoti hai
 * Full Spring context nahi — fast test!
 * Tomcat nahi chalta — MockMvc HTTP simulate karta hai
 * ============================================================
 *
 * Kya test karte hain:
 * - HTTP status codes (200, 201, 400, 404)
 * - Response JSON format
 * - Validation errors
 * - Exception handling (GlobalExceptionHandler)
 */
@WebMvcTest(PlanController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
@DisplayName("PlanController Tests")
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlanService planService;

    @MockBean
    private JwtService jwtService;

    private static final String TENANT_ID = "a0000000-0000-0000-0000-000000000001";
    private static final String BASE_URL = "/v1/tenants/" + TENANT_ID + "/plans";

    // Valid JWT token mock
    private static final String VALID_TOKEN = "Bearer valid-token";

    private PlanResponse samplePlanResponse() {
        return PlanResponse.builder()
            .id("plan-123")
            .name("Basic Monthly")
            .price(new BigDecimal("99.00"))
            .currency("INR")
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("POST /plans — Plan successfully create hona chahiye (201)")
    void shouldCreatePlan_andReturn201() throws Exception {
        // Arrange
        CreatePlanRequest request = new CreatePlanRequest();
        request.setName("Basic Monthly");
        request.setPrice(new BigDecimal("99.00"));
        request.setCurrency("INR");
        request.setBillingInterval(BillingInterval.MONTHLY);

        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("admin@paycycle.com");
        when(jwtService.extractTenantId("valid-token")).thenReturn(TENANT_ID);
        when(jwtService.extractRole("valid-token")).thenReturn("ADMIN");
        when(planService.createPlan(eq(TENANT_ID), any()))
            .thenReturn(samplePlanResponse());

        // Act & Assert
        mockMvc.perform(post(BASE_URL)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())           // 201
            .andExpect(jsonPath("$.id").value("plan-123"))
            .andExpect(jsonPath("$.name").value("Basic Monthly"))
            .andExpect(jsonPath("$.currency").value("INR"))
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("POST /plans — Name missing ho toh 400 aana chahiye")
    void shouldReturn400_whenNameIsMissing() throws Exception {
        // Arrange — name field nahi hai
        CreatePlanRequest request = new CreatePlanRequest();
        request.setPrice(new BigDecimal("99.00")); // name missing!

        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("admin@paycycle.com");
        when(jwtService.extractTenantId("valid-token")).thenReturn(TENANT_ID);
        when(jwtService.extractRole("valid-token")).thenReturn("ADMIN");

        // Act & Assert
        mockMvc.perform(post(BASE_URL)
                .header("Authorization", VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())        // 400
            .andExpect(jsonPath("$.error").value("Validation Failed"))
            .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    @DisplayName("GET /plans — Saare active plans milne chahiye (200)")
    void shouldGetAllPlans_andReturn200() throws Exception {
        // Arrange
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("admin@paycycle.com");
        when(jwtService.extractTenantId("valid-token")).thenReturn(TENANT_ID);
        when(jwtService.extractRole("valid-token")).thenReturn("ADMIN");
        when(planService.getActivePlans(TENANT_ID))
            .thenReturn(List.of(samplePlanResponse()));

        // Act & Assert
        mockMvc.perform(get(BASE_URL)
                .header("Authorization", VALID_TOKEN))
            .andExpect(status().isOk())                // 200
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").value("plan-123"));
    }

    @Test
    @DisplayName("GET /plans/{id} — Plan nahi mila toh 404 aana chahiye")
    void shouldReturn404_whenPlanNotFound() throws Exception {
        // Arrange
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("admin@paycycle.com");
        when(jwtService.extractTenantId("valid-token")).thenReturn(TENANT_ID);
        when(jwtService.extractRole("valid-token")).thenReturn("ADMIN");
        when(planService.getPlan(TENANT_ID, "wrong-id"))
            .thenThrow(new ResourceNotFoundException("Plan", "wrong-id"));

        // Act & Assert
        mockMvc.perform(get(BASE_URL + "/wrong-id")
                .header("Authorization", VALID_TOKEN))
            .andExpect(status().isNotFound())          // 404
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Token nahi hai toh 403 aana chahiye")
    void shouldReturn403_whenNoToken() throws Exception {
        // Act & Assert — Authorization header nahi bheja
        mockMvc.perform(get(BASE_URL))
            .andExpect(status().isForbidden()); // 403
    }
}

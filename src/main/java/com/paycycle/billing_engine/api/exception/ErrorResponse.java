package com.paycycle.billing_engine.api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ErrorResponse — Clean error format.
 *
 * @JsonInclude(NON_NULL) — null fields JSON mein nahi aayenge.
 * fieldErrors sirf validation errors mein hoga.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    private int status;
    private String error;
    private String message;
    private Map<String, String> fieldErrors;

    public static ErrorResponse of(int status, String error, String message) {
        return ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(status)
            .error(error)
            .message(message)
            .build();
    }
}

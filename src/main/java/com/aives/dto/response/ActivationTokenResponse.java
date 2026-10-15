package com.aives.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivationTokenResponse {

    private String token;
    private String email;
    private String fullName;
    private Instant expiresAt;
    private String message;
}

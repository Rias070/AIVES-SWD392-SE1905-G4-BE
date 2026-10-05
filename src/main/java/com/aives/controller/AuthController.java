package com.aives.controller;

import com.aives.dto.request.AuthRequest;
import com.aives.dto.request.UserCreateRequest;
import com.aives.dto.response.AuthResponse;
import com.aives.response.ApiResponse;
import com.aives.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Public authentication and registration endpoints")
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    @Operation(summary = "Login to system", description = "Authenticate with email and password to receive JWT Bearer token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        log.info("POST /api/v1/auth/login: email={}", request.getEmail());
        AuthResponse response = authenticationService.login(request);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .code(1000)
                .message("Login successfully")
                .result(response)
                .build());
    }

    @PostMapping("/register")
    @Operation(summary = "Register new account", description = "Self-registration for new students/lecturers")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody UserCreateRequest request) {
        log.info("POST /api/v1/auth/register: email={}", request.getEmail());
        AuthResponse response = authenticationService.register(request);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .code(1000)
                .message("Register account successfully")
                .result(response)
                .build());
    }
}

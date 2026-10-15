package com.aives.controller;

import com.aives.application.port.in.AuthenticationUseCase;
import com.aives.dto.request.ActivationRequest;
import com.aives.dto.request.AuthRequest;
import com.aives.dto.request.UserCreateRequest;
import com.aives.dto.response.ActivationTokenResponse;
import com.aives.dto.response.AuthResponse;
import com.aives.response.ApiResponse;
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

    private final AuthenticationUseCase authenticationService;
    private final com.aives.application.port.out.NotificationPort emailService;

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
    @Operation(summary = "Register new account",
            description = "Self-registration for students/lecturers — requires email activation. Admin role gets ACTIVE immediately.")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody UserCreateRequest request) {
        log.info("POST /api/v1/auth/register: email={}", request.getEmail());
        AuthResponse response = authenticationService.register(request);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .code(1000)
                .message("Register account successfully")
                .result(response)
                .build());
    }

    @GetMapping("/activate")
    @Operation(summary = "Activate account via email link",
            description = "Verify JWT activation token and activate the account. Call this when user clicks the link in their email.")
    public ResponseEntity<ApiResponse<ActivationTokenResponse>> activate(@RequestParam String token) {
        log.info("GET /api/v1/auth/activate: token received");
        ActivationTokenResponse result = authenticationService.activateAccount(token);
        return ResponseEntity.ok(ApiResponse.<ActivationTokenResponse>builder()
                .code(1000)
                .message(result.getMessage())
                .result(result)
                .build());
    }

    @PostMapping("/resend-activation")
    @Operation(summary = "Resend activation email",
            description = "Request a new activation email if the original link expired or was not received.")
    public ResponseEntity<ApiResponse<String>> resendActivation(@Valid @RequestBody ActivationRequest request) {
        log.info("POST /api/v1/auth/resend-activation: email={}", request.getEmail());
        authenticationService.resendActivationEmail(request.getEmail());
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .code(1000)
                .message("Email kích hoạt mới đã được gửi. Vui lòng kiểm tra hộp thư (bao gồm thư rác).")
                .result("Email kích hoạt đã được gửi.")
                .build());
    }

    @GetMapping("/test-mail")
    @Operation(summary = "Test sending email", description = "Trigger a test registration welcome email to the specified address")
    public ResponseEntity<ApiResponse<String>> testMail(@RequestParam String to) {
        log.info("GET /api/v1/auth/test-mail: to={}", to);
        emailService.sendRegistrationWelcomeEmail(to, "Người Dùng Thử Nghiệm", "STUDENT", "SE190001");
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .code(1000)
                .message("Lệnh gửi email chào mừng đã được khởi tạo thành công tới: " + to)
                .result("Dispatched to " + to)
                .build());
    }
}

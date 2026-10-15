package com.aives.application.port.in;

import com.aives.dto.request.AuthRequest;
import com.aives.dto.request.UserCreateRequest;
import com.aives.dto.response.AuthResponse;

public interface AuthenticationUseCase {
    AuthResponse login(AuthRequest request);
    AuthResponse register(UserCreateRequest request);
    com.aives.dto.response.ActivationTokenResponse activateAccount(String token);
    void resendActivationEmail(String email);
}

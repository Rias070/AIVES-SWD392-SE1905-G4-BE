package com.aives.service;

import com.aives.dto.request.AuthRequest;
import com.aives.dto.request.UserCreateRequest;
import com.aives.dto.response.AuthResponse;
import com.aives.dto.response.UserResponse;
import com.aives.entity.User;
import com.aives.entity.UserRole;
import com.aives.enums.ErrorCode;
import com.aives.enums.RoleEnum;
import com.aives.enums.UserStatus;
import com.aives.exception.AppException;
import com.aives.repository.UserRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final UserManagementService userManagementService;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.signer-key}")
    private String signerKey;

    @Value("${jwt.expiration:3600}")
    private long validDuration;

    public AuthResponse login(AuthRequest request) {
        log.info("Processing login request for email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED, "User not found with email: " + request.getEmail()));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Account is inactive or pending verification.");
        }

        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Invalid email or password.");
        }

        String token = generateToken(user);
        UserResponse userResponse = userManagementService.getUser(user.getUuid());

        return AuthResponse.builder()
                .token(token)
                .authenticated(true)
                .user(userResponse)
                .build();
    }

    public AuthResponse register(UserCreateRequest request) {
        log.info("Processing self-registration for email: {}", request.getEmail());
        
        // Default self-registered role to STUDENT if not provided or empty
        if (request.getRoleName() == null || request.getRoleName().isBlank()) {
            request.setRoleName(RoleEnum.STUDENT.name());
        }

        UserResponse createdUser = userManagementService.createUser(request);

        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        String token = generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .authenticated(true)
                .user(createdUser)
                .build();
    }

    private String generateToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        List<String> rolesList = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getName())
                .collect(Collectors.toList());

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUuid().toString())
                .issuer("aives.edu.vn")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plus(validDuration, ChronoUnit.SECONDS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .claim("userCode", user.getUserCode())
                .claim("roles", rolesList)
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(signerKey.getBytes()));
            return jwsObject.serialize();
        } catch (Exception e) {
            log.error("Cannot create JWT token", e);
            throw new RuntimeException("Error signing JWT token", e);
        }
    }
}

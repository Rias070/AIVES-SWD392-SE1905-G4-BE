package com.aives.service;

import com.aives.dto.request.AuthRequest;
import com.aives.dto.request.UserCreateRequest;
import com.aives.dto.response.ActivationTokenResponse;
import com.aives.dto.response.AuthResponse;
import com.aives.dto.response.UserResponse;
import com.aives.entity.User;
import com.aives.enums.ErrorCode;
import com.aives.enums.RoleEnum;
import com.aives.enums.UserStatus;
import com.aives.exception.AppException;
import com.aives.repository.UserRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.aives.application.port.in.AuthenticationUseCase;
import com.aives.application.port.in.UserManagementUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService implements AuthenticationUseCase {

    private final UserRepository userRepository;
    private final UserManagementUseCase userManagementService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${jwt.signer-key}")
    private String signerKey;

    @Value("${jwt.expiration:3600}")
    private long validDuration;

    @Value("${jwt.activation-expiration:86400}")
    private long activationExpiration;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    // ==========================================
    // LOGIN
    // ==========================================
    public AuthResponse login(AuthRequest request) {
        log.info("Processing login request for email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED,
                        "User not found with email: " + request.getEmail()));

        // Check PENDING_EMAIL first — must activate via email first
        if (user.getStatus() == UserStatus.PENDING_EMAIL) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVATED,
                    "Hãy kích hoạt tài khoản trong email đã đăng ký trước khi đăng nhập.");
        }

        // Check LOCKED
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED,
                    "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        // Check INACTIVE
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE,
                    "Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ quản trị viên.");
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

    // ==========================================
    // REGISTER (Self-registration)
    // ==========================================
    @Transactional
    public AuthResponse register(UserCreateRequest request) {
        log.info("Processing self-registration for email: {}", request.getEmail());

        // Default self-registered role to STUDENT if not provided or empty
        if (request.getRoleName() == null || request.getRoleName().isBlank()) {
            request.setRoleName(RoleEnum.STUDENT.name());
        }

        boolean isAdminRegistration = RoleEnum.ADMIN.name().equalsIgnoreCase(request.getRoleName());

        // ADMIN registration → ACTIVE immediately, no email activation needed
        if (isAdminRegistration) {
            log.info("Admin-level registration detected for email: {}", request.getEmail());
            UserResponse createdUser = userManagementService.createUser(request);

            User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

            String token = generateToken(user);

            // Send account creation email (no activation needed for admin)
            try {
                emailService.sendAccountCreationEmail(
                        user.getEmail(),
                        user.getFullName(),
                        request.getRoleName(),
                        user.getUserCode(),
                        request.getPassword()
                );
            } catch (Exception e) {
                log.error("Failed to send account creation email to {}: {}", user.getEmail(), e.getMessage());
            }

            return AuthResponse.builder()
                    .token(token)
                    .authenticated(true)
                    .user(createdUser)
                    .build();
        }

        // STUDENT / LECTURER → PENDING_EMAIL status
        log.info("Self-registration for email: {} with role: {} → status = PENDING_EMAIL",
                request.getEmail(), request.getRoleName());

        // Set status to PENDING_EMAIL before creating user
        request.setStatus(UserStatus.PENDING_EMAIL);

        UserResponse createdUser = userManagementService.createUser(request);

        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // Generate activation token and send activation email
        String activationToken = generateActivationToken(user);
        String activationLink = frontendUrl + "/activate?token=" + activationToken;

        try {
            emailService.sendAccountActivationEmail(
                    user.getEmail(),
                    user.getFullName(),
                    request.getRoleName(),
                    activationLink
            );
            log.info("Activation email sent to: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send activation email to {}: {}", user.getEmail(), e.getMessage());
            // Don't fail registration if email fails — user can request resend
        }

        // Return response WITHOUT JWT token — user must activate first
        return AuthResponse.builder()
                .token(null)
                .authenticated(false)
                .user(createdUser)
                .build();
    }

    // ==========================================
    // ACTIVATE ACCOUNT (from email link)
    // ==========================================
    @Transactional
    public ActivationTokenResponse activateAccount(String token) {
        log.info("Processing account activation with token.");

        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(token);
        } catch (Exception e) {
            log.error("Invalid activation token format: {}", e.getMessage());
            throw new AppException(ErrorCode.ACTIVATION_TOKEN_INVALID,
                    "Liên kết kích hoạt không hợp lệ. Vui lòng yêu cầu gửi lại email.");
        }

        // Verify signature
        try {
            if (!signedJWT.verify(new MACVerifier(signerKey.getBytes()))) {
                throw new AppException(ErrorCode.ACTIVATION_TOKEN_INVALID,
                        "Liên kết kích hoạt không hợp lệ. Vui lòng yêu cầu gửi lại email.");
            }
        } catch (JOSEException e) {
            log.error("JWT verification failed: {}", e.getMessage());
            throw new AppException(ErrorCode.ACTIVATION_TOKEN_INVALID,
                    "Liên kết kích hoạt không hợp lệ. Vui lòng yêu cầu gửi lại email.");
        }

        JWTClaimsSet claims;
        try {
            claims = signedJWT.getJWTClaimsSet();
        } catch (Exception e) {
            throw new AppException(ErrorCode.ACTIVATION_TOKEN_INVALID,
                    "Liên kết kích hoạt không hợp lệ. Vui lòng yêu cầu gửi lại email.");
        }

        // Verify purpose claim
        String purpose;
        try {
            purpose = claims.getStringClaim("purpose");
        } catch (Exception e) {
            purpose = null;
        }
        if (!"ACTIVATION".equals(purpose)) {
            log.warn("Activation token has wrong purpose: {}", purpose);
            throw new AppException(ErrorCode.ACTIVATION_TOKEN_INVALID,
                    "Liên kết kích hoạt không hợp lệ. Vui lòng yêu cầu gửi lại email.");
        }

        // Check expiration
        try {
            if (claims.getExpirationTime().before(new Date())) {
                throw new AppException(ErrorCode.ACTIVATION_TOKEN_EXPIRED,
                        "Liên kết kích hoạt đã hết hạn (sau 24 giờ). Vui lòng yêu cầu gửi lại email.");
            }
        } catch (Exception e) {
            if (e instanceof AppException) throw (AppException) e;
            throw new AppException(ErrorCode.ACTIVATION_TOKEN_INVALID,
                    "Liên kết kích hoạt không hợp lệ. Vui lòng yêu cầu gửi lại email.");
        }

        // Get user from subject claim
        String userUuid;
        try {
            userUuid = claims.getSubject();
        } catch (Exception e) {
            throw new AppException(ErrorCode.ACTIVATION_TOKEN_INVALID,
                    "Liên kết kích hoạt không hợp lệ. Vui lòng yêu cầu gửi lại email.");
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(userUuid);
        } catch (Exception e) {
            throw new AppException(ErrorCode.ACTIVATION_TOKEN_INVALID,
                    "Liên kết kích hoạt không hợp lệ. Vui lòng yêu cầu gửi lại email.");
        }

        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED,
                        "Tài khoản không tồn tại."));

        // Check current status
        if (user.getStatus() == UserStatus.ACTIVE) {
            log.info("User {} already active, returning success message.", user.getEmail());
            return ActivationTokenResponse.builder()
                    .message("Tài khoản đã được kích hoạt trước đó. Bạn có thể đăng nhập ngay.")
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .build();
        }

        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.INACTIVE) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Tài khoản đang bị khóa hoặc vô hiệu hóa. Không thể kích hoạt. Vui lòng liên hệ quản trị viên.");
        }

        if (user.getStatus() != UserStatus.PENDING_EMAIL) {
            throw new AppException(ErrorCode.ACTIVATION_TOKEN_INVALID,
                    "Trạng thái tài khoản không hợp lệ để kích hoạt.");
        }

        // Activate the account
        user.setStatus(UserStatus.ACTIVE);
        user.setActivatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Account activated successfully for email: {}", user.getEmail());

        // Get role name for email
        String roleName = user.getUserRoles().stream()
                .findFirst()
                .map(ur -> ur.getRole().getName())
                .orElse("STUDENT");

        // Send activation success email
        try {
            emailService.sendActivationSuccessEmail(user.getEmail(), user.getFullName(), roleName);
        } catch (Exception e) {
            log.error("Failed to send activation success email to {}: {}", user.getEmail(), e.getMessage());
        }

        return ActivationTokenResponse.builder()
                .message("Kích hoạt tài khoản thành công! Bạn có thể đăng nhập ngay.")
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    // ==========================================
    // RESEND ACTIVATION EMAIL
    // ==========================================
    public void resendActivationEmail(String email) {
        log.info("Processing resend activation email for: {}", email);

        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED,
                        "Không tìm thấy tài khoản với email: " + email));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_ACTIVATED,
                    "Tài khoản đã được kích hoạt trước đó. Bạn có thể đăng nhập ngay.");
        }

        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.INACTIVE) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Tài khoản đang bị khóa hoặc vô hiệu hóa. Không thể gửi lại email kích hoạt.");
        }

        // Generate new activation token
        String newToken = generateActivationToken(user);
        String activationLink = frontendUrl + "/activate?token=" + newToken;

        String roleName = user.getUserRoles().stream()
                .findFirst()
                .map(ur -> ur.getRole().getName())
                .orElse("STUDENT");

        try {
            emailService.sendAccountActivationEmail(
                    user.getEmail(),
                    user.getFullName(),
                    roleName,
                    activationLink
            );
            log.info("New activation email sent to: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to resend activation email to {}: {}", user.getEmail(), e.getMessage());
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED,
                    "Gửi email kích hoạt thất bại. Vui lòng thử lại sau.");
        }
    }

    // ==========================================
    // TOKEN GENERATION HELPERS
    // ==========================================

    /**
     * Generate standard authentication JWT token (HS512).
     */
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

    /**
     * Generate activation JWT token (HS512) with purpose=ACTIVATION, exp=24h.
     */
    private String generateActivationToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        Instant expiresAt = Instant.now().plus(activationExpiration, ChronoUnit.SECONDS);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUuid().toString())
                .issuer("aives.edu.vn")
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("purpose", "ACTIVATION")
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(signerKey.getBytes()));
            return jwsObject.serialize();
        } catch (Exception e) {
            log.error("Cannot create activation token for user: {}", user.getEmail(), e);
            throw new RuntimeException("Error signing activation token", e);
        }
    }
}

package com.aives.configuration;

import com.aives.enums.RoleEnum;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/**"
    };

    public SecurityConfig(CorsConfigurationSource corsConfigurationSource,
                          JwtDecoder jwtDecoder,
                          JwtAuthenticationConverter jwtAuthenticationConverter) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request -> request
                        // Public auth endpoints
                        .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()

                        // Swagger / OpenAPI documentation
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // WebSocket for real-time viva exam rooms
                        .requestMatchers("/ws/**").permitAll()

                        // Payment webhooks / return status
                        .requestMatchers("/api/v1/payments/momo/ipn").permitAll()
                        .requestMatchers("/api/v1/payments/momo/status").permitAll()
                        .requestMatchers("/api/v1/payments/vnpay/status").permitAll()

                        // Admin endpoints
                        .requestMatchers("/api/v1/admin/**").hasRole(RoleEnum.ADMIN.name())

                        // Lecturer endpoints
                        .requestMatchers("/api/v1/lecturer/**").hasAnyRole(RoleEnum.ADMIN.name(), RoleEnum.LECTURER.name())

                        // Student endpoints
                        .requestMatchers("/api/v1/student/**").hasAnyRole(RoleEnum.ADMIN.name(), RoleEnum.STUDENT.name())

                        // Authenticated user profile
                        .requestMatchers("/api/v1/users/myinfo", "/api/v1/users/myinfo/**").authenticated()

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}

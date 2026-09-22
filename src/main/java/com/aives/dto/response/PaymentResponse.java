package com.aives.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID uuid;
    private UUID userId;
    private UUID examSessionId;
    private String subjectName;
    private String paymentMethod;
    private BigDecimal amount;
    private String transactionId;
    private String status;
    private LocalDateTime createdAt;
}

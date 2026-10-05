package com.aives.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VivaTurnResponse {
    private UUID uuid;
    private Integer turnOrder;
    private UUID questionId;
    private String questionContent;
    private String studentAnswer;
    private String audioTranscript;
    private String aiEvaluation;
    private BigDecimal score;
    private Integer turnDurationSeconds;
    private LocalDateTime createdAt;
}

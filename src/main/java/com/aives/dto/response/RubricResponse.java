package com.aives.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricResponse {
    private UUID uuid;
    private UUID subjectId;
    private UUID questionId;
    private String criterionName;
    private String description;
    private BigDecimal maxScore;
    private BigDecimal weight;
    private LocalDateTime createdAt;
}

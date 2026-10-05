package com.aives.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricRequest {
    private UUID subjectUuid;
    private UUID questionUuid;

    @NotBlank(message = "Criterion name is required")
    private String criterionName;

    private String description;

    @Builder.Default
    private BigDecimal maxScore = new BigDecimal("10.00");

    @Builder.Default
    private BigDecimal weight = new BigDecimal("1.00");
}

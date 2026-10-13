package com.aives.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LecturerFinalizeScoreRequest {

    @NotNull(message = "Final score is required")
    @DecimalMin(value = "0.0", message = "Score must be at least 0.0")
    @DecimalMax(value = "10.0", message = "Score cannot exceed 10.0")
    private BigDecimal finalScore;

    private String lecturerFeedback;
    private UUID examinerId;
}

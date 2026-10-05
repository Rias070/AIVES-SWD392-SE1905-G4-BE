package com.aives.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VivaTurnSubmitRequest {
    private UUID questionUuid;

    @NotNull(message = "Turn order is required")
    private Integer turnOrder;

    private String studentAnswer;
    private String audioTranscript;
    private Integer turnDurationSeconds;
}

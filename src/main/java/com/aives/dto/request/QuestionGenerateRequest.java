package com.aives.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionGenerateRequest {
    private UUID subjectUuid;
    private String subjectCode;
    private UUID documentUuid;

    private String topic; // E.g. "Session Management in Spring", "Dijkstra algorithm"

    @Builder.Default
    private String bloomLevel = "ALL"; // REMEMBER, UNDERSTAND, APPLY, ANALYZE, or ALL

    @Builder.Default
    private Integer count = 3; // Number of questions to generate

    @Builder.Default
    private String difficulty = "MEDIUM"; // EASY, MEDIUM, HARD
}

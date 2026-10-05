package com.aives.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionRequest {
    private UUID subjectUuid;
    private String subjectCode;

    private String questionCode;

    @NotBlank(message = "Question content is required")
    private String content;

    private String difficulty; // EASY, MEDIUM, HARD
    private String bloomLevel; // Bloom 1 - Nhớ, Bloom 2 - Hiểu, Bloom 3 - Vận dụng, Bloom 4 - Phân tích, etc.

    private String expectedAnswer;
    private String keywords;
    private String status; // ACTIVE, PENDING, APPROVED
}

package com.aives.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamStartRequest {
    private String sessionCode;
    private UUID subjectUuid;
    private String subjectCode;
}

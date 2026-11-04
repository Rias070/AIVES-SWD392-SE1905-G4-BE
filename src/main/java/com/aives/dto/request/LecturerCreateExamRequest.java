package com.aives.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LecturerCreateExamRequest {
    @NotBlank(message = "Session code is required")
    private String sessionCode;

    private UUID subjectUuid;
    private String subjectCode;
    private UUID studentUuid;

    private LocalDateTime scheduledAt;
    private LocalDateTime endedAt;
    private Integer durationMinutes;

    private List<UUID> questionUuids;
    private String notes;
}


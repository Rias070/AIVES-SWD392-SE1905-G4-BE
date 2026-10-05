package com.aives.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectAssignRequest {

    @NotNull(message = "Subject ID is required")
    private UUID subjectUuid;

    @NotNull(message = "Lecturer ID is required")
    private UUID lecturerUuid;

    @Builder.Default
    private Boolean canApproveRag = true;

    @Builder.Default
    private Boolean canEditRubric = true;
}

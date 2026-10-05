package com.aives.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectAssignmentResponse {
    private UUID id;
    private UUID subjectId;
    private String subjectCode;
    private String subjectName;
    private UUID lecturerId;
    private String lecturerName;
    private String lecturerEmail;
    private Boolean canApproveRAG;
    private Boolean canEditRubric;
    private LocalDateTime assignedAt;
}

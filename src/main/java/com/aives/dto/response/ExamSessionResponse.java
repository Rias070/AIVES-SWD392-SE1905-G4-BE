package com.aives.dto.response;

import com.aives.enums.ExamSessionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSessionResponse {
    private UUID id;
    private String sessionCode;
    private UUID subjectId;
    private String subjectCode;
    private String subjectName;
    private UUID studentId;
    private String studentName;
    private String studentEmail;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private ExamSessionStatus status;
    private BigDecimal totalScore;
    private String feedback;
    private Integer turnsCount;
    private String rubricBadge;
    private String badgeColor;
}

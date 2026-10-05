package com.aives.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {
    private UUID id;
    private String code;
    private UUID subjectId;
    private String subjectCode;
    private String subjectName;
    private String content;
    private String difficulty;
    private String bloomLevel;
    private String bloomColor;
    private String expectedAnswer;
    private List<String> keywords;
    private String vectorStatus;
    private String status;
    private Boolean isApproved;
    private Integer rubricCount;
    private LocalDateTime createdAt;
}

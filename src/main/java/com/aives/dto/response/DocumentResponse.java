package com.aives.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private UUID id;
    private UUID subjectId;
    private String subjectCode;
    private String subjectName;
    private String filename;
    private String uri;
    private String size;
    private String status;
    private String statusText;
    private Integer chunkCount;
    private LocalDateTime createdAt;
}

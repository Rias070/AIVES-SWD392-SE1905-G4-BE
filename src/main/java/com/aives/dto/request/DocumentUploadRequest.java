package com.aives.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadRequest {
    private UUID subjectUuid;
    private String subjectCode;

    @NotBlank(message = "File name is required")
    private String fileName;

    private String fileUrl;
    private String fileSize;
}

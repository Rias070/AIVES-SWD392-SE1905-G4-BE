package com.aives.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectResponse {
    private UUID uuid;
    private String code;
    private String name;
    private String description;
    private Integer credits;
    private Integer lecturerCount;
    private Integer questionCount;
    private Integer documentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.aives.dto.request;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VivaTurnWsRequest {
    private UUID sessionId;
    private UUID questionUuid;
    private String questionContent;
    private Integer turnOrder;
    private String studentTranscript;
    private String studentAnswer;
    private Integer turnDurationSeconds;
    private String examLang;
}

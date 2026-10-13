package com.aives.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VivaTurnWsResponse {
    private UUID sessionId;
    private Integer turnOrder;
    private BigDecimal suggestedScore;
    private String aiEvaluation;
    private String followupQuestion;
    private String bloomLevel;
    private List<String> ragContextSnippets;
    private boolean lastTurn;
    private String status;
}

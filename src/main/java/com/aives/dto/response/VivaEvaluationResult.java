package com.aives.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VivaEvaluationResult {
    public BigDecimal score;
    public String aiEvaluation;
    public String followupQuestion;
    public String bloomLevel;
}

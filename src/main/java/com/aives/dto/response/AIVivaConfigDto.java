package com.aives.dto.response;

import lombok.*;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIVivaConfigDto {
    @Builder.Default
    private String defaultLanguage = "vi-VN";

    @Builder.Default
    private Integer maxTurns = 5;

    @Builder.Default
    private Integer turnTimeoutSeconds = 90;

    @Builder.Default
    private Double passingScore = 5.0;

    @Builder.Default
    private String feedbackTone = "PROFESSIONAL_ACADEMIC";

    @Builder.Default
    private String sttModel = "Whisper-v3-Turbo";

    @Builder.Default
    private String ttsVoice = "vi-VN-Standard-A (Natural)";

    @Builder.Default
    private Double temperature = 0.3;

    @Builder.Default
    private Integer ragTopK = 3;

    private Integer maxFollowupTurns;

    private Integer maxAnswerTimeSec;

    @Builder.Default
    private Boolean isHumanInTheLoop = true;

    private Map<String, Object> extraParams;

    public Integer getMaxFollowupTurns() {
        return maxFollowupTurns != null ? maxFollowupTurns : maxTurns;
    }

    public Integer getMaxAnswerTimeSec() {
        return maxAnswerTimeSec != null ? maxAnswerTimeSec : turnTimeoutSeconds;
    }

    public Boolean getIsHumanInTheLoop() {
        return isHumanInTheLoop != null ? isHumanInTheLoop : true;
    }
}

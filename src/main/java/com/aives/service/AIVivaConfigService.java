package com.aives.service;

import com.aives.dto.response.AIVivaConfigDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AIVivaConfigService {

    private AIVivaConfigDto currentConfig = AIVivaConfigDto.builder()
            .defaultLanguage("vi-VN")
            .maxTurns(5)
            .turnTimeoutSeconds(90)
            .passingScore(5.0)
            .feedbackTone("PROFESSIONAL_ACADEMIC")
            .sttModel("Whisper-v3-Turbo")
            .ttsVoice("vi-VN-Standard-A (Natural)")
            .temperature(0.3)
            .ragTopK(3)
            .extraParams(new HashMap<>())
            .build();

    public AIVivaConfigDto getConfig() {
        return currentConfig;
    }

    public AIVivaConfigDto updateConfig(AIVivaConfigDto newConfig) {
        log.info("Updating AI Viva configuration: {}", newConfig);
        if (newConfig.getDefaultLanguage() != null) currentConfig.setDefaultLanguage(newConfig.getDefaultLanguage());
        if (newConfig.getMaxTurns() != null) currentConfig.setMaxTurns(newConfig.getMaxTurns());
        if (newConfig.getTurnTimeoutSeconds() != null) currentConfig.setTurnTimeoutSeconds(newConfig.getTurnTimeoutSeconds());
        if (newConfig.getPassingScore() != null) currentConfig.setPassingScore(newConfig.getPassingScore());
        if (newConfig.getFeedbackTone() != null) currentConfig.setFeedbackTone(newConfig.getFeedbackTone());
        if (newConfig.getSttModel() != null) currentConfig.setSttModel(newConfig.getSttModel());
        if (newConfig.getTtsVoice() != null) currentConfig.setTtsVoice(newConfig.getTtsVoice());
        if (newConfig.getTemperature() != null) currentConfig.setTemperature(newConfig.getTemperature());
        if (newConfig.getRagTopK() != null) currentConfig.setRagTopK(newConfig.getRagTopK());
        if (newConfig.getExtraParams() != null) currentConfig.setExtraParams(newConfig.getExtraParams());
        return currentConfig;
    }

    public Map<String, Object> getSttTtsSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("sttModel", currentConfig.getSttModel());
        settings.put("ttsVoice", currentConfig.getTtsVoice());
        settings.put("sampleRateHz", 16000);
        settings.put("noiseSuppression", true);
        settings.put("echoCancellation", true);
        return settings;
    }

    public Map<String, Object> updateSttTtsSettings(Map<String, Object> updates) {
        if (updates.containsKey("sttModel")) {
            currentConfig.setSttModel(String.valueOf(updates.get("sttModel")));
        }
        if (updates.containsKey("ttsVoice")) {
            currentConfig.setTtsVoice(String.valueOf(updates.get("ttsVoice")));
        }
        return getSttTtsSettings();
    }

    public AIVivaConfigDto resetToDefault() {
        log.info("Resetting AI Viva configuration to default");
        currentConfig = AIVivaConfigDto.builder()
                .defaultLanguage("vi-VN")
                .maxTurns(5)
                .turnTimeoutSeconds(90)
                .passingScore(5.0)
                .feedbackTone("PROFESSIONAL_ACADEMIC")
                .sttModel("Whisper-v3-Turbo")
                .ttsVoice("vi-VN-Standard-A (Natural)")
                .temperature(0.3)
                .ragTopK(3)
                .extraParams(new HashMap<>())
                .build();
        return currentConfig;
    }
}

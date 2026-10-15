package com.aives.application.port.in;

import com.aives.dto.response.AIVivaConfigDto;

import java.util.Map;

public interface AIVivaConfigUseCase {
    AIVivaConfigDto getConfig();
    AIVivaConfigDto updateConfig(AIVivaConfigDto newConfig);
    Map<String, Object> getSttTtsSettings();
    Map<String, Object> updateSttTtsSettings(Map<String, Object> updates);
    AIVivaConfigDto resetToDefault();
}

package com.aives.controller;

import com.aives.dto.response.AIVivaConfigDto;
import com.aives.response.ApiResponse;
import com.aives.service.AIVivaConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/config")
@RequiredArgsConstructor
@Tag(name = "Admin - AI Viva Configuration", description = "Endpoints for managing AI viva parameters, STT and TTS models")
public class AdminConfigController {

    private final AIVivaConfigService aiVivaConfigService;

    @GetMapping("/ai-viva")
    @Operation(summary = "Get current AI Viva configuration")
    public ResponseEntity<ApiResponse<AIVivaConfigDto>> getAIVivaConfig() {
        return ResponseEntity.ok(ApiResponse.<AIVivaConfigDto>builder()
                .code(1000)
                .result(aiVivaConfigService.getConfig())
                .build());
    }

    @PutMapping("/ai-viva")
    @Operation(summary = "Update AI Viva configuration")
    public ResponseEntity<ApiResponse<AIVivaConfigDto>> updateAIVivaConfig(@RequestBody AIVivaConfigDto config) {
        return ResponseEntity.ok(ApiResponse.<AIVivaConfigDto>builder()
                .code(1000)
                .message("Cập nhật tham số AI Viva thành công")
                .result(aiVivaConfigService.updateConfig(config))
                .build());
    }

    @GetMapping("/stt-tts")
    @Operation(summary = "Get Whisper STT and TTS voice settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSTTTTSSettings() {
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .result(aiVivaConfigService.getSttTtsSettings())
                .build());
    }

    @PutMapping("/stt-tts")
    @Operation(summary = "Update Whisper STT and TTS voice settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateSTTTTSSettings(
            @RequestBody Map<String, Object> settings) {
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Cập nhật cài đặt STT / TTS thành công")
                .result(aiVivaConfigService.updateSttTtsSettings(settings))
                .build());
    }

    @PostMapping("/ai-viva/reset")
    @Operation(summary = "Reset AI Viva parameters to system default")
    public ResponseEntity<ApiResponse<AIVivaConfigDto>> resetConfig() {
        return ResponseEntity.ok(ApiResponse.<AIVivaConfigDto>builder()
                .code(1000)
                .message("Đã khôi phục cài đặt AI mặc định")
                .result(aiVivaConfigService.resetToDefault())
                .build());
    }
}

package com.aives.controller;

import com.aives.application.port.in.SubjectUseCase;
import com.aives.dto.request.SubjectLanguageConfigDto;
import com.aives.dto.response.SubjectResponse;
import com.aives.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/subjects", "/api/subjects"})
@Tag(name = "Subjects - Public/Catalog & Examination Config", description = "Endpoints for subject catalog and examination language configuration")
public class SubjectController {

    private final SubjectUseCase subjectService;

    public SubjectController(SubjectUseCase subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    @Operation(summary = "Get list of active subjects")
    public ResponseEntity<ApiResponse<List<SubjectResponse>>> listSubjects() {
        return ResponseEntity.ok(ApiResponse.<List<SubjectResponse>>builder()
                .code(1000)
                .result(subjectService.getAllSubjects())
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get subject details")
    public ResponseEntity<ApiResponse<SubjectResponse>> getSubject(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.<SubjectResponse>builder()
                .code(1000)
                .result(subjectService.getSubjectById(id))
                .build());
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get subject details by course code")
    public ResponseEntity<ApiResponse<SubjectResponse>> getSubjectByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.<SubjectResponse>builder()
                .code(1000)
                .result(subjectService.getSubjectByCode(code))
                .build());
    }

    @GetMapping("/{id}/language-config")
    @Operation(summary = "Get subject examination language and technical glossary")
    public ResponseEntity<ApiResponse<SubjectLanguageConfigDto>> getLanguageConfig(@PathVariable UUID id) {
        SubjectResponse subject = subjectService.getSubjectById(id);
        return ResponseEntity.ok(ApiResponse.<SubjectLanguageConfigDto>builder()
                .code(1000)
                .result(SubjectLanguageConfigDto.builder()
                        .language(subject.getLanguage())
                        .technicalTerms(subject.getTechnicalTerms())
                        .build())
                .build());
    }

    @PatchMapping("/{id}/language-config")
    @Operation(summary = "Update subject examination STT/TTS language configuration (Dual-role: Admin / Assigned Lecturer)")
    public ResponseEntity<ApiResponse<SubjectResponse>> updateLanguageConfig(
            @PathVariable UUID id,
            @Valid @RequestBody SubjectLanguageConfigDto request) {
        return ResponseEntity.ok(ApiResponse.<SubjectResponse>builder()
                .code(1000)
                .message("Cập nhật cấu hình ngôn ngữ khảo thí môn học thành công")
                .result(subjectService.updateLanguageConfig(id, request))
                .build());
    }
}

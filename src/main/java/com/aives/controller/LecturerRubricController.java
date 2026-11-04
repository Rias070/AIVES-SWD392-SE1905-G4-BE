package com.aives.controller;

import com.aives.application.port.in.LecturerRubricUseCase;
import com.aives.dto.request.RubricBulkSaveRequest;
import com.aives.dto.request.RubricRequest;
import com.aives.dto.response.RubricResponse;
import com.aives.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lecturer/rubrics")
@Tag(name = "Lecturer - Rubrics Management", description = "Endpoints for managing evaluation rubrics")
public class LecturerRubricController {

    private final LecturerRubricUseCase lecturerService;

    public LecturerRubricController(LecturerRubricUseCase lecturerService) {
        this.lecturerService = lecturerService;
    }

    @GetMapping("/by-subject/{subjectId}")
    @Operation(summary = "Get rubric TEMPLATE (Barem chuẩn) for a specific subject")
    public ResponseEntity<ApiResponse<List<RubricResponse>>> getRubricsBySubject(@PathVariable UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.<List<RubricResponse>>builder()
                .code(1000)
                .result(lecturerService.getRubricsBySubject(subjectId))
                .build());
    }

    @GetMapping("/by-subject/{subjectId}/all")
    @Operation(summary = "Get ALL rubrics of a subject (template + rubrics attached to each question)")
    public ResponseEntity<ApiResponse<List<RubricResponse>>> getAllRubricsBySubject(@PathVariable UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.<List<RubricResponse>>builder()
                .code(1000)
                .result(lecturerService.getAllRubricsBySubject(subjectId))
                .build());
    }

    @GetMapping("/by-question/{questionId}")
    @Operation(summary = "Get rubrics attached to a specific question")
    public ResponseEntity<ApiResponse<List<RubricResponse>>> getRubricsByQuestion(@PathVariable UUID questionId) {
        return ResponseEntity.ok(ApiResponse.<List<RubricResponse>>builder()
                .code(1000)
                .result(lecturerService.getRubricsByQuestion(questionId))
                .build());
    }

    @PostMapping
    @Operation(summary = "Create a new rubric criterion")
    public ResponseEntity<ApiResponse<RubricResponse>> createRubric(@Valid @RequestBody RubricRequest request) {
        return ResponseEntity.ok(ApiResponse.<RubricResponse>builder()
                .code(1000)
                .message("Tạo tiêu chí Rubric thành công")
                .result(lecturerService.createRubric(request))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a rubric criterion")
    public ResponseEntity<ApiResponse<RubricResponse>> updateRubric(
            @PathVariable UUID id,
            @Valid @RequestBody RubricRequest request) {
        return ResponseEntity.ok(ApiResponse.<RubricResponse>builder()
                .code(1000)
                .message("Cập nhật tiêu chí Rubric thành công")
                .result(lecturerService.updateRubric(id, request))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a rubric criterion")
    public ResponseEntity<ApiResponse<Void>> deleteRubric(@PathVariable UUID id) {
        lecturerService.deleteRubric(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Xóa tiêu chí Rubric thành công")
                .build());
    }

    @PostMapping("/bulk")
    @Operation(summary = "Save a whole rubric set at once (subject template or per-question)")
    public ResponseEntity<ApiResponse<List<RubricResponse>>> bulkSaveRubrics(
            @Valid @RequestBody RubricBulkSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.<List<RubricResponse>>builder()
                .code(1000)
                .message("Lưu toàn bộ tiêu chí Rubric thành công")
                .result(lecturerService.bulkSaveRubrics(
                        request.getSubjectUuid(), request.getQuestionUuid(), request.getRubrics()))
                .build());
    }

    @PostMapping("/copy-template/{questionId}")
    @Operation(summary = "Copy the subject rubric template into a question that has no rubrics yet")
    public ResponseEntity<ApiResponse<Integer>> copyTemplateToQuestion(@PathVariable UUID questionId) {
        int copied = lecturerService.copySubjectTemplateToQuestion(questionId);
        return ResponseEntity.ok(ApiResponse.<Integer>builder()
                .code(1000)
                .message("Đã áp dụng Barem môn học cho câu hỏi (" + copied + " tiêu chí)")
                .result(copied)
                .build());
    }

    @PostMapping("/apply-template/{subjectId}")
    @Operation(summary = "Apply the subject rubric template to every question of the subject without rubrics")
    public ResponseEntity<ApiResponse<Integer>> applyTemplateToSubject(@PathVariable UUID subjectId) {
        int copied = lecturerService.applySubjectTemplateToSubject(subjectId);
        return ResponseEntity.ok(ApiResponse.<Integer>builder()
                .code(1000)
                .message("Đã áp dụng Barem môn học cho " + copied + " câu hỏi")
                .result(copied)
                .build());
    }
}

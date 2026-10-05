package com.aives.controller;

import com.aives.dto.request.RubricRequest;
import com.aives.dto.response.RubricResponse;
import com.aives.response.ApiResponse;
import com.aives.service.LecturerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lecturer/rubrics")
@RequiredArgsConstructor
@Tag(name = "Lecturer - Rubrics Management", description = "Endpoints for managing evaluation rubrics")
public class LecturerRubricController {

    private final LecturerService lecturerService;

    @GetMapping("/by-subject/{subjectId}")
    @Operation(summary = "Get rubrics for a specific subject")
    public ResponseEntity<ApiResponse<List<RubricResponse>>> getRubricsBySubject(@PathVariable UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.<List<RubricResponse>>builder()
                .code(1000)
                .result(lecturerService.getRubricsBySubject(subjectId))
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
}

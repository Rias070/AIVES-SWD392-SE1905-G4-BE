package com.aives.controller;

import com.aives.dto.request.AssignmentPermissionRequest;
import com.aives.dto.request.SubjectAssignRequest;
import com.aives.dto.request.SubjectRequest;
import com.aives.dto.response.SubjectAssignmentResponse;
import com.aives.dto.response.SubjectResponse;
import com.aives.response.ApiResponse;
import com.aives.service.SubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/admin/subjects", "/api/admin/subjects"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Subject Management", description = "Endpoints for managing subjects and lecturer assignments")
public class AdminSubjectController {

    private final SubjectService subjectService;

    // ==========================================
    // SUBJECTS CRUD
    // ==========================================
    @GetMapping
    @Operation(summary = "List all subjects")
    public ResponseEntity<ApiResponse<List<SubjectResponse>>> listSubjects() {
        return ResponseEntity.ok(ApiResponse.<List<SubjectResponse>>builder()
                .code(1000)
                .message("Lấy danh sách môn học thành công")
                .result(subjectService.getAllSubjects())
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get subject details by ID")
    public ResponseEntity<ApiResponse<SubjectResponse>> getSubject(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.<SubjectResponse>builder()
                .code(1000)
                .result(subjectService.getSubjectById(id))
                .build());
    }

    @PostMapping
    @Operation(summary = "Create a new subject")
    public ResponseEntity<ApiResponse<SubjectResponse>> createSubject(@Valid @RequestBody SubjectRequest request) {
        SubjectResponse response = subjectService.createSubject(request);
        return ResponseEntity.ok(ApiResponse.<SubjectResponse>builder()
                .code(1000)
                .message("Tạo môn học thành công")
                .result(response)
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing subject")
    public ResponseEntity<ApiResponse<SubjectResponse>> updateSubject(
            @PathVariable UUID id,
            @Valid @RequestBody SubjectRequest request) {
        SubjectResponse response = subjectService.updateSubject(id, request);
        return ResponseEntity.ok(ApiResponse.<SubjectResponse>builder()
                .code(1000)
                .message("Cập nhật môn học thành công")
                .result(response)
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a subject (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteSubject(@PathVariable UUID id) {
        subjectService.deleteSubject(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Xóa môn học thành công")
                .build());
    }

    @PatchMapping("/{id}/soft-delete")
    @Operation(summary = "Explicit soft-delete subject (set deleted_at = NOW and status = INACTIVE)")
    public ResponseEntity<ApiResponse<Void>> softDeleteSubject(@PathVariable UUID id) {
        subjectService.deleteSubject(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Môn học đã được xóa mềm thành công (deleted_at set)")
                .build());
    }

    // ==========================================
    // LECTURER ASSIGNMENTS
    // ==========================================
    @GetMapping("/assignments")
    @Operation(summary = "List all lecturer subject assignments")
    public ResponseEntity<ApiResponse<List<SubjectAssignmentResponse>>> listAssignments() {
        return ResponseEntity.ok(ApiResponse.<List<SubjectAssignmentResponse>>builder()
                .code(1000)
                .message("Lấy danh sách phân công môn học thành công")
                .result(subjectService.getAllAssignments())
                .build());
    }

    @GetMapping("/assignments/lecturers/{lecturerId}")
    @Operation(summary = "List assignments for a specific lecturer")
    public ResponseEntity<ApiResponse<List<SubjectAssignmentResponse>>> getLecturerAssignments(
            @PathVariable UUID lecturerId) {
        return ResponseEntity.ok(ApiResponse.<List<SubjectAssignmentResponse>>builder()
                .code(1000)
                .result(subjectService.getLecturerAssignments(lecturerId))
                .build());
    }

    @PostMapping("/assignments")
    @Operation(summary = "Assign a subject to a lecturer")
    public ResponseEntity<ApiResponse<SubjectAssignmentResponse>> assignSubject(
            @Valid @RequestBody SubjectAssignRequest request) {
        SubjectAssignmentResponse response = subjectService.assignSubject(request);
        return ResponseEntity.ok(ApiResponse.<SubjectAssignmentResponse>builder()
                .code(1000)
                .message("Phân công môn học thành công")
                .result(response)
                .build());
    }

    @PostMapping("/{subjectId}/assign-lecturer")
    @Operation(summary = "Assign a lecturer to a specific subject")
    public ResponseEntity<ApiResponse<SubjectAssignmentResponse>> assignLecturerToSubject(
            @PathVariable UUID subjectId,
            @RequestBody SubjectAssignRequest request) {
        request.setSubjectUuid(subjectId);
        SubjectAssignmentResponse response = subjectService.assignSubject(request);
        return ResponseEntity.ok(ApiResponse.<SubjectAssignmentResponse>builder()
                .code(1000)
                .message("Phân công giảng viên cho môn học thành công")
                .result(response)
                .build());
    }

    @GetMapping("/{subjectId}/lecturers")
    @Operation(summary = "Get list of lecturers assigned to a specific subject")
    public ResponseEntity<ApiResponse<List<SubjectAssignmentResponse>>> getSubjectLecturers(
            @PathVariable UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.<List<SubjectAssignmentResponse>>builder()
                .code(1000)
                .message("Lấy danh sách giảng viên của môn học thành công")
                .result(subjectService.getSubjectLecturers(subjectId))
                .build());
    }

    @DeleteMapping("/{subjectId}/lecturers/{lecturerId}")
    @Operation(summary = "Unassign a lecturer from a specific subject")
    public ResponseEntity<ApiResponse<Void>> unassignLecturerFromSubject(
            @PathVariable UUID subjectId,
            @PathVariable UUID lecturerId) {
        subjectService.removeLecturerFromSubject(subjectId, lecturerId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Hủy phân công giảng viên khỏi môn học thành công")
                .build());
    }

    @PutMapping("/assignments/{assignmentId}/permissions")
    @Operation(summary = "Update RAG and Rubric permissions for an assignment")
    public ResponseEntity<ApiResponse<SubjectAssignmentResponse>> updatePermissions(
            @PathVariable UUID assignmentId,
            @RequestBody AssignmentPermissionRequest request) {
        SubjectAssignmentResponse response = subjectService.updateAssignmentPermissions(assignmentId, request);
        return ResponseEntity.ok(ApiResponse.<SubjectAssignmentResponse>builder()
                .code(1000)
                .message("Cập nhật quyền hạn thành công")
                .result(response)
                .build());
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @Operation(summary = "Remove subject assignment from lecturer")
    public ResponseEntity<ApiResponse<Void>> removeAssignment(@PathVariable UUID assignmentId) {
        subjectService.removeAssignment(assignmentId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Hủy phân công thành công")
                .build());
    }

    @PatchMapping("/{id}/language-config")
    @Operation(summary = "Admin override/update subject examination language configuration")
    public ResponseEntity<ApiResponse<SubjectResponse>> updateSubjectLanguageConfig(
            @PathVariable UUID id,
            @Valid @RequestBody com.aives.dto.request.SubjectLanguageConfigDto request) {
        SubjectResponse response = subjectService.updateLanguageConfig(id, request);
        return ResponseEntity.ok(ApiResponse.<SubjectResponse>builder()
                .code(1000)
                .message("Cập nhật ngôn ngữ khảo thí môn học thành công")
                .result(response)
                .build());
    }
}

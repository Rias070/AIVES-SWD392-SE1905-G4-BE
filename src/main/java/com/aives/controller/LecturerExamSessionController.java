package com.aives.controller;

import com.aives.application.port.in.StudentExamUseCase;
import com.aives.dto.request.LecturerFinalizeScoreRequest;
import com.aives.dto.response.ExamSessionResponse;
import com.aives.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/lecturer/exam-sessions", "/api/lecturer/exam-sessions"})
@RequiredArgsConstructor
@Tag(name = "Lecturer - Exam Sessions & Score Finalization", description = "Endpoints for lecturer review, grading override and score finalize")
public class LecturerExamSessionController {

    private final StudentExamUseCase studentExamService;

    @PostMapping("/{sessionId}/finalize-score")
    @Operation(summary = "Finalize and override score for an exam session")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> finalizeScore(
            @PathVariable UUID sessionId,
            @Valid @RequestBody LecturerFinalizeScoreRequest request) {
        return ResponseEntity.ok(ApiResponse.<ExamSessionResponse>builder()
                .code(1000)
                .message("Chốt điểm chính thức ca thi thành công")
                .result(studentExamService.finalizeExamSessionScore(sessionId, request))
                .build());
    }

    @GetMapping
    @Operation(summary = "Get list of all exam sessions for lecturer review")
    public ResponseEntity<ApiResponse<List<ExamSessionResponse>>> getSessions(
            @RequestParam(required = false) UUID studentId) {
        return ResponseEntity.ok(ApiResponse.<List<ExamSessionResponse>>builder()
                .code(1000)
                .message("Lấy danh sách ca thi cho hội đồng chấm điểm thành công")
                .result(studentExamService.getStudentSessions(studentId))
                .build());
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get detail of an exam session for review")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> getSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.<ExamSessionResponse>builder()
                .code(1000)
                .result(studentExamService.getSessionById(sessionId))
                .build());
    }
}

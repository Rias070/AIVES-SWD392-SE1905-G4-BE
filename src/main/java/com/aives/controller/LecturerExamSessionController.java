package com.aives.controller;

import com.aives.application.port.in.StudentExamUseCase;
import com.aives.dto.request.LecturerCreateExamRequest;
import com.aives.dto.request.LecturerFinalizeScoreRequest;
import com.aives.dto.response.ExamSessionResponse;
import com.aives.dto.response.VivaTurnResponse;
import com.aives.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/lecturer/exam-sessions", "/api/lecturer/exam-sessions"})
@Tag(name = "Lecturer - Exam Sessions & Score Finalization", description = "Endpoints for lecturer review, exam organization, grading override and score finalize")
public class LecturerExamSessionController {

    private final StudentExamUseCase studentExamService;

    public LecturerExamSessionController(StudentExamUseCase studentExamService) {
        this.studentExamService = studentExamService;
    }

    @PostMapping
    @Operation(summary = "Lecturer creates a new scheduled exam session with subject, schedule, and questions")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> createSession(
            @RequestParam(required = false) UUID examinerId,
            @Valid @RequestBody LecturerCreateExamRequest request) {
        return ResponseEntity.ok(ApiResponse.<ExamSessionResponse>builder()
                .code(1000)
                .message("Tạo kỳ thi vấn đáp thành công")
                .result(studentExamService.createExamSession(examinerId, request))
                .build());
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Delete an exam session")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable UUID sessionId) {
        studentExamService.deleteExamSession(sessionId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Xóa kỳ thi thành công")
                .build());
    }

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

    @GetMapping("/{sessionId}/turns")
    @Operation(summary = "Get viva turns (questions, audio transcript, AI evaluation, rubric score) for an exam session")
    public ResponseEntity<ApiResponse<List<VivaTurnResponse>>> getSessionTurns(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.<List<VivaTurnResponse>>builder()
                .code(1000)
                .message("Lấy danh sách câu hỏi và câu trả lời của thí sinh thành công")
                .result(studentExamService.getSessionTurns(sessionId))
                .build());
    }
}

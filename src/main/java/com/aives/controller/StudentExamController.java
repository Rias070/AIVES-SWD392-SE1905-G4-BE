package com.aives.controller;

import com.aives.dto.request.ExamStartRequest;
import com.aives.dto.request.VivaTurnSubmitRequest;
import com.aives.dto.response.ExamSessionResponse;
import com.aives.dto.response.VivaTurnResponse;
import com.aives.response.ApiResponse;
import com.aives.service.StudentExamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/exam-sessions")
@RequiredArgsConstructor
@Tag(name = "Student - Exam Sessions & Viva", description = "Endpoints for student exam sessions, viva turns and AI evaluation")
public class StudentExamController {

    private final StudentExamService studentExamService;

    @GetMapping
    @Operation(summary = "List exam sessions for student")
    public ResponseEntity<ApiResponse<List<ExamSessionResponse>>> getSessions(
            @RequestParam(required = false) UUID studentId) {
        return ResponseEntity.ok(ApiResponse.<List<ExamSessionResponse>>builder()
                .code(1000)
                .message("Lấy danh sách ca thi thành công")
                .result(studentExamService.getStudentSessions(studentId))
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get exam session details")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> getSession(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.<ExamSessionResponse>builder()
                .code(1000)
                .result(studentExamService.getSessionById(id))
                .build());
    }

    @GetMapping("/{id}/turns")
    @Operation(summary = "Get viva turns for an exam session")
    public ResponseEntity<ApiResponse<List<VivaTurnResponse>>> getSessionTurns(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.<List<VivaTurnResponse>>builder()
                .code(1000)
                .result(studentExamService.getSessionTurns(id))
                .build());
    }

    @PostMapping("/start")
    @Operation(summary = "Start or check-in to an exam session")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> startExam(
            @RequestParam(required = false) UUID studentId,
            @RequestBody ExamStartRequest request) {
        return ResponseEntity.ok(ApiResponse.<ExamSessionResponse>builder()
                .code(1000)
                .message("Khởi tạo phiên thi Viva thành công")
                .result(studentExamService.startExamSession(studentId, request))
                .build());
    }

    @PostMapping("/{id}/turns")
    @Operation(summary = "Submit a viva turn answer and get AI evaluation")
    public ResponseEntity<ApiResponse<VivaTurnResponse>> submitTurn(
            @PathVariable UUID id,
            @Valid @RequestBody VivaTurnSubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.<VivaTurnResponse>builder()
                .code(1000)
                .message("Ghi nhận lượt vấn đáp thành công")
                .result(studentExamService.submitVivaTurn(id, request))
                .build());
    }

    @PostMapping("/{id}/finish")
    @Operation(summary = "Finish an exam session and calculate final score")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> finishExam(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.<ExamSessionResponse>builder()
                .code(1000)
                .message("Hoàn thành ca thi vấn đáp thành công")
                .result(studentExamService.finishExamSession(id))
                .build());
    }
}

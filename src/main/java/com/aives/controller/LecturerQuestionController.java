package com.aives.controller;

import com.aives.dto.request.QuestionRequest;
import com.aives.dto.response.QuestionResponse;
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
@RequestMapping("/api/v1/lecturer/questions")
@RequiredArgsConstructor
@Tag(name = "Lecturer - Question Bank Management", description = "Endpoints for managing question bank and Bloom taxonomy")
public class LecturerQuestionController {

    private final LecturerService lecturerService;

    @GetMapping
    @Operation(summary = "List questions in bank with filters")
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> listQuestions(
            @RequestParam(required = false) UUID subjectUuid,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.<List<QuestionResponse>>builder()
                .code(1000)
                .message("Lấy danh sách câu hỏi thành công")
                .result(lecturerService.getQuestions(subjectUuid, difficulty, keyword))
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get question details")
    public ResponseEntity<ApiResponse<QuestionResponse>> getQuestion(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.<QuestionResponse>builder()
                .code(1000)
                .result(lecturerService.getQuestionById(id))
                .build());
    }

    @PostMapping
    @Operation(summary = "Create a new question")
    public ResponseEntity<ApiResponse<QuestionResponse>> createQuestion(
            @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(ApiResponse.<QuestionResponse>builder()
                .code(1000)
                .message("Thêm câu hỏi mới thành công")
                .result(lecturerService.createQuestion(request))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing question")
    public ResponseEntity<ApiResponse<QuestionResponse>> updateQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(ApiResponse.<QuestionResponse>builder()
                .code(1000)
                .message("Cập nhật câu hỏi thành công")
                .result(lecturerService.updateQuestion(id, request))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a question")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(@PathVariable UUID id) {
        lecturerService.deleteQuestion(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Xóa câu hỏi thành công")
                .build());
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Toggle approval status of a question")
    public ResponseEntity<ApiResponse<QuestionResponse>> toggleApprove(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.<QuestionResponse>builder()
                .code(1000)
                .message("Cập nhật trạng thái duyệt câu hỏi thành công")
                .result(lecturerService.toggleApproveQuestion(id))
                .build());
    }
}

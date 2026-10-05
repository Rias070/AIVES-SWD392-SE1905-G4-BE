package com.aives.controller;

import com.aives.dto.request.DocumentUploadRequest;
import com.aives.dto.response.DocumentResponse;
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
@RequestMapping("/api/v1/lecturer/documents")
@RequiredArgsConstructor
@Tag(name = "Lecturer - Document & RAG Management", description = "Endpoints for syllabus upload, chunking and vector indexing")
public class LecturerDocumentController {

    private final LecturerService lecturerService;

    @GetMapping
    @Operation(summary = "Get syllabus documents for RAG retrieval")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getDocuments(
            @RequestParam(required = false) String subjectCode,
            @RequestParam(required = false) UUID subjectUuid) {
        return ResponseEntity.ok(ApiResponse.<List<DocumentResponse>>builder()
                .code(1000)
                .message("Lấy danh sách học liệu thành công")
                .result(lecturerService.getDocuments(subjectCode, subjectUuid))
                .build());
    }

    @PostMapping("/upload")
    @Operation(summary = "Register or upload a course syllabus document")
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @Valid @RequestBody DocumentUploadRequest request) {
        return ResponseEntity.ok(ApiResponse.<DocumentResponse>builder()
                .code(1000)
                .message("Tải lên và xử lý tệp học liệu thành công")
                .result(lecturerService.uploadDocument(request))
                .build());
    }

    @PostMapping("/{id}/vectorize")
    @Operation(summary = "Trigger vectorization for a document")
    public ResponseEntity<ApiResponse<DocumentResponse>> vectorizeDocument(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.<DocumentResponse>builder()
                .code(1000)
                .message("Vector hóa học liệu thành công")
                .result(lecturerService.triggerVectorize(id))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a syllabus document")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID id) {
        lecturerService.deleteDocument(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Xóa học liệu thành công")
                .build());
    }
}

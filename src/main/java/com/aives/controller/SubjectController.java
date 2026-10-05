package com.aives.controller;

import com.aives.dto.response.SubjectResponse;
import com.aives.response.ApiResponse;
import com.aives.service.SubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
@Tag(name = "Subjects - Public/Catalog", description = "Public catalog of subjects")
public class SubjectController {

    private final SubjectService subjectService;

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
}

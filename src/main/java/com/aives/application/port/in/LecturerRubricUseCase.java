package com.aives.application.port.in;

import com.aives.dto.request.RubricRequest;
import com.aives.dto.response.RubricResponse;

import java.util.List;
import java.util.UUID;

public interface LecturerRubricUseCase {
    /**
     * Rubric TEMPLATE (Barem chuẩn) của môn học - question_uuid IS NULL.
     */
    List<RubricResponse> getRubricsBySubject(UUID subjectUuid);

    /**
     * Tất cả rubric của môn học: template + rubric của từng câu hỏi.
     */
    List<RubricResponse> getAllRubricsBySubject(UUID subjectUuid);

    /**
     * Rubric gắn riêng cho một câu hỏi.
     */
    List<RubricResponse> getRubricsByQuestion(UUID questionUuid);

    RubricResponse createRubric(RubricRequest request);

    RubricResponse updateRubric(UUID id, RubricRequest request);

    void deleteRubric(UUID id);

    /**
     * Lưu cả bộ rubric của một nhóm trong 1 lần gọi (đồng bộ theo diff).
     */
    List<RubricResponse> bulkSaveRubrics(UUID subjectUuid, UUID questionUuid, List<RubricRequest> rubrics);

    /**
     * Copy rubric template của môn học vào một câu hỏi (chỉ khi câu hỏi chưa có rubric riêng).
     */
    int copySubjectTemplateToQuestion(UUID questionId);

    /**
     * Áp dụng rubric template của môn học cho mọi câu hỏi của môn chưa có rubric riêng.
     */
    int applySubjectTemplateToSubject(UUID subjectId);
}

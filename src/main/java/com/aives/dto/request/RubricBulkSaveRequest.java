package com.aives.dto.request;

import jakarta.validation.Valid;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricBulkSaveRequest {

    /**
     * Môn học sở hữu nhóm rubric. Bắt buộc.
     */
    private UUID subjectUuid;

    /**
     * Nếu có -> lưu rubric riêng của câu hỏi.
     * Nếu null -> lưu rubric TEMPLATE của môn học (question_uuid IS NULL).
     */
    private UUID questionUuid;

    @Valid
    @Builder.Default
    private List<RubricRequest> rubrics = List.of();
}

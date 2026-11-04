package com.aives.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricResponse {
    private UUID uuid;

    /**
     * Alias của {@link #uuid} để Frontend đọc được cả {@code id} lẫn {@code uuid}.
     * Tránh việc FE gửi sai id khi xóa/cập nhật rubric.
     */
    private UUID id;

    private UUID subjectId;
    private UUID questionId;

    /**
     * TRUE nếu đây là rubric template của môn học (không gắn vào câu hỏi nào).
     */
    private Boolean isTemplate;

    private String criterionName;
    private String description;
    private BigDecimal maxScore;

    /**
     * Trọng số theo phần trăm (0-100). Tổng của mỗi nhóm rubric = 100.
     */
    private BigDecimal weight;

    private LocalDateTime createdAt;
}

package com.aives.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricRequest {
    /**
     * Id của tiêu chí đang tồn tại. Nếu có -> cập nhật tại chỗ (giữ nguyên uuid).
     * Nếu null -> tạo tiêu chí mới.
     */
    private UUID uuid;

    private UUID subjectUuid;
    private UUID questionUuid;

    @NotBlank(message = "Criterion name is required")
    private String criterionName;

    private String description;

    @Builder.Default
    private BigDecimal maxScore = new BigDecimal("10.00");

    /**
     * Trọng số (%). Hệ thống tự chuẩn hoá lại theo maxScore nên có thể bỏ qua.
     */
    @Builder.Default
    private BigDecimal weight = new BigDecimal("100.00");
}

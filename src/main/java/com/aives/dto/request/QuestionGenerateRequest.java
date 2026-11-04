package com.aives.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionGenerateRequest {
    private UUID subjectUuid;
    private String subjectCode;
    private UUID documentUuid;

    private String topic; // E.g. "Session Management in Spring", "Dijkstra algorithm"

    /**
     * Context tự do do giảng viên nhập tay (đề cương, ghi chú, đề thi cũ...).
     * Được ưu tiên cao hơn nội dung RAG khi truyền vào prompt LLM.
     */
    private String customContext;

    /**
     * Cờ bật/tắt tham khảo tài liệu RAG.
     * null hoặc true => dùng RAG (giữ hành vi mặc định).
     * false => bỏ qua hoàn toàn việc truy xuất chunk tài liệu.
     */
    private Boolean useRagContext;

    @Builder.Default
    private String bloomLevel = "ALL"; // REMEMBER, UNDERSTAND, APPLY, ANALYZE, or ALL

    @Builder.Default
    private Integer count = 3; // Number of questions to generate

    @Builder.Default
    private String difficulty = "MEDIUM"; // EASY, MEDIUM, HARD
}

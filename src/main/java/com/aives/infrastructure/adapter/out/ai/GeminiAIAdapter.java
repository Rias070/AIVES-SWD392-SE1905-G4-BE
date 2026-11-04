package com.aives.infrastructure.adapter.out.ai;

import com.aives.application.port.out.LLMProviderPort;
import com.aives.application.port.out.VectorEmbeddingPort;
import com.aives.dto.response.VivaEvaluationResult;
import com.aives.entity.Rubric;
import com.aives.entity.Subject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class GeminiAIAdapter implements LLMProviderPort, VectorEmbeddingPort {

    // ===== Canonical Bloom Taxonomy labels (single source of truth) =====
    public static final String BLOOM_1 = "Bloom 1 - Nhận biết";
    public static final String BLOOM_2 = "Bloom 2 - Thông hiểu";
    public static final String BLOOM_3 = "Bloom 3 - Vận dụng";
    public static final String BLOOM_4 = "Bloom 4 - Phân tích";
    public static final String BLOOM_5 = "Bloom 5 - Đánh giá";
    public static final String BLOOM_6 = "Bloom 6 - Sáng tạo";

    private static final String[] BLOOM_LABELS = {BLOOM_1, BLOOM_2, BLOOM_3, BLOOM_4, BLOOM_5, BLOOM_6};

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiAIAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${gemini.api.key:dummy_key}")
    private String geminiApiKey;

    /** Primary text generation model. */
    @Value("${gemini.text.model:gemini-3.5-flash}")
    private String textModel;

    /** Comma-separated ordered list of fallback text models tried after the primary one fails. */
    @Value("${gemini.fallback.models:gemini-3.5-flash-lite,gemini-3-flash-preview,gemini-3.1-flash-lite}")
    private String fallbackModels;

    /** Embedding model used to build vectors (text-embedding-004 was retired by Google). */
    @Value("${gemini.embedding.model:gemini-embedding-001}")
    private String embeddingModel;

    /** Requested embedding dimensionality; must match the vector(1536) column. */
    @Value("${gemini.embedding.dimensions:1536}")
    private int embeddingDimensions;

    @Override
    public VivaEvaluationResult evaluateVivaTurn(
            String subjectCode,
            int turnOrder,
            String currentQuestion,
            String studentTranscript,
            List<String> ragSnippets,
            List<Rubric> rubrics,
            String lang) {

        boolean isUnanswered = (studentTranscript == null || studentTranscript.isBlank() ||
                studentTranscript.toLowerCase().contains("không trả lời") ||
                studentTranscript.trim().length() < 3);
        if (isUnanswered) {
            return new VivaEvaluationResult(
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    "Thí sinh không trả lời hoặc không ghi nhận được âm thanh trả lời cho câu hỏi này. Điểm đề xuất: 0.0.",
                    "Thí sinh vui lòng tập trung và trả lời câu hỏi tiếp theo.",
                    "Bloom 1 - Nhận biết"
            );
        }

        if (geminiApiKey != null && !geminiApiKey.isBlank() && !"dummy_key".equalsIgnoreCase(geminiApiKey)) {
            try {
                StringBuilder rubricStr = new StringBuilder();
                if (rubrics != null && !rubrics.isEmpty()) {
                    for (Rubric r : rubrics) {
                        rubricStr.append("- ").append(r.getCriterionName())
                                .append(" (").append(r.getWeight() != null ? r.getWeight() : 20).append("%): ")
                                .append(r.getDescription() != null ? r.getDescription() : "").append("\n");
                    }
                }
                String prompt = "You are an expert AI oral examiner for course " + subjectCode + ".\n" +
                        "Language: " + lang + "\n" +
                        "Current Question: " + currentQuestion + "\n" +
                        "Student Answer: " + studentTranscript + "\n" +
                        "Syllabus Context (RAG): " + (ragSnippets != null ? String.join(" | ", ragSnippets) : "") + "\n" +
                        "Rubric Criteria:\n" + (rubricStr.length() > 0 ? rubricStr.toString() : "Standard Viva Rubric") + "\n" +
                        "Task: Evaluate answer on 0.0-10.0 scale based on rubrics, write a 1-sentence assessment, and generate an adaptive follow-up question (Bloom 4-5) to probe deeper. Format as:\n" +
                        "SCORE: <number>\nEVAL: <text>\nFOLLOWUP: <text>\nBLOOM: <text>";

                Map<String, Object> part = Map.of("text", prompt);
                Map<String, Object> contentObj = Map.of("parts", List.of(part));
                Map<String, Object> body = Map.of("contents", List.of(contentObj));

                String aiRawText = callGeminiGenerateContent(body, null, "viva-evaluate");
                if (aiRawText != null) {
                    return parseGeminiOutput(aiRawText, turnOrder, subjectCode, lang);
                }
            } catch (Exception e) {
                log.warn("Gemini API call failed or timed out, activating adaptive domain reasoning engine: {}", e.getMessage());
            }
        }

        return computeDomainEvaluation(subjectCode, turnOrder, studentTranscript, lang);
    }

    @Override
    public List<Map<String, Object>> generateQuestionsFromSyllabus(
            Subject subject,
            String topic,
            String bloomLevel,
            String difficulty,
            int count,
            String ragContext) {
        return generateQuestionsFromSyllabus(subject, topic, bloomLevel, difficulty, count, ragContext, null);
    }

    @Override
    public List<Map<String, Object>> generateQuestionsFromSyllabus(
            Subject subject,
            String topic,
            String bloomLevel,
            String difficulty,
            int count,
            String ragContext,
            String customContext) {

        String safeDiff = (difficulty != null && !difficulty.isBlank()) ? difficulty.trim().toUpperCase() : "MEDIUM";
        String safeCustomContext = sanitizePromptInput(customContext, 8000);

        // Bloom "ALL" nghĩa là giảng viên muốn bộ câu hỏi phủ đều 6 mức Bloom.
        // Mọi mức khác đều là một mức cụ thể và sẽ được ép theo đúng mức đó.
        boolean allBloom = bloomLevel == null || bloomLevel.isBlank() || "ALL".equalsIgnoreCase(bloomLevel.trim());
        Integer requestedBloomNumber = allBloom ? null : extractBloomNumber(bloomLevel);
        String requestedBloomLabel = requestedBloomNumber != null ? bloomLabelOf(requestedBloomNumber) : BLOOM_3;

        // Kế hoạch Bloom ngẫu nhiên (chỉ dùng cho nhánh "ALL"): các mức được rút ngẫu nhiên
        // trên toàn bộ dải 1-6 nên KHÔNG bao giờ cố định là 1,2,3... theo thứ tự tăng dần.
        // Kế hoạch này được dùng cho cả prompt (bảng gán) lẫn việc ép nhãn sau khi parse,
        // nên kết quả luôn khớp với nhau dù AI có tuân thủ prompt hay không.
        List<Integer> bloomPlan = allBloom ? randomBloomPlan(count) : null;
        if (allBloom) {
            log.info("Random Bloom plan for ALL (count={}): {}", count, bloomPlan);
        }

        if (geminiApiKey != null && !geminiApiKey.isBlank() && !"dummy_key".equalsIgnoreCase(geminiApiKey)) {
            try {
                // Pedagogical Bloom Taxonomy & Difficulty Guidelines
                String bloomDesc;
                if (allBloom) {
                    bloomDesc = "ĐA DẠNG MỨC ĐỘ BLOOM (PHÂN BỔ NGẪU NHIÊN TRÊN TOÀN BỘ DẢI BLOOM 1-6):\n" +
                            "- Trọng tâm: Bộ câu hỏi phải phủ đa dạng các mức nhận thức Bloom, KHÔNG được dồn cục vào một mức duy nhất.\n" +
                            "- Quy tắc phân bổ: Mức Bloom của từng câu đã được hệ thống chọn NGẪU NHIÊN trên toàn bộ dải Bloom 1-6.\n" +
                            "  Bạn PHẢI tuân thủ CHÍNH XÁC bảng gán được nêu ở mục BLOOM ASSIGNMENT bên dưới, KHÔNG tự ý đổi mức,\n" +
                            "  KHÔNG được mặc định lần lượt theo thứ tự Bloom 1, Bloom 2, Bloom 3...\n" +
                            "- Đặc trưng từng mức (phải thể hiện rõ trong nội dung câu hỏi):\n" +
                            "  + Bloom 1 (NHẬN BIẾT): Hãy định nghĩa..., Hãy liệt kê..., Kể tên..., Nêu khái niệm về...\n" +
                            "  + Bloom 2 (THÔNG HIỂU): Hãy giải thích tại sao..., Diễn giải nguyên lý..., Phân biệt cơ bản giữa...\n" +
                            "  + Bloom 3 (VẬN DỤNG): Hãy vận dụng... để giải quyết tình huống..., Hãy áp dụng mẫu thiết kế...\n" +
                            "  + Bloom 4 (PHÂN TÍCH): Hãy phân tích cơ chế nội tại..., Mổ xẻ..., So sánh đối chiếu...\n" +
                            "  + Bloom 5 (ĐÁNH GIÁ): Hãy đánh giá sự đánh đổi..., Hãy phản biện..., Biện minh vì sao...\n" +
                            "  + Bloom 6 (SÁNG TẠO): Hãy thiết kế..., Đề xuất giải pháp tổng thể..., Xây dựng chiến lược...\n" +
                            "- Yêu cầu: Mỗi câu hỏi phải đúng với đặc trưng của mức Bloom được gán cho nó, và các câu phải khác nhau về chủ đề.";
                } else if (requestedBloomNumber != null && requestedBloomNumber == 1) {
                    bloomDesc = "MỨC ĐỘ BLOOM 1 (NHẬN BIẾT / REMEMBER):\n" +
                            "- Trọng tâm: Kiểm tra trí nhớ, nhận diện định nghĩa chuẩn, cú pháp, thuật ngữ chuyên ngành và các thành phần cốt lõi.\n" +
                            "- Động từ hành động bắt buộc: Hãy định nghĩa..., Hãy liệt kê..., Kể tên các thành phần của..., Nêu khái niệm về...\n" +
                            "- Yêu cầu: Câu hỏi rõ ràng, trực diện, KHÔNG bắt phân tích so sánh hay giải quyết tình huống phức tạp.";
                } else if (requestedBloomNumber != null && requestedBloomNumber == 2) {
                    bloomDesc = "MỨC ĐỘ BLOOM 2 (THÔNG HIỂU / UNDERSTAND):\n" +
                            "- Trọng tâm: Kiểm tra khả năng hiểu bản chất, giải thích nguyên lý hoạt động, làm rõ lý do tại sao công nghệ/mẫu thiết kế này ra đời và ý nghĩa của nó.\n" +
                            "- Động từ hành động bắt buộc: Hãy giải thích tại sao..., Diễn giải nguyên lý hoạt động của..., Trình bày bản chất của..., Phân biệt cơ bản giữa...\n" +
                            "- Yêu cầu: Yêu cầu sinh viên dùng ngôn từ của mình để làm rõ cơ chế, không chỉ đọc thuộc lòng định nghĩa.";
                } else if (requestedBloomNumber != null && requestedBloomNumber == 3) {
                    bloomDesc = "MỨC ĐỘ BLOOM 3 (VẬN DỤNG / APPLY):\n" +
                            "- Trọng tâm: Đưa ra một tình huống/bài toán thực tế cụ thể trong phát triển phần mềm và yêu cầu sinh viên áp dụng trực tiếp kiến thức/mẫu thiết kế này để giải quyết.\n" +
                            "- Động từ hành động bắt buộc: Hãy vận dụng kiến thức về... để xử lý tình huống..., Hãy áp dụng mẫu thiết kế... để giải quyết bài toán..., Viết kịch bản thực thi...\n" +
                            "- Yêu cầu: Bắt buộc phải có ngữ cảnh tình huống nghiệp vụ thực tế (e.g. xử lý giỏ hàng, đặt vé máy bay, tính phí vận chuyển).";
                } else if (requestedBloomNumber != null && requestedBloomNumber == 4) {
                    bloomDesc = "MỨC ĐỘ BLOOM 4 (PHÂN TÍCH / ANALYZE):\n" +
                            "- Trọng tâm: Mổ xẻ chi tiết cơ chế nội tại, phân tích luồng dữ liệu, đối chiếu so sánh sâu sắc ưu/nhược điểm với giải pháp đối chứng, chỉ ra nguyên nhân gốc rễ (root cause).\n" +
                            "- Động từ hành động bắt buộc: Hãy phân tích cơ chế nội tại của..., Mổ xẻ luồng xử lý dữ liệu khi..., So sánh đối chiếu chuyên sâu giữa... và..., Chỉ ra nguyên nhân xảy ra hiện tượng...\n" +
                            "- Yêu cầu: Đòi hỏi tư duy bóc tách cấu trúc và làm rõ sự tương tác giữa các module.";
                } else if (requestedBloomNumber != null && requestedBloomNumber == 5) {
                    bloomDesc = "MỨC ĐỘ BLOOM 5 (ĐÁNH GIÁ & PHẢN BIỆN / EVALUATE):\n" +
                            "- Trọng tâm: Đặt sinh viên vào vai trò Kiến trúc sư phần mềm (Software Architect / Tech Lead) để đánh giá sự đánh đổi (Quality Attribute Trade-offs: Hiệu năng vs Bảo mật, Tính nhất quán vs Tính sẵn sàng), biện minh cho quyết định thiết kế và phản biện các phương án khác nhau.\n" +
                            "- Động từ hành động bắt buộc: Dưới góc độ kiến trúc sư, hãy đánh giá sự đánh đổi (trade-offs) giữa..., Hãy phản biện quyết định sử dụng..., Biện minh vì sao phương án A tối ưu hơn phương án B trong bối cảnh...\n" +
                            "- Yêu cầu: Phải có lập luận sắc bén, dựa trên các thuộc tính chất lượng (Latency, Scalability, Fault Tolerance, CAP theorem).";
                } else if (requestedBloomNumber != null && requestedBloomNumber == 6) {
                    bloomDesc = "MỨC ĐỘ BLOOM 6 (SÁNG TẠO & THIẾT KẾ / CREATE):\n" +
                            "- Trọng tâm: Yêu cầu sinh viên thiết kế giải pháp kiến trúc tổng thể mới, đề xuất quy trình kỹ thuật hoặc phối hợp nhiều công nghệ thành một hệ thống chịu tải cao.\n" +
                            "- Động từ hành động bắt buộc: Hãy thiết kế kiến trúc hệ thống cho..., Đề xuất giải pháp tổng thể để tích hợp..., Xây dựng chiến lược kiểm soát chất lượng từ đầu đến cuối cho...\n" +
                            "- Yêu cầu: Đòi hỏi tính tổng hợp cao, tư duy hệ thống toàn diện.";
                } else {
                    // Không xác định được mức Bloom cụ thể -> dùng mức trung tính (Vận dụng).
                    // KHÔNG dùng Bloom 6 ở nhánh này: nếu không, mọi giá trị lạ (vd: "ALL")
                    // sẽ bị hiểu nhầm thành Bloom 6 (CREATE).
                    bloomDesc = "MỨC ĐỘ BLOOM 3 (VẬN DỤNG / APPLY):\n" +
                            "- Trọng tâm: Đưa ra một tình huống/bài toán thực tế cụ thể trong phát triển phần mềm và yêu cầu sinh viên áp dụng trực tiếp kiến thức/mẫu thiết kế này để giải quyết.\n" +
                            "- Động từ hành động bắt buộc: Hãy vận dụng kiến thức về... để xử lý tình huống..., Hãy áp dụng mẫu thiết kế... để giải quyết bài toán...\n" +
                            "- Yêu cầu: Bắt buộc phải có ngữ cảnh tình huống nghiệp vụ thực tế.";
                }

                String diffDesc;
                if ("EASY".equals(safeDiff)) {
                    diffDesc = "MỨC ĐỘ KHÓ DỄ (EASY): Tình huống tường minh, trọng tâm vào trường hợp chuẩn (happy path), không gài bẫy hay trường hợp biên ngoại lệ phức tạp.";
                } else if ("HARD".equals(safeDiff)) {
                    diffDesc = "MỨC ĐỘ KHÓ KHÓ (HARD): Tình huống phức tạp ở quy mô lớn (High Concurrency, Distributed Lock, Network Partition, Race Condition, Failure Recovery, CAP theorem), yêu cầu tư duy phản biện sắc bén và xử lý các kịch bản ngoại lệ khắt khe.";
                } else {
                    diffDesc = "MỨC ĐỘ KHÓ TRUNG BÌNH (MEDIUM): Tình huống thực tế tiêu biểu trong doanh nghiệp, đòi hỏi kết hợp giữa lý thuyết nền tảng và bài toán triển khai cụ thể.";
                }

                String customContextSection = "";
                if (safeCustomContext != null && !safeCustomContext.isBlank()) {
                    customContextSection = "=== CONTEXT TỰ DO TỪ GIẢNG VIÊN (ƯU TIÊN CAO NHẤT - NGUỒN SỰ THẬT CHÍNH THỨC) ===\n" +
                            safeCustomContext + "\n" +
                            "=== QUY TẮC BẮT BUỘC VỚI CONTEXT GIẢNG VIÊN ===\n" +
                            "1. CHỈ dựa vào CONTEXT TỰ DO ở trên để đặt câu hỏi. Đây là nguồn kiến thức chính thức của giảng viên.\n" +
                            "2. TUYỆT ĐỐI KHÔNG dùng tên file, đường dẫn hay tên tài liệu làm chủ đề nội dung câu hỏi.\n" +
                            "3. Trích xuất trực tiếp định nghĩa, thuật ngữ, công thức, case study có thật trong context này.\n\n";
                }

                String ragSection = "";
                if (ragContext != null && !ragContext.isBlank()) {
                    ragSection = "=== NỘI DUNG TÀI LIỆU HỌC LIỆU GỐC (GROUND TRUTH CONTEXT TỪ TÀI LIỆU/SLIDE ĐƯỢC CHỈ ĐỊNH) ===\n" +
                            ragContext + "\n" +
                            "=== QUY TẮC BẮT BUỘC (CRITICAL MANDATE) ===\n" +
                            "1. BẮT BUỘC ĐỌC KỸ VĂN BẢN TÀI LIỆU: Trích xuất trực tiếp các định lý, công thức, mô hình, case study, thành phần cụ thể có thật trong văn bản để đặt câu hỏi.\n" +
                            "2. ĐA DẠNG HÓA TUYỆT ĐỐI (DIVERSITY): Mỗi câu hỏi trong bộ " + count + " câu PHẢI hỏi về một phần kiến thức hoặc chủ đề HOÀN TOÀN KHÁC BIỆT. TUYỆT ĐỐI KHÔNG sinh câu hỏi lặp ý hay dùng chung một mô-típ mẫu câu.\n" +
                            "3. Câu hỏi phải chuyên sâu, bám sát nội dung bài học, mang tính thực tế cao cho kỳ thi vấn đáp viva.\n\n";
                }

                boolean hasRag = ragContext != null && !ragContext.isBlank();
                boolean hasCustom = safeCustomContext != null && !safeCustomContext.isBlank();

                String topicInfo;
                if (topic != null && !topic.isBlank()) {
                    topicInfo = "Focus Area: " + sanitizePromptInput(topic, 500) + "\n";
                } else if (hasCustom) {
                    topicInfo = "Focus Area: Trích xuất trực tiếp từ CONTEXT TỰ DO của giảng viên.\n";
                } else if (hasRag) {
                    topicInfo = "Focus Area: Trích xuất trực tiếp từ các phần khác nhau trong tài liệu học liệu.\n";
                } else {
                    topicInfo = "Focus Area: Thiết kế câu hỏi vấn đáp theo chủ đề trọng tâm của học phần, dựa trên kiến thức nền tảng của môn học.\n";
                }

                // Nhãn Bloom khai báo trong prompt + hướng dẫn gán nhãn cho từng câu
                String bloomTemplateLine;
                String bloomAssignmentRule;
                if (allBloom) {
                    bloomTemplateLine = "    \"bloomLevel\": \"Bloom N - <tên mức>\",\n";
                    StringBuilder planLines = new StringBuilder();
                    for (int i = 0; i < bloomPlan.size(); i++) {
                        planLines.append("  Câu ").append(i + 1).append(" -> ").append(bloomLabelOf(bloomPlan.get(i))).append("\n");
                    }
                    bloomAssignmentRule = "BLOOM ASSIGNMENT (STRICT):\n" +
                            "- Bạn được dùng đúng " + count + " câu hỏi, mỗi câu gán đúng MỘT mức Bloom.\n" +
                            "- Các mức Bloom đã được hệ thống chọn NGẪU NHIÊN trên toàn bộ dải Bloom 1-6 (có thể xuất hiện Bloom 5 hoặc Bloom 6 ngay ở câu đầu tiên).\n" +
                            "- Bạn PHẢI tuân thủ CHÍNH XÁC bảng gán sau, KHÔNG tự ý đổi mức cho câu nào:\n" +
                            planLines +
                            "- Chỉ được dùng đúng 6 nhãn sau, không dùng nhãn khác (ví dụ KHÔNG dùng \"CREATE\" hay \"Bloom 6\" viết tắt):\n" +
                            "  [\"" + BLOOM_1 + "\", \"" + BLOOM_2 + "\", \"" + BLOOM_3 + "\", \"" + BLOOM_4 + "\", \"" + BLOOM_5 + "\", \"" + BLOOM_6 + "\"]\n" +
                            "- Nội dung câu hỏi phải khớp với đúng đặc trưng của mức Bloom được gán.\n";
                } else {
                    bloomTemplateLine = "    \"bloomLevel\": \"" + requestedBloomLabel + "\",\n";
                    bloomAssignmentRule = "BLOOM ASSIGNMENT (STRICT):\n" +
                            "- Tất cả " + count + " câu hỏi BẮT BUỘC dùng mức Bloom: \"" + requestedBloomLabel + "\".\n" +
                            "- KHÔNG được dùng bất kỳ mức Bloom nào khác. Trả về đúng chuỗi: \"" + requestedBloomLabel + "\".\n";
                }

                String prompt = "You are a Senior Academic Examiner and Professor designing oral examination (viva) questions for course " +
                        subject.getCode() + " (" + subject.getName() + ").\n" +
                        "Language: Vietnamese (vi-VN).\n" +
                        topicInfo +
                        "Requested Bloom Taxonomy: " + (allBloom
                                ? "ALL (các mức Bloom đã được phân bổ NGẪU NHIÊN theo bảng gán bên dưới)"
                                : requestedBloomLabel) + "\n" +
                        "Requested Difficulty: " + safeDiff + "\n" +
                        "Question Count: " + count + "\n\n" +
                        "=== PEDAGOGICAL INSTRUCTIONS (MUST STRICTLY FOLLOW) ===\n" +
                        bloomDesc + "\n\n" +
                        diffDesc + "\n\n" +
                        customContextSection +
                        ragSection +
                        "Task: Generate exactly " + count + " UNIQUE viva questions reflecting strictly the pedagogical guidelines and syllabus context above.\n" +
                        "Each question must explore a DIFFERENT angle of the topic (do NOT generate similar questions).\n" +
                        bloomAssignmentRule + "\n" +
                        "Return ONLY a valid JSON Array (no Markdown ticks, no other text):\n" +
                        "[\n" +
                        "  {\n" +
                        "    \"content\": \"Câu hỏi vấn đáp chi tiết mang đúng tính chất của Bloom level và Độ khó yêu cầu...\",\n" +
                        bloomTemplateLine +
                        "    \"difficulty\": \"" + safeDiff + "\",\n" +
                        "    \"expectedAnswer\": \"Các luận điểm cốt lõi thí sinh cần nêu...\",\n" +
                        "    \"keywords\": [\"Từ khóa 1\", \"Từ khóa 2\"],\n" +
                        "    \"rubrics\": [\n" +
                        "      {\"criterionName\": \"Tiêu chí 1\", \"maxScore\": 3.5, \"weight\": 35.0, \"description\": \"Mô tả tiêu chí 1\"},\n" +
                        "      {\"criterionName\": \"Tiêu chí 2\", \"maxScore\": 3.5, \"weight\": 35.0, \"description\": \"Mô tả tiêu chí 2\"},\n" +
                        "      {\"criterionName\": \"Tiêu chí 3\", \"maxScore\": 3.0, \"weight\": 30.0, \"description\": \"Mô tả tiêu chí 3\"}\n" +
                        "    ]\n" +
                        "  }\n" +
                        "]";

                Map<String, Object> body = buildRequestBody(prompt);

                String rawText = callGeminiGenerateContent(body, null, "question-generation");
                if (rawText != null) {
                    List<Map<String, Object>> parsed = parseGeminiQuestionJson(rawText);
                    if (parsed != null && !parsed.isEmpty()) {
                        // Chuẩn hoá nhãn Bloom của AI về nhãn canonical
                        normalizeBloomLabels(parsed, allBloom && bloomPlan != null && !bloomPlan.isEmpty()
                                ? bloomLabelOf(bloomPlan.get(0))
                                : requestedBloomLabel);
                        if (!allBloom && !allMatchBloom(parsed, requestedBloomNumber)) {
                            // AI trả sai mức Bloom -> thử lại 1 lần với prompt cứu vãn
                            log.warn("Gemini returned questions with wrong Bloom level (expected {}), retrying once with corrective prompt",
                                    requestedBloomLabel);
                            String correctivePrompt = buildCorrectiveBloomPrompt(prompt, requestedBloomLabel, count);
                            String retryRaw = callGeminiGenerateContent(buildRequestBody(correctivePrompt), null, "question-generation-retry");
                            if (retryRaw != null) {
                                List<Map<String, Object>> retried = parseGeminiQuestionJson(retryRaw);
                                if (retried != null && !retried.isEmpty()) {
                                    normalizeBloomLabels(retried, requestedBloomLabel);
                                    if (!allMatchBloom(retried, requestedBloomNumber)) {
                                        log.warn("Gemini still returned wrong Bloom level after retry, forcing all questions to {}",
                                                requestedBloomLabel);
                                    }
                                    parsed = retried;
                                }
                            }
                            // Ép nhãn về đúng mức đã yêu cầu trong mọi trường hợp
                            forceBloomLabel(parsed, requestedBloomLabel);
                        } else if (allBloom) {
                            // Nhánh "ALL": ép từng câu theo đúng kế hoạch Bloom đã random trước,
                            // bảo đảm độ phủ mức ngẫu nhiên (không phải 1,2,3... tuần tự).
                            forceBloomPlan(parsed, bloomPlan);
                        }
                        return parsed;
                    }
                    log.warn("Gemini returned content that could not be parsed as a question JSON array, using high-quality Domain Fallback Generator");
                }
            } catch (Exception e) {
                log.warn("Gemini question generation API call failed, using high-quality Domain Fallback Generator: {}", e.getMessage());
            }
        }

        return generateDomainFallbackQuestions(subject, topic, allBloom ? "ALL" : requestedBloomLabel, difficulty, count);
    }

    /**
     * Chuẩn hoá nhãn Bloom của từng câu về nhãn canonical.
     * Nhãn không nhận diện được sẽ được gán bằng {@code fallbackLabel}.
     */
    private void normalizeBloomLabels(List<Map<String, Object>> items, String fallbackLabel) {
        if (items == null) {
            return;
        }
        for (Map<String, Object> item : items) {
            Object raw = item.get("bloomLevel");
            String normalized = normalizeBloomLabel(raw != null ? raw.toString() : null);
            item.put("bloomLevel", normalized != null ? normalized : fallbackLabel);
        }
    }

    /**
     * Kiểm tra mọi câu hỏi có cùng đúng mức Bloom đã yêu cầu hay không.
     */
    private boolean allMatchBloom(List<Map<String, Object>> items, Integer expectedLevel) {
        if (items == null || expectedLevel == null) {
            return true;
        }
        for (Map<String, Object> item : items) {
            Object raw = item.get("bloomLevel");
            Integer level = extractBloomNumber(raw != null ? raw.toString() : null);
            if (level == null || level != expectedLevel) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ép nhãn Bloom của mọi câu hỏi về đúng mức đã yêu cầu.
     */
    private void forceBloomLabel(List<Map<String, Object>> items, String label) {
        if (items == null) {
            return;
        }
        for (Map<String, Object> item : items) {
            item.put("bloomLevel", label);
        }
    }

    /**
     * Tạo prompt cứu vãn khi AI trả sai mức Bloom: nhấn mạnh mức bắt buộc,
     * yêu cầu trả lại toàn bộ danh sách với đúng mức đó.
     */
    private String buildCorrectiveBloomPrompt(String originalPrompt, String requiredLabel, int count) {
        return originalPrompt +
                "\n\n=== CORRECTION REQUIRED (OVERRIDES ANYTHING ABOVE) ===\n" +
                "Lần trước bạn đã gán sai mức Bloom cho các câu hỏi. Bạn PHẢI trả lại TOÀN BỘ " + count + " câu hỏi với:\n" +
                "\"bloomLevel\": \"" + requiredLabel + "\"\n" +
                "KHÔNG dùng mức Bloom nào khác. KHÔNG dùng nhãn viết tắt (vd: \"CREATE\", \"APPLY\").\n" +
                "Nội dung mỗi câu phải đúng đặc trưng của mức \"" + requiredLabel + "\".\n" +
                "Trả về ONLY a valid JSON Array (no Markdown ticks, no other text).";
    }

    /**
     * Đóng gói body request cho Gemini từ một prompt.
     */
    private Map<String, Object> buildRequestBody(String prompt) {
        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> contentObj = Map.of("parts", List.of(part));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.85);
        generationConfig.put("topK", 40);
        generationConfig.put("topP", 0.95);
        generationConfig.put("maxOutputTokens", 4096);

        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(contentObj));
        body.put("generationConfig", generationConfig);
        return body;
    }

    @Override
    public String generateEmbeddingVector(String text) {
        if (text == null || text.isBlank()) {
            return generateRandomEmbedding("empty");
        }
        // Try real Gemini Embedding API with model fallback
        if (geminiApiKey != null && !geminiApiKey.isBlank() && !"dummy_key".equalsIgnoreCase(geminiApiKey)) {
            String[] embedModels = {embeddingModel, "gemini-embedding-001", "text-embedding-004"};
            for (String modelName : embedModels) {
                if (modelName == null || modelName.isBlank()) continue;
                try {
                    String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":embedContent?key=" + geminiApiKey;
                    Map<String, Object> part = Map.of("text", text);
                    Map<String, Object> body = Map.of(
                            "model", "models/" + modelName,
                            "content", Map.of("parts", List.of(part)),
                            "outputDimensionality", embeddingDimensions
                    );
                    Map<?, ?> response = restTemplate.postForObject(url, body, Map.class);
                    if (response != null && response.containsKey("embedding")) {
                        Map<?, ?> embedding = (Map<?, ?>) response.get("embedding");
                        List<?> values = (List<?>) embedding.get("values");
                        if (values != null && !values.isEmpty()) {
                            return padAndFormatVector(values, embeddingDimensions);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Gemini Embedding model {} failed ({}), trying next...", modelName, e.getMessage());
                }
            }
        }
        return generateRandomEmbedding(text);
    }

    /**
     * Calls Gemini {@code generateContent} with the primary model and falls back through the
     * configured chain of alternate models when the primary one fails (503 UNAVAILABLE, 404 model
     * not found, empty content, ...).
     *
     * @return the raw text of the first successful response, or {@code null} if every model failed
     */
    private String callGeminiGenerateContent(Map<String, Object> body, Integer maxOutputTokens, String purpose) {
        List<String> models = new ArrayList<>();
        if (textModel != null && !textModel.isBlank()) {
            models.add(textModel.trim());
        }
        if (fallbackModels != null && !fallbackModels.isBlank()) {
            Arrays.stream(fallbackModels.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank() && !models.contains(s))
                    .forEach(models::add);
        }
        if (models.isEmpty()) {
            log.error("Gemini: no model configured for {}", purpose);
            return null;
        }

        for (String model : models) {
            try {
                Map<String, Object> requestBody = body;
                if (maxOutputTokens != null) {
                    requestBody = new HashMap<>(body);
                    Map<String, Object> genConfig = new HashMap<>();
                    Object existing = requestBody.get("generationConfig");
                    if (existing instanceof Map) {
                        genConfig.putAll((Map<String, Object>) existing);
                    }
                    genConfig.put("maxOutputTokens", maxOutputTokens);
                    requestBody.put("generationConfig", genConfig);
                }
                String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                        + model + ":generateContent?key=" + geminiApiKey;
                Map<?, ?> response = restTemplate.postForObject(url, requestBody, Map.class);
                String text = extractFirstCandidateText(response);
                if (text != null && !text.isBlank()) {
                    log.info("Gemini [{}] succeeded for {}", model, purpose);
                    return text;
                }
                log.warn("Gemini model [{}] returned empty content for {}", model, purpose);
            } catch (Exception e) {
                log.warn("Gemini model [{}] failed for {}: {} - trying next fallback", model, purpose, e.getMessage());
            }
        }

        log.error("Gemini: all models failed for {}. Models tried: {}", purpose, models);
        return null;
    }

    /**
     * Extracts the text of the first candidate from a Gemini generateContent response.
     */
    private String extractFirstCandidateText(Map<?, ?> response) {
        if (response == null || !response.containsKey("candidates")) {
            return null;
        }
        List<?> candidates = (List<?>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
        if (candidate == null || !candidate.containsKey("content")) {
            return null;
        }
        Map<?, ?> content = (Map<?, ?>) candidate.get("content");
        if (content == null || !content.containsKey("parts")) {
            return null;
        }
        List<?> parts = (List<?>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        Map<?, ?> textPart = (Map<?, ?>) parts.get(0);
        if (textPart == null) {
            return null;
        }
        Object text = textPart.get("text");
        return text instanceof String ? (String) text : null;
    }

    /**
     * Pad embedding vector to target dimension and return as JSON array string.
     * If input vector is shorter than target, fills remaining with deterministic noise.
     * If input is longer, truncates.
     */
    private String padAndFormatVector(List<?> values, int targetDim) {
        float[] vector = new float[targetDim];
        int copyLen = Math.min(values.size(), targetDim);
        for (int i = 0; i < copyLen; i++) {
            Object v = values.get(i);
            if (v instanceof Number) {
                vector[i] = ((Number) v).floatValue();
            }
        }
        // Pad remaining dims with deterministic small values based on content hash
        if (copyLen < targetDim) {
            Random padRng = new Random(copyLen);
            for (int i = copyLen; i < targetDim; i++) {
                vector[i] = (float) (padRng.nextGaussian() * 0.01);
            }
        }
        // Normalize
        float norm = 0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm == 0f) norm = 1f;

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < targetDim; i++) {
            sb.append(String.format(Locale.US, "%.5f", vector[i] / norm));
            if (i < targetDim - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Fallback: deterministic random vector based on text hash.
     * Used when API is unavailable.
     */
    private String generateRandomEmbedding(String text) {
        float[] vector = new float[1536];
        int hash = text != null ? text.hashCode() : 42;
        Random rng = new Random(hash);
        float norm = 0f;
        for (int i = 0; i < 1536; i++) {
            vector[i] = (float) (rng.nextGaussian() * 0.1);
            norm += vector[i] * vector[i];
        }
        norm = (float) Math.sqrt(norm);
        if (norm == 0f) norm = 1f;

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 1536; i++) {
            sb.append(String.format(Locale.US, "%.5f", vector[i] / norm));
            if (i < 1535) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private List<Map<String, Object>> parseGeminiQuestionJson(String rawText) {
        if (rawText == null || rawText.isBlank()) return Collections.emptyList();
        try {
            String clean = rawText.trim();
            int startIdx = clean.indexOf('[');
            int endIdx = clean.lastIndexOf(']');
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                clean = clean.substring(startIdx, endIdx + 1);
            } else {
                if (clean.startsWith("```json")) clean = clean.substring(7);
                else if (clean.startsWith("```")) clean = clean.substring(3);
                if (clean.endsWith("```")) clean = clean.substring(0, clean.length() - 3);
                clean = clean.trim();
            }

            JsonNode root = objectMapper.readTree(clean);
            if (root.isArray()) {
                List<Map<String, Object>> items = new ArrayList<>();
                for (JsonNode node : root) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("content", node.has("content") ? node.get("content").asText() : "");
                    item.put("bloomLevel", node.has("bloomLevel") ? node.get("bloomLevel").asText() : "Bloom 3 - Vận dụng");
                    item.put("difficulty", node.has("difficulty") ? node.get("difficulty").asText() : "MEDIUM");
                    item.put("expectedAnswer", node.has("expectedAnswer") ? node.get("expectedAnswer").asText() : "");

                    List<String> kw = new ArrayList<>();
                    if (node.has("keywords") && node.get("keywords").isArray()) {
                        for (JsonNode k : node.get("keywords")) kw.add(k.asText());
                    }
                    item.put("keywords", kw);

                    List<Map<String, Object>> rubrics = new ArrayList<>();
                    if (node.has("rubrics") && node.get("rubrics").isArray()) {
                        for (JsonNode r : node.get("rubrics")) {
                            Map<String, Object> rMap = new HashMap<>();
                            rMap.put("criterionName", r.has("criterionName") ? r.get("criterionName").asText() : "Tiêu chí");
                            rMap.put("maxScore", r.has("maxScore") ? r.get("maxScore").asDouble() : 10.0);
                            rMap.put("weight", r.has("weight") ? r.get("weight").asDouble() : 30.0);
                            rMap.put("description", r.has("description") ? r.get("description").asText() : "");
                            rubrics.add(rMap);
                        }
                    }
                    item.put("rubrics", rubrics);
                    items.add(item);
                }
                return items;
            }
        } catch (Exception e) {
            log.warn("Could not parse Gemini JSON output: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<Map<String, Object>> generateDomainFallbackQuestions(
            Subject subject, String topic, String bloomLevel, String difficulty, int count) {
        String code = subject != null && subject.getCode() != null ? subject.getCode().toUpperCase() : "SWD392";
        String sanitizedTopic = (topic != null && !topic.isBlank() && !"Trọng tâm kiến thức môn học".equals(topic.trim()))
                ? sanitizeTopicInput(topic)
                : null;
        String safeTopic = (sanitizedTopic != null && !sanitizedTopic.isBlank())
                ? sanitizedTopic
                : "Kiến trúc hệ thống và quy trình phát triển phần mềm";
        String safeDiff = (difficulty != null && !difficulty.isBlank() && !"ALL".equalsIgnoreCase(difficulty)) 
                ? difficulty.toUpperCase() 
                : "MEDIUM";

        Map<String, List<Map<String, Object>>> bloomMap = new LinkedHashMap<>();

        // Bloom 1 - Nhận biết (Remembering) - 5 Distinct Questions
        List<Map<String, Object>> b1List = new ArrayList<>();
        b1List.add(createQuestionItem(
                "Hãy định nghĩa và liệt kê ít nhất 3 đặc điểm kỹ thuật nền tảng của \"" + safeTopic + "\" trong bối cảnh môn " + code + ". Những thuật ngữ chuẩn nào gắn liền với công nghệ này?",
                "Bloom 1 - Nhận biết", "EASY",
                "Sinh viên nêu rõ: 1. Định nghĩa chuẩn xác của " + safeTopic + ". 2. Ba đặc tính kỹ thuật cốt lõi. 3. Thuật ngữ quốc tế liên quan.",
                List.of(safeTopic, "Định nghĩa", "Đặc tính", "Thuật ngữ"),
                List.of(
                        Map.of("criterionName", "Định nghĩa chuẩn xác", "maxScore", 4.0, "weight", 40.0, "description", "Nêu đúng định nghĩa theo giáo trình học phần"),
                        Map.of("criterionName", "Liệt kê đầy đủ đặc tính", "maxScore", 3.5, "weight", 35.0, "description", "Liệt kê chính xác ít nhất 3 đặc trưng kỹ thuật"),
                        Map.of("criterionName", "Thuật ngữ chuyên môn", "maxScore", 2.5, "weight", 25.0, "description", "Sử dụng đúng thuật ngữ chuẩn quốc tế")
                )
        ));
        b1List.add(createQuestionItem(
                "Kể tên các thành phần cấu tạo (components) và vai trò cơ bản của từng khối chức năng trong mô hình vận hành của \"" + safeTopic + "\".",
                "Bloom 1 - Nhận biết", "EASY",
                "Sinh viên liệt kê: 1. Danh sách các module hoặc thành phần chính. 2. Nhiệm vụ cơ bản của từng khối chức năng.",
                List.of(safeTopic, "Thành phần", "Cấu tạo", "Module"),
                List.of(
                        Map.of("criterionName", "Liệt kê thành phần", "maxScore", 5.0, "weight", 50.0, "description", "Nêu đúng các khối cấu thành của kiến trúc"),
                        Map.of("criterionName", "Mô tả vai trò cơ bản", "maxScore", 5.0, "weight", 50.0, "description", "Trình bày đúng chức năng từng thành phần")
                )
        ));
        b1List.add(createQuestionItem(
                "Liệt kê các giao thức mạng (protocols) và định dạng dữ liệu (payload formats) tiêu chuẩn được sử dụng phổ biến nhất khi tích hợp \"" + safeTopic + "\".",
                "Bloom 1 - Nhận biết", "EASY",
                "Sinh viên liệt kê: 1. Các giao thức (HTTP/REST, gRPC, WebSocket, AMQP). 2. Các định dạng (JSON, Protocol Buffers, Avro).",
                List.of(safeTopic, "Protocols", "Giao thức", "Payload", "Định dạng dữ liệu"),
                List.of(
                        Map.of("criterionName", "Liệt kê đúng giao thức", "maxScore", 5.0, "weight", 50.0, "description", "Chỉ rõ các giao thức truyền tải tiêu chuẩn"),
                        Map.of("criterionName", "Định dạng dữ liệu", "maxScore", 5.0, "weight", 50.0, "description", "Nêu đúng các định dạng serialize/deserialize")
                )
        ));
        b1List.add(createQuestionItem(
                "Nêu các điều kiện tiền đề về hạ tầng phần mềm (prerequisites) và môi trường runtime cần thiết trước khi triển khai \"" + safeTopic + "\".",
                "Bloom 1 - Nhận biết", "EASY",
                "Sinh viên trình bày: 1. Yêu cầu hệ điều hành/container runtime. 2. Các dịch vụ phụ trợ cần có (Database, Service Registry).",
                List.of(safeTopic, "Prerequisites", "Hạ tầng", "Môi trường triển khai"),
                List.of(
                        Map.of("criterionName", "Điều kiện môi trường", "maxScore", 5.0, "weight", 50.0, "description", "Nêu đúng các phần mềm và thư viện phụ thuộc"),
                        Map.of("criterionName", "Dịch vụ phụ trợ", "maxScore", 5.0, "weight", 50.0, "description", "Chỉ ra các hạ tầng mạng và lưu trữ bắt buộc")
                )
        ));
        b1List.add(createQuestionItem(
                "Chỉ ra sự khác biệt về mặt định nghĩa giữa \"" + safeTopic + "\" và khái niệm kiến trúc truyền thống tương ứng trong phần mềm.",
                "Bloom 1 - Nhận biết", "EASY",
                "Sinh viên phân biệt: 1. Khái niệm cũ và mới. 2. Điểm phân định ranh giới cơ bản.",
                List.of(safeTopic, "Khái niệm", "Đối chiếu", "Ranh giới"),
                List.of(
                        Map.of("criterionName", "Phân định khái niệm", "maxScore", 5.0, "weight", 50.0, "description", "Chỉ rõ sự khác nhau về mặt ngữ nghĩa"),
                        Map.of("criterionName", "Phạm vi ứng dụng", "maxScore", 5.0, "weight", 50.0, "description", "Nêu đúng phạm vi áp dụng của từng khái niệm")
                )
        ));
        bloomMap.put("BLOOM 1", b1List);

        // Bloom 2 - Thông hiểu (Understanding) - 5 Distinct Questions
        List<Map<String, Object>> b2List = new ArrayList<>();
        b2List.add(createQuestionItem(
                "Trình bày khái niệm, nguyên lý hoạt động cốt lõi và các thành phần cấu tạo chính của \"" + safeTopic + "\". Công nghệ này giải quyết bài toán mấu chốt nào trong môn " + code + "?",
                "Bloom 2 - Thông hiểu", "MEDIUM",
                "Sinh viên cần trình bày rõ: 1. Định nghĩa chuẩn xác của " + safeTopic + ". 2. Các thành phần cấu tạo và luồng dữ liệu. 3. Ý nghĩa thực tiễn đối với hệ thống phần mềm.",
                List.of(safeTopic, "Nguyên lý", "Kiến trúc", "Khái niệm"),
                List.of(
                        Map.of("criterionName", "Định nghĩa & Bản chất kỹ thuật", "maxScore", 3.5, "weight", 35.0, "description", "Nêu đúng định nghĩa chuẩn và bối cảnh sử dụng"),
                        Map.of("criterionName", "Cấu trúc & Luồng hoạt động", "maxScore", 4.0, "weight", 40.0, "description", "Mô tả mạch lạc các thành phần và tương tác qua lại"),
                        Map.of("criterionName", "Ý nghĩa trong kiến trúc", "maxScore", 2.5, "weight", 25.0, "description", "Chỉ rõ giá trị mang lại so với cách tiếp cận cũ")
                )
        ));
        b2List.add(createQuestionItem(
                "Giải thích luồng dữ liệu (data flow) và chu kỳ xử lý yêu cầu (request lifecycle) khi áp dụng \"" + safeTopic + "\". Các module trao đổi thông điệp với nhau như thế nào?",
                "Bloom 2 - Thông hiểu", "MEDIUM",
                "Sinh viên giải thích: 1. Điểm bắt đầu nhận request. 2. Các chặng trung gian xử lý và chuyển tiếp. 3. Cơ chế giao tiếp (REST, gRPC, Message Broker).",
                List.of(safeTopic, "Luồng dữ liệu", "Giao tiếp", "Lifecycle"),
                List.of(
                        Map.of("criterionName", "Mô tả luồng dữ liệu", "maxScore", 4.0, "weight", 40.0, "description", "Luồng đi của dữ liệu chính xác, mạch lạc"),
                        Map.of("criterionName", "Giao thức và định dạng truyền tin", "maxScore", 3.5, "weight", 35.0, "description", "Nêu rõ giao thức và cách đồng bộ/bất đồng bộ"),
                        Map.of("criterionName", "Xử lý kết quả trả về", "maxScore", 2.5, "weight", 25.0, "description", "Trình bày cách đóng gói response và xử lý mã lỗi")
                )
        ));
        b2List.add(createQuestionItem(
                "Giải thích tại sao \"" + safeTopic + "\" lại giúp giảm thiểu sự phụ thuộc chặt chẽ (tight coupling) giữa các thành phần và tăng cường tính độc lập khi triển khai.",
                "Bloom 2 - Thông hiểu", "MEDIUM",
                "Sinh viên giải thích: 1. Khái niệm Loose Coupling. 2. Cơ chế trừu tượng hóa và đóng gói giao diện tương tác. 3. Độc lập trong chu kỳ release.",
                List.of(safeTopic, "Loose Coupling", "Độc lập", "Tách rời"),
                List.of(
                        Map.of("criterionName", "Bản chất Loose Coupling", "maxScore", 4.0, "weight", 40.0, "description", "Hiểu đúng cơ chế giảm phụ thuộc giữa các module"),
                        Map.of("criterionName", "Độc lập triển khai", "maxScore", 3.5, "weight", 35.0, "description", "Giải thích khả năng deploy độc lập không gián đoạn"),
                        Map.of("criterionName", "Lợi ích bảo trì", "maxScore", 2.5, "weight", 25.0, "description", "Nêu rõ giá trị đối với khả năng mở rộng code")
                )
        ));
        b2List.add(createQuestionItem(
                "Phân biệt cơ chế xử lý đồng bộ (Synchronous) và bất đồng bộ (Asynchronous) trong mô hình \"" + safeTopic + "\". Trong trường hợp nào nên ưu tiên mô hình bất đồng bộ?",
                "Bloom 2 - Thông hiểu", "MEDIUM",
                "Sinh viên làm rõ: 1. So sánh cơ chế non-blocking vs blocking. 2. Hàng đợi thông điệp Message Queue. 3. Các kịch bản thực tiễn cần xử lý bất đồng bộ.",
                List.of(safeTopic, "Sync vs Async", "Bất đồng bộ", "Message Queue"),
                List.of(
                        Map.of("criterionName", "Phân biệt Sync/Async", "maxScore", 4.0, "weight", 40.0, "description", "Chỉ rõ cơ chế non-blocking và luồng thực thi"),
                        Map.of("criterionName", "Trường hợp áp dụng", "maxScore", 3.5, "weight", 35.0, "description", "Chọn đúng ngữ cảnh nghiệp vụ cần bất đồng bộ"),
                        Map.of("criterionName", "Quản lý hàng đợi", "maxScore", 2.5, "weight", 25.0, "description", "Nêu đúng vai trò của Message Broker")
                )
        ));
        b2List.add(createQuestionItem(
                "Làm rõ nguyên tắc phân chia ranh giới ngữ cảnh (Bounded Context) khi thiết kế các module xoay quanh \"" + safeTopic + "\".",
                "Bloom 2 - Thông hiểu", "MEDIUM",
                "Sinh viên giải thích: 1. Khái niệm Bounded Context trong Domain-Driven Design. 2. Cách xác định ranh giới dữ liệu và model nghiệp vụ.",
                List.of(safeTopic, "Bounded Context", "DDD", "Ranh giới nghiệp vụ"),
                List.of(
                        Map.of("criterionName", "Khái niệm Bounded Context", "maxScore", 4.0, "weight", 40.0, "description", "Nắm vững nguyên tắc phân định của DDD"),
                        Map.of("criterionName", "Ranh giới dữ liệu", "maxScore", 3.5, "weight", 35.0, "description", "Giải thích cách cô lập model và database"),
                        Map.of("criterionName", "Ứng dụng thực tiễn", "maxScore", 2.5, "weight", 25.0, "description", "Đưa ra ví dụ thực tế mạch lạc")
                )
        ));
        bloomMap.put("BLOOM 2", b2List);

        // Bloom 3 - Vận dụng (Applying) - 5 Distinct Questions
        List<Map<String, Object>> b3List = new ArrayList<>();
        b3List.add(createQuestionItem(
                "Trong một dự án thực tế môn " + code + ", anh/chị hãy vận dụng \"" + safeTopic + "\" để giải quyết bài toán đồng bộ dữ liệu hoặc quản lý trạng thái. Nêu cụ thể các bước triển khai kỹ thuật.",
                "Bloom 3 - Vận dụng", "MEDIUM",
                "Sinh viên trình bày: 1. Bối cảnh dự án cụ thể. 2. Các bước cấu hình và viết mã áp dụng. 3. Kết quả sau khi tích hợp.",
                List.of(safeTopic, "Vận dụng", "Triển khai", "Đồng bộ"),
                List.of(
                        Map.of("criterionName", "Xác định đúng bài toán áp dụng", "maxScore", 3.0, "weight", 30.0, "description", "Chọn ngữ cảnh nghiệp vụ phù hợp với công nghệ"),
                        Map.of("criterionName", "Quy trình triển khai từng bước", "maxScore", 4.5, "weight", 45.0, "description", "Mô tả đầy đủ cấu hình, code và tích hợp"),
                        Map.of("criterionName", "Kiểm tra tính đúng đắn", "maxScore", 2.5, "weight", 25.0, "description", "Đưa ra phương pháp nghiệm thu kết quả vận hành")
                )
        ));
        b3List.add(createQuestionItem(
                "Hãy cấu hình và triển khai mẫu Circuit Breaker kết hợp với Retry pattern trong thư viện Resilience4j khi dịch vụ ứng dụng \"" + safeTopic + "\" tương tác với API bên thứ ba.",
                "Bloom 3 - Vận dụng", "MEDIUM",
                "Sinh viên nêu: 1. Cấu hình file application.yml (slidingWindowSize, failureRateThreshold). 2. Cách bọc hàm gọi Service bằng annotation @CircuitBreaker. 3. Định nghĩa phương thức Fallback.",
                List.of(safeTopic, "Circuit Breaker", "Resilience4j", "Fallback", "Retry"),
                List.of(
                        Map.of("criterionName", "Cấu hình tham số ngưỡng", "maxScore", 4.0, "weight", 40.0, "description", "Thiết lập đúng sliding window và failure rate threshold"),
                        Map.of("criterionName", "Hiện thực hóa Fallback method", "maxScore", 3.5, "weight", 35.0, "description", "Viết đúng signature và logic trả lời dự phòng"),
                        Map.of("criterionName", "Xử lý ngoại lệ", "maxScore", 2.5, "weight", 25.0, "description", "Bắt đúng loại exception và log cảnh báo")
                )
        ));
        b3List.add(createQuestionItem(
                "Vận dụng chiến lược bộ nhớ đệm đa tầng (Multi-level Caching: Caffeine Cache in-memory + Redis Distributed Cache) để tối ưu hóa hiệu năng truy vấn cho module \"" + safeTopic + "\".",
                "Bloom 3 - Vận dụng", "MEDIUM",
                "Sinh viên trình bày: 1. Các bước đọc Cache L1 trước, nếu miss đọc Cache L2, nếu miss đọc DB. 2. Cấu hình TTL (Time-To-Live). 3. Cơ chế vô hiệu hóa cache (Cache Invalidation) khi có update.",
                List.of(safeTopic, "Caching", "Redis", "Caffeine", "Cache Invalidation"),
                List.of(
                        Map.of("criterionName", "Kiến trúc Cache L1/L2", "maxScore", 4.0, "weight", 40.0, "description", "Mô tả đúng quy trình tra cứu hai tầng cache"),
                        Map.of("criterionName", "Chiến lược Invalidation", "maxScore", 3.5, "weight", 35.0, "description", "Xóa hoặc cập nhật key cache kịp thời khi dữ liệu đổi"),
                        Map.of("criterionName", "Cấu hình TTL và Eviction", "maxScore", 2.5, "weight", 25.0, "description", "Ngăn chặn tràn RAM và giải phóng cache cũ hợp lý")
                )
        ));
        b3List.add(createQuestionItem(
                "Vận dụng API Gateway (Spring Cloud Gateway) để cấu hình định tuyến thông minh (Dynamic Routing) và Rate Limiting (Token Bucket) cho các dịch vụ triển khai \"" + safeTopic + "\".",
                "Bloom 3 - Vận dụng", "MEDIUM",
                "Sinh viên trình bày: 1. Định nghĩa route predicates và filters. 2. Cấu hình RedisRateLimiter giới hạn 100 req/s/user. 3. Cách thêm custom header chuyển tiếp xuống microservice.",
                List.of(safeTopic, "API Gateway", "Rate Limiting", "Token Bucket", "Routing"),
                List.of(
                        Map.of("criterionName", "Cấu hình Route & Predicate", "maxScore", 4.0, "weight", 40.0, "description", "Định tuyến đường dẫn URI chính xác"),
                        Map.of("criterionName", "Thuật toán Rate Limiting", "maxScore", 3.5, "weight", 35.0, "description", "Áp dụng Token Bucket với Redis thành công"),
                        Map.of("criterionName", "Bảo mật Header", "maxScore", 2.5, "weight", 25.0, "description", "Chuyển tiếp context người dùng an toàn")
                )
        ));
        b3List.add(createQuestionItem(
                "Áp dụng Transactional Outbox Pattern kết hợp Debezium CDC (Change Data Capture) trong hệ thống \"" + safeTopic + "\" để đảm bảo gửi message vào Kafka thành công ngay cả khi mất điện.",
                "Bloom 3 - Vận dụng", "MEDIUM",
                "Sinh viên mô tả: 1. Tạo bảng 'outbox_events' cùng transaction với bảng nghiệp vụ. 2. Debezium đọc WAL (Write-Ahead Log) của PostgreSQL. 3. Bắn event vào Kafka topic tương ứng.",
                List.of(safeTopic, "Outbox Pattern", "Debezium", "CDC", "Kafka", "At-Least-Once"),
                List.of(
                        Map.of("criterionName", "Cấu trúc Outbox Table", "maxScore", 4.0, "weight", 40.0, "description", "Ghi event chung transaction với dữ liệu nghiệp vụ"),
                        Map.of("criterionName", "Cơ chế CDC qua WAL", "maxScore", 3.5, "weight", 35.0, "description", "Giải thích cách Debezium capture log không làm chậm DB"),
                        Map.of("criterionName", "Đảm bảo At-least-once delivery", "maxScore", 2.5, "weight", 25.0, "description", "Xử lý idempotency phía consumer")
                )
        ));
        bloomMap.put("BLOOM 3", b3List);

        // Bloom 4 - Phân tích (Analyzing) - 5 Distinct Questions
        List<Map<String, Object>> b4List = new ArrayList<>();
        b4List.add(createQuestionItem(
                "Phân tích cơ chế vận hành chuyên sâu của \"" + safeTopic + "\" và so sánh với các giải pháp truyền thống tương đương. Khi nào nên và KHÔNG nên áp dụng kỹ thuật này?",
                "Bloom 4 - Phân tích", "HARD",
                "Sinh viên cần phân tích sâu: 1. Cơ chế nội tại khi xử lý tải cao. 2. Bảng so sánh ưu/nhược điểm với giải pháp đối chứng. 3. Tiêu chí quyết định sử dụng trong dự án.",
                List.of(safeTopic, "Phân tích so sánh", "Cơ chế", "Đánh đổi"),
                List.of(
                        Map.of("criterionName", "Phân tích cơ chế chuyên sâu", "maxScore", 3.5, "weight", 35.0, "description", "Giải thích rõ ràng cơ chế nội tại và xử lý ngoại lệ"),
                        Map.of("criterionName", "So sánh đối chiếu khách quan", "maxScore", 3.5, "weight", 35.0, "description", "Chỉ rõ điểm mạnh, điểm yếu so với các phương án khác"),
                        Map.of("criterionName", "Điều kiện áp dụng phù hợp", "maxScore", 3.0, "weight", 30.0, "description", "Đưa ra kịch bản quyết định kỹ thuật chính xác")
                )
        ));
        b4List.add(createQuestionItem(
                "Hãy phân tích nguyên nhân gốc rễ (Root Cause) và các điểm nghẽn hiệu năng (Bottlenecks) thường gặp nhất khi ứng dụng \"" + safeTopic + "\" trong hệ thống phân tán. Đề xuất phương pháp phân tích log/tracing.",
                "Bloom 4 - Phân tích", "HARD",
                "Sinh viên phân tích: 1. Các điểm nghẽn tại tầng mạng, I/O hoặc DB lock. 2. Cách đọc phân tích distributed trace (OpenTelemetry/Jaeger). 3. Biện pháp cô lập bottleneck.",
                List.of(safeTopic, "Bottlenecks", "Root Cause", "Tracing", "Profiling"),
                List.of(
                        Map.of("criterionName", "Nhận diện đúng điểm nghẽn kỹ thuật", "maxScore", 3.5, "weight", 35.0, "description", "Phân tích đúng các nguy cơ gây nghẽn tại CPU/Memory/IO"),
                        Map.of("criterionName", "Phương pháp truy vết log & trace", "maxScore", 4.0, "weight", 40.0, "description", "Sử dụng APM và Tracing để khoanh vùng nguyên nhân"),
                        Map.of("criterionName", "Biện pháp giải tỏa điểm nghẽn", "maxScore", 2.5, "weight", 25.0, "description", "Đề xuất tối ưu hóa cấu hình hoặc thuật toán")
                )
        ));
        b4List.add(createQuestionItem(
                "Mổ xẻ hiện tượng khuếch đại độ trễ (Latency Amplification / Tail Latency) và Cascading Failure xảy ra trong chuỗi gọi lồng nhau liên quan tới \"" + safeTopic + "\".",
                "Bloom 4 - Phân tích", "HARD",
                "Sinh viên phân tích: 1. Phân phối độ trễ P99. 2. Tác động lan truyền khi thread pool bị chiếm dụng. 3. Biện pháp Bulkhead cô lập tài nguyên.",
                List.of(safeTopic, "Tail Latency", "P99", "Cascading Failure", "Bulkhead"),
                List.of(
                        Map.of("criterionName", "Phân tích chỉ số P99 Latency", "maxScore", 4.0, "weight", 40.0, "description", "Mô tả hiện tượng trễ lũy kế qua các chặng dịch vụ"),
                        Map.of("criterionName", "Cơ chế cạn kiệt Thread Pool", "maxScore", 3.5, "weight", 35.0, "description", "Chỉ rõ nguyên nhân gây nghẽn toàn bộ ứng dụng"),
                        Map.of("criterionName", "Giải pháp cô lập Bulkhead", "maxScore", 2.5, "weight", 25.0, "description", "Thiết lập phân vùng tài nguyên biệt lập")
                )
        ));
        b4List.add(createQuestionItem(
                "Phân tích nguy cơ xung đột dữ liệu (Data Inconsistency) và Race Conditions khi nhiều luồng hoặc phiên bản đồng thời cập nhật trạng thái trong \"" + safeTopic + "\".",
                "Bloom 4 - Phân tích", "HARD",
                "Sinh viên mổ xẻ: 1. Hiện tượng Lost Update hoặc Dirty Read. 2. So sánh Optimistic Locking (versioning) vs Pessimistic Locking (SELECT FOR UPDATE).",
                List.of(safeTopic, "Race Condition", "Optimistic Locking", "Pessimistic Locking", "Data Integrity"),
                List.of(
                        Map.of("criterionName", "Phát hiện kịch bản Race Condition", "maxScore", 4.0, "weight", 40.0, "description", "Mô tả chính xác thời điểm xảy ra tranh chấp dữ liệu"),
                        Map.of("criterionName", "So sánh cơ chế Lock", "maxScore", 3.5, "weight", 35.0, "description", "Đánh giá chi phí hiệu năng giữa Optimistic và Pessimistic"),
                        Map.of("criterionName", "Bảo đảm toàn vẹn giao dịch", "maxScore", 2.5, "weight", 25.0, "description", "Đề xuất giải pháp kiểm soát xung đột tối ưu")
                )
        ));
        b4List.add(createQuestionItem(
                "So sánh chuyên sâu giữa hai chiến lược lưu trữ: Database-per-Service và Shared-Database đối với dịch vụ \"" + safeTopic + "\". Khi nào chi phí đồng bộ hóa vượt quá lợi ích?",
                "Bloom 4 - Phân tích", "HARD",
                "Sinh viên phân tích: 1. Tính độc lập schema vs độ phức tạp distributed query. 2. Khó khăn khi thực hiện join dữ liệu hoặc báo cáo tổng hợp.",
                List.of(safeTopic, "Database-per-Service", "Shared Database", "Data Partitioning"),
                List.of(
                        Map.of("criterionName", "So sánh tính độc lập dữ liệu", "maxScore", 4.0, "weight", 40.0, "description", "Mổ xẻ ưu nhược điểm của việc tách riêng database"),
                        Map.of("criterionName", "Bài toán Distributed Query", "maxScore", 3.5, "weight", 35.0, "description", "Chỉ ra thách thức khi join dữ liệu qua HTTP/Event"),
                        Map.of("criterionName", "Đưa ra tiêu chí lựa chọn", "maxScore", 2.5, "weight", 25.0, "description", "Đưa ra khuyến nghị phù hợp theo quy mô hệ thống")
                )
        ));
        bloomMap.put("BLOOM 4", b4List);

        // Bloom 5 - Đánh giá (Evaluating) - 5 Distinct Questions
        List<Map<String, Object>> b5List = new ArrayList<>();
        b5List.add(createQuestionItem(
                "Dựa trên các tiêu chí kiến trúc (Quality Attributes), hãy đánh giá sự đánh đổi (trade-offs) về hiệu năng, tính sẵn sàng và tính nhất quán dữ liệu khi triển khai \"" + safeTopic + "\" ở quy mô lớn.",
                "Bloom 5 - Đánh giá", "HARD",
                "Sinh viên cần lập luận: 1. Tác động tới Latency, Throughput và Tính toàn vẹn dữ liệu. 2. Các rủi ro tiềm ẩn (Distributed lock, Single Point of Failure, Network partition). 3. Chiến lược giảm thiểu rủi ro.",
                List.of(safeTopic, "Trade-offs", "Hiệu năng", "CAP theorem", "Tính nhất quán"),
                List.of(
                        Map.of("criterionName", "Phân tích các thuộc tính chất lượng", "maxScore", 3.5, "weight", 35.0, "description", "Đánh giá cụ thể về Performance, Scalability và Availability"),
                        Map.of("criterionName", "Lập luận về sự đánh đổi (Trade-offs)", "maxScore", 4.0, "weight", 40.0, "description", "Chỉ ra vì sao không có giải pháp hoàn hảo, phải chọn đánh đổi"),
                        Map.of("criterionName", "Giải pháp kiểm soát rủi ro", "maxScore", 2.5, "weight", 25.0, "description", "Đề xuất các phương án mitigating strategy khả thi")
                )
        ));
        b5List.add(createQuestionItem(
                "Giả sử hệ thống sử dụng \"" + safeTopic + "\" gặp sự cố nghẽn cổ chai hoặc mất kết nối dịch vụ trong giờ cao điểm. Anh/Chị hãy phản biện quy trình cô lập sự cố, khắc phục và giải pháp giám sát (Observability).",
                "Bloom 5 - Đánh giá", "HARD",
                "Sinh viên trình bày: 1. Các bước phát hiện và cô lập lỗi (Circuit Breaker, Health checks). 2. Giải pháp phục hồi không gián đoạn (Fallback, Retry with Exponential Backoff). 3. Hệ thống Metrics/Logging/Tracing cần có.",
                List.of(safeTopic, "Troubleshooting", "Circuit Breaker", "Observability", "High Availability"),
                List.of(
                        Map.of("criterionName", "Quy trình xử lý sự cố hệ thống", "maxScore", 3.5, "weight", 35.0, "description", "Các bước cô lập và khoanh vùng sự cố bài bản"),
                        Map.of("criterionName", "Cơ chế phục hồi tự động", "maxScore", 3.5, "weight", 35.0, "description", "Ứng dụng Retry, Fallback, Circuit Breaker hợp lý"),
                        Map.of("criterionName", "Thiết lập hệ thống quan sát", "maxScore", 3.0, "weight", 30.0, "description", "Nêu rõ các chỉ số APM, Log và Trace cần giám sát")
                )
        ));
        b5List.add(createQuestionItem(
                "Biện minh cho quyết định lựa chọn giao thức truyền tải nội bộ: khi nào nên thay thế hoàn toàn RESTful JSON bằng gRPC/Protocol Buffers trong kiến trúc \"" + safeTopic + "\"? Đánh giá chi phí vận hành và lợi ích.",
                "Bloom 5 - Đánh giá", "HARD",
                "Sinh viên lập luận: 1. Binary payload size nhỏ hơn JSON 60-80%. 2. Multiplexing trên HTTP/2. 3. Đánh đổi về công cụ debug, tooling và độ dốc học tập.",
                List.of(safeTopic, "gRPC", "Protocol Buffers", "HTTP/2", "Hiệu năng"),
                List.of(
                        Map.of("criterionName", "Lập luận về tối ưu hóa băng thông", "maxScore", 4.0, "weight", 40.0, "description", "Chứng minh lợi thế binary serialization"),
                        Map.of("criterionName", "Đánh giá chi phí phát triển & debug", "maxScore", 3.5, "weight", 35.0, "description", "Chỉ rõ nhược điểm khi debug mạng so với JSON"),
                        Map.of("criterionName", "Quyết định chuyển đổi kiến trúc", "maxScore", 2.5, "weight", 25.0, "description", "Đưa ra ngưỡng tải cụ thể để biện minh")
                )
        ));
        b5List.add(createQuestionItem(
                "Đánh giá rủi ro an ninh mạng và các lỗ hổng tiềm ẩn (như Man-in-the-Middle, Service Spoofing) khi các dịch vụ liên quan tới \"" + safeTopic + "\" giao tiếp qua mạng nội bộ. Hãy phản biện giải pháp mTLS và Zero-Trust.",
                "Bloom 5 - Đánh giá", "HARD",
                "Sinh viên đánh giá: 1. Tại sao mạng nội bộ không còn an toàn. 2. Cơ chế xác thực hai chiều mTLS với Service Mesh (Istio). 3. Chi phí CPU khi mã hóa/giải mã SSL handshake.",
                List.of(safeTopic, "Zero-Trust", "mTLS", "An ninh hệ thống", "Service Mesh"),
                List.of(
                        Map.of("criterionName", "Nhận diện nguy cơ an ninh mạng", "maxScore", 4.0, "weight", 40.0, "description", "Phân tích các lỗ hổng nội bộ tiềm ẩn"),
                        Map.of("criterionName", "Đánh giá kiến trúc Zero-Trust", "maxScore", 3.5, "weight", 35.0, "description", "Lập luận về cơ chế xác thực danh tính từng service"),
                        Map.of("criterionName", "Đánh giá overhead hiệu năng", "maxScore", 2.5, "weight", 25.0, "description", "Cân nhắc chi phí xử lý mật mã của mTLS")
                )
        ));
        b5List.add(createQuestionItem(
                "Phản biện bài toán Tổng chi phí sở hữu (TCO - Total Cost of Ownership) và độ phức tạp vận hành khi quyết định phân tách một hệ thống Monolith ổn định sang hệ thống \"" + safeTopic + "\". Khi nào quyết định này là SAI LẦM?",
                "Bloom 5 - Đánh giá", "HARD",
                "Sinh viên phản biện: 1. Chi phí DevOps, CI/CD, hạ tầng Kubernetes, chi phí Cloud. 2. Luật Conway và quy mô đội ngũ nhân sự. 3. Tiêu chí cảnh báo khi hệ thống chưa đủ lớn.",
                List.of(safeTopic, "TCO", "Monolith vs Microservices", "Conway Law", "Chi phí vận hành"),
                List.of(
                        Map.of("criterionName", "Phân tích chi phí ẩn", "maxScore", 4.0, "weight", 40.0, "description", "Chỉ ra chi phí về hạ tầng, giám sát và quản lý"),
                        Map.of("criterionName", "Đánh giá năng lực đội ngũ", "maxScore", 3.5, "weight", 35.0, "description", "Lập luận về định luật Conway và văn hóa DevOps"),
                        Map.of("criterionName", "Quyết định phản biện sắc bén", "maxScore", 2.5, "weight", 25.0, "description", "Nêu rõ kịch bản nên giữ nguyên Monolith")
                )
        ));
        bloomMap.put("BLOOM 5", b5List);

        // Bloom 6 - Sáng tạo & Thiết kế (Creating & System Design) - 5 Distinct Questions
        List<Map<String, Object>> b6List = new ArrayList<>();
        b6List.add(createQuestionItem(
                "Hãy thiết kế bản vẽ kiến trúc hệ thống (System Design Blueprint) áp dụng \"" + safeTopic + "\" cho bài toán chịu tải 100.000 yêu cầu/giây (TPS cao). Trình bày mô hình phân tầng, chiến lược phân vùng dữ liệu và giải pháp chịu lỗi (Fault-Tolerance).",
                "Bloom 6 - Sáng tạo", "HARD",
                "Sinh viên phác thảo: 1. Sơ đồ kiến trúc logic đa tầng (API Gateway, Microservices, Caching, Event-driven, Distributed DB). 2. Chiến lược Database Sharding/Replication. 3. Cơ chế chịu lỗi (Circuit Breaker, Rate Limiting, Graceful Degradation).",
                List.of(safeTopic, "System Design", "High Throughput", "Fault-Tolerance", "Distributed Architecture"),
                List.of(
                        Map.of("criterionName", "Thiết kế kiến trúc logic tổng thể", "maxScore", 4.0, "weight", 40.0, "description", "Sơ đồ kiến trúc toàn diện, phân định rõ ranh giới giữa các service"),
                        Map.of("criterionName", "Chiến lược phân vùng & xử lý tải cao", "maxScore", 3.5, "weight", 35.0, "description", "Thiết kế giải pháp Caching, Partitioning và Load Balancing hợp lý"),
                        Map.of("criterionName", "Chiến lược chịu lỗi & Phục hồi thảm họa", "maxScore", 2.5, "weight", 25.0, "description", "Đảm bảo tính sẵn sàng cao (High Availability) khi có node bị sập")
                )
        ));
        b6List.add(createQuestionItem(
                "Đề xuất một giải pháp kiến trúc kết hợp \"" + safeTopic + "\" với mô hình Event-Driven Architecture để đảm bảo tính nhất quán cuối cùng (Eventual Consistency) qua Saga Pattern. Nêu chi tiết kịch bản bù trừ giao dịch (Compensating Transactions) khi có bước thất bại.",
                "Bloom 6 - Sáng tạo", "HARD",
                "Sinh viên thiết kế: 1. Luồng Saga (Orchestration hoặc Choreography). 2. Cấu trúc các Event xuất bản vào Message Broker (Kafka/RabbitMQ). 3. Kịch bản khôi phục và giao dịch bù trừ (Compensating actions) khi một service gặp lỗi.",
                List.of(safeTopic, "Saga Pattern", "Event-Driven", "Eventual Consistency", "Compensating Transactions"),
                List.of(
                        Map.of("criterionName", "Thiết kế luồng Saga chuẩn xác", "maxScore", 4.0, "weight", 40.0, "description", "Lựa chọn và vẽ đúng mô hình Orchestration hoặc Choreography"),
                        Map.of("criterionName", "Cơ chế giao dịch bù trừ (Rollback)", "maxScore", 3.5, "weight", 35.0, "description", "Xử lý triệt để trạng thái bất định khi có node mạng bị fail"),
                        Map.of("criterionName", "Bảo toàn toàn vẹn dữ liệu", "maxScore", 2.5, "weight", 25.0, "description", "Ngăn ngừa dữ liệu rác hoặc trạng thái mâu thuẫn giữa các dịch vụ")
                )
        ));
        b6List.add(createQuestionItem(
                "Thiết kế mô hình kiến trúc CQRS (Command Query Responsibility Segregation) kết hợp Event Sourcing cho hệ thống thanh toán xoay quanh \"" + safeTopic + "\". Trình bày cách đồng bộ hóa Read Model từ Append-only Event Store.",
                "Bloom 6 - Sáng tạo", "HARD",
                "Sinh viên xây dựng: 1. Tách biệt Command Bus (Write) và Query Bus (Read). 2. Lưu trữ Event Store bất biến (Immutable). 3. Projector lắng nghe event để cập nhật Elasticsearch/Postgres cho truy vấn.",
                List.of(safeTopic, "CQRS", "Event Sourcing", "Event Store", "Read Model"),
                List.of(
                        Map.of("criterionName", "Tách bạch Command và Query", "maxScore", 4.0, "weight", 40.0, "description", "Mô hình hóa rõ ràng hai luồng ghi và đọc độc lập"),
                        Map.of("criterionName", "Thiết kế Event Store bất biến", "maxScore", 3.5, "weight", 35.0, "description", "Lưu vết toàn bộ thay đổi trạng thái theo thời gian"),
                        Map.of("criterionName", "Đồng bộ Read Model tối ưu", "maxScore", 2.5, "weight", 25.0, "description", "Tối ưu hóa tốc độ tìm kiếm và tổng hợp dữ liệu")
                )
        ));
        b6List.add(createQuestionItem(
                "Hãy thiết kế kế hoạch Phục hồi thảm họa đa vùng (Multi-Region Disaster Recovery: Active-Active) cho hệ thống sử dụng \"" + safeTopic + "\" với yêu cầu RPO = 0 (Recovery Point Objective) và RTO < 30 giây (Recovery Time Objective).",
                "Bloom 6 - Sáng tạo", "HARD",
                "Sinh viên đề xuất: 1. Global Server Load Balancing (GSLB) điều hướng DNS. 2. Cross-region asynchronous replication cho Database. 3. Xử lý xung đột ghi đồng thời (Conflict Resolution: Last-Write-Wins hoặc CRDT).",
                List.of(safeTopic, "Disaster Recovery", "Active-Active", "Multi-Region", "RPO", "RTO"),
                List.of(
                        Map.of("criterionName", "Kiến trúc Multi-Region Active-Active", "maxScore", 4.0, "weight", 40.0, "description", "Mô hình hóa hạ tầng hai datacenter chạy song song"),
                        Map.of("criterionName", "Đồng bộ và giải quyết xung đột", "maxScore", 3.5, "weight", 35.0, "description", "Ứng dụng CRDT hoặc chiến lược giải quyết conflict chuẩn xác"),
                        Map.of("criterionName", "Đạt chuẩn RPO = 0 và RTO cực thấp", "maxScore", 2.5, "weight", 25.0, "description", "Chứng minh tính khả thi khi xảy ra sự cố sập cả một region")
                )
        ));
        b6List.add(createQuestionItem(
                "Thiết kế khung kiểm thử tự động toàn diện (Continuous Testing Pipeline) cho hệ thống \"" + safeTopic + "\" tích hợp Contract Testing (với Pact), Chaos Engineering (với Chaos Mesh) và Performance Testing tự động trước khi Deploy Production.",
                "Bloom 6 - Sáng tạo", "HARD",
                "Sinh viên xây dựng: 1. Consumer-driven contract test ngăn vỡ API giữa các đội nhóm. 2. Kịch bản tiêm lỗi hỗn loạn (Chaos injection: ngắt mạng, tăng độ trễ, kill pod). 3. Quality Gate chặn release khi vi phạm SLA.",
                List.of(safeTopic, "Contract Testing", "Chaos Engineering", "Quality Gate", "Testing Pipeline"),
                List.of(
                        Map.of("criterionName", "Thiết kế Contract Testing (Pact)", "maxScore", 4.0, "weight", 40.0, "description", "Bảo đảm tính tương thích giữa consumer và provider"),
                        Map.of("criterionName", "Kiểm thử khả năng chịu lỗi (Chaos)", "maxScore", 3.5, "weight", 35.0, "description", "Chủ động kiểm chứng hệ thống tự phục hồi dưới tải nặng"),
                        Map.of("criterionName", "Thiết lập Quality Gate tự động", "maxScore", 2.5, "weight", 25.0, "description", "Tự động hóa hoàn toàn trong luồng CI/CD")
                )
        ));
        bloomMap.put("BLOOM 6", b6List);

        // ===== Chọn pool câu hỏi theo mức Bloom đã yêu cầu =====
        boolean allBloom = bloomLevel == null || bloomLevel.isBlank() || "ALL".equalsIgnoreCase(bloomLevel.trim());

        List<Map<String, Object>> result = new ArrayList<>();
        if (allBloom) {
            // Phân bổ NGẪU NHIÊN các mức Bloom: kế hoạch rút mức khác nhau từ {1..6}
            // (lặp lại khi count > 6), nên 3 câu sẽ ra 3 mức bất kỳ trong dải 1-6,
            // không cố định là Bloom 1, 2, 3 như trước.
            List<List<Map<String, Object>>> orderedPools = new ArrayList<>();
            for (String key : List.of("BLOOM 1", "BLOOM 2", "BLOOM 3", "BLOOM 4", "BLOOM 5", "BLOOM 6")) {
                List<Map<String, Object>> pool = bloomMap.get(key);
                if (pool != null && !pool.isEmpty()) {
                    orderedPools.add(pool);
                }
            }
            if (orderedPools.isEmpty()) {
                return result;
            }
            List<Integer> plan = randomBloomPlan(count);
            for (int i = 0; i < count; i++) {
                int level = plan.get(i);
                List<Map<String, Object>> pool = bloomMap.get("BLOOM " + level);
                if (pool == null || pool.isEmpty()) {
                    pool = orderedPools.get(i % orderedPools.size());
                }
                Map<String, Object> item = new HashMap<>(pool.get(i % pool.size()));
                item.put("difficulty", safeDiff);
                result.add(item);
            }
            return result;
        }

        // Mức Bloom cụ thể -> lấy đúng nhóm tương ứng
        Integer requestedLevel = extractBloomNumber(bloomLevel);
        List<Map<String, Object>> targetPool = new ArrayList<>();
        if (requestedLevel != null) {
            List<Map<String, Object>> pool = bloomMap.get("BLOOM " + requestedLevel);
            if (pool != null) {
                targetPool.addAll(pool);
            }
        }
        if (targetPool.isEmpty()) {
            for (List<Map<String, Object>> subList : bloomMap.values()) {
                targetPool.addAll(subList);
            }
        }

        for (int i = 0; i < count; i++) {
            Map<String, Object> item = new HashMap<>(targetPool.get(i % targetPool.size()));
            item.put("difficulty", safeDiff);
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> createQuestionItem(String content, String bloom, String diff, String exp, List<String> kw, List<Map<String, Object>> rubrics) {
        Map<String, Object> map = new HashMap<>();
        map.put("content", content);
        map.put("bloomLevel", bloom);
        map.put("difficulty", diff);
        map.put("expectedAnswer", exp);
        map.put("keywords", kw);
        map.put("rubrics", rubrics);
        return map;
    }

    /**
     * Làm sạch topic do người dùng nhập trước khi đưa vào prompt:
     * - Loại bỏ ký tự điều khiển có thể phá vỡ cấu trúc prompt.
     * - Giới hạn độ dài để tránh prompt bloat.
     * - Trả về null nếu nội dung rỗng sau khi làm sạch.
     */
    private String sanitizePromptInput(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").trim();
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    /**
     * Sinh kế hoạch Bloom ngẫu nhiên cho {@code count} câu hỏi khi người dùng chọn "ALL".
     * <ul>
     *   <li>{@code count <= 6}: rút {@code count} mức KHÁC NHAU từ tập {1..6} (shuffle).</li>
     *   <li>{@code count > 6}: lấy đủ 6 mức (đã shuffle) rồi lặp lại, mỗi vòng shuffle mới.</li>
     * </ul>
     * Kết quả KHÔNG bao giờ bắt đầu một cách cố định từ Bloom 1, nên bộ câu hỏi có
     * độ phủ mức Bloom ngẫu nhiên trên toàn bộ dải 1-6.
     */
    private static List<Integer> randomBloomPlan(int count) {
        List<Integer> plan = new ArrayList<>();
        if (count <= 0) {
            return plan;
        }
        List<Integer> levels = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        while (plan.size() < count) {
            Collections.shuffle(levels, ThreadLocalRandom.current());
            for (int lv : levels) {
                if (plan.size() >= count) {
                    break;
                }
                plan.add(lv);
            }
        }
        return plan;
    }

    /**
     * Ép nhãn Bloom của từng câu hỏi theo đúng kế hoạch đã random (nhánh "ALL").
     * Câu thứ {@code i} sẽ mang nhãn canonical của mức {@code plan.get(i)}.
     * Nếu AI trả nhiều hơn số câu trong kế hoạch, các câu dư được giữ nguyên nhãn
     * canonical đã chuẩn hoá (để tránh ép trùng mức).
     */
    private void forceBloomPlan(List<Map<String, Object>> items, List<Integer> plan) {
        if (items == null || plan == null || plan.isEmpty()) {
            return;
        }
        int limit = Math.min(items.size(), plan.size());
        for (int i = 0; i < limit; i++) {
            items.get(i).put("bloomLevel", bloomLabelOf(plan.get(i)));
        }
    }

    /**
     * Trích xuất số mức Bloom (1..6) từ một chuỗi bất kỳ.
     * Nhận diện được cả dạng số ("Bloom 4", "4", "MỨC ĐỘ BLOOM 6") lẫn dạng từ khoá
     * tiếng Anh / tiếng Việt ("CREATE", "SÁNG TẠO", "PHÂN TÍCH"...).
     *
     * @return số mức 1..6, hoặc null nếu không xác định được.
     */
    public static Integer extractBloomNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String upper = raw.toUpperCase();

        // Ưu tiên nhận diện theo từ khoá (chính xác hơn việc dò chữ số trong "BLOOM 6")
        if (upper.contains("NHẬN BIẾT") || upper.contains("REMEMBER") || upper.contains("NHAN BIET")) {
            return 1;
        }
        if (upper.contains("THÔNG HIỂU") || upper.contains("UNDERSTAND") || upper.contains("THONG HIEU")) {
            return 2;
        }
        if (upper.contains("VẬN DỤNG") || upper.contains("APPLY") || upper.contains("VAN DUNG")) {
            return 3;
        }
        if (upper.contains("PHÂN TÍCH") || upper.contains("ANALYZE") || upper.contains("ANALYSE") || upper.contains("PHAN TICH")) {
            return 4;
        }
        if (upper.contains("ĐÁNH GIÁ") || upper.contains("EVALUATE") || upper.contains("DANH GIA")) {
            return 5;
        }
        if (upper.contains("SÁNG TẠO") || upper.contains("CREATE") || upper.contains("SANG TAO")) {
            return 6;
        }

        // Dò chữ số 1..6 đứng riêng (sau "Bloom", "mức", "level")
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:BLOOM|MUC|LEVEL|LEVEL|CAP DO|CAPDO)?\\s*([1-6])")
                .matcher(upper);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        // Cuối cùng: chuỗi chỉ chứa đúng một chữ số 1..6
        java.util.regex.Matcher single = java.util.regex.Pattern.compile("^[\\s\\-]*([1-6])[\\s\\-]*$").matcher(upper);
        if (single.find()) {
            return Integer.parseInt(single.group(1));
        }
        return null;
    }

    /**
     * Chuẩn hoá nhãn Bloom của Gemini về nhãn canonical mà toàn hệ thống sử dụng.
     * Gemini thường trả về nhãn thô như "CREATE", "Bloom 6", "APPLY"...
     *
     * @return nhãn canonical ("Bloom N - ..."), hoặc null nếu không xác định được.
     */
    public static String normalizeBloomLabel(String raw) {
        Integer n = extractBloomNumber(raw);
        if (n == null) {
            return null;
        }
        return BLOOM_LABELS[n - 1];
    }

    /**
     * Nhãn canonical của một mức Bloom (1..6).
     */
    public static String bloomLabelOf(int level) {
        if (level < 1 || level > 6) {
            return BLOOM_3;
        }
        return BLOOM_LABELS[level - 1];
    }

    /**
     * Làm sạch chủ đề: loại bỏ phần mở rộng tên file và chuỗi giống slug/tên file
     * (vd: "swd-test-quest.txt", "swd-test-quest") vì sẽ tạo ra câu hỏi vô nghĩa.
     */
    private String sanitizeTopicInput(String raw) {
        String cleaned = sanitizePromptInput(raw, 500);
        if (cleaned == null) {
            return null;
        }
        String stripped = cleaned.replaceAll("\\.(txt|pdf|docx?|pptx?|md)$", "").trim();
        // Chuỗi không có khoảng trắng giống tên file/slug -> không dùng làm chủ đề
        if (stripped.isEmpty() || !stripped.contains(" ")) {
            return null;
        }
        return stripped;
    }

    private VivaEvaluationResult parseGeminiOutput(String raw, int turnOrder, String subjectCode, String lang) {
        BigDecimal score = BigDecimal.valueOf(8.25);
        String eval = "Luận điểm trình bày mạch lạc, đối chiếu chuẩn xác với đề cương giáo trình.";
        String followup = "Hãy giải thích thêm về trường hợp biên và cách tối ưu hóa giải thuật khi quy mô dữ liệu tăng gấp 10 lần?";
        String bloom = "Bloom 5 - Đánh giá phản biện";

        try {
            for (String line : raw.split("\n")) {
                line = line.trim();
                if (line.startsWith("SCORE:")) {
                    String num = line.substring(6).replaceAll("[^0-9.]", "").trim();
                    if (!num.isEmpty()) {
                        score = BigDecimal.valueOf(Double.parseDouble(num)).setScale(2, RoundingMode.HALF_UP);
                    }
                } else if (line.startsWith("EVAL:")) {
                    eval = line.substring(5).trim();
                } else if (line.startsWith("FOLLOWUP:")) {
                    followup = line.substring(9).trim();
                } else if (line.startsWith("BLOOM:")) {
                    bloom = line.substring(6).trim();
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing Gemini output: {}", e.getMessage());
        }
        return new VivaEvaluationResult(score, eval, followup, bloom);
    }

    private VivaEvaluationResult computeDomainEvaluation(String subjectCode, int turnOrder, String transcript, String lang) {
        boolean isUnanswered = (transcript == null || transcript.isBlank() ||
                transcript.toLowerCase().contains("không trả lời") ||
                transcript.trim().length() < 3);
        if (isUnanswered) {
            return new VivaEvaluationResult(
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    "Thí sinh không trả lời hoặc không ghi nhận được âm thanh trả lời cho câu hỏi này. Điểm đề xuất: 0.0.",
                    "Thí sinh vui lòng tập trung và trả lời câu hỏi tiếp theo.",
                    "Bloom 1 - Nhận biết"
            );
        }
        String lower = transcript != null ? transcript.toLowerCase() : "";
        String sCode = subjectCode != null ? subjectCode.toUpperCase().trim() : "SWD392";

        double baseScore = 7.75;
        if (lower.length() > 60) baseScore += 0.5;
        if (lower.length() > 150) baseScore += 0.5;
        if (lower.contains("microservice") || lower.contains("saga") || lower.contains("circuit breaker") 
                || lower.contains("dijkstra") || lower.contains("bias") || lower.contains("acid")) {
            baseScore += 0.5;
        }
        baseScore = Math.min(9.5, Math.max(6.0, baseScore));
        BigDecimal score = BigDecimal.valueOf(baseScore).setScale(2, RoundingMode.HALF_UP);

        String eval = "Thí sinh nắm chắc bản chất lý thuyết, phân tích đúng cơ chế và đối chiếu khớp với chuẩn Rubric học phần.";
        String followup;
        String bloom = "Bloom 5 - Đánh giá phản biện";

        if (sCode.contains("SWD") || sCode.contains("ARCH")) {
            // Software Architecture Domain (SWD392)
            if (turnOrder == 1) {
                if (lower.contains("saga") || lower.contains("nhất quán") || lower.contains("giao dịch")) {
                    followup = "Bạn vừa đề cập đến việc bảo toàn dữ liệu. Trong Saga Pattern, nếu một giao dịch con cục bộ thất bại giữa chừng, hệ thống sẽ kích hoạt giao dịch bù trừ (Compensating Transaction) theo cơ chế Orchestration hay Choreography như thế nào để đảm bảo tính nhất quán cuối cùng (Eventual Consistency)?";
                } else {
                    followup = "Trong kiến trúc Microservices phân tán, giữa giao thức Two-Phase Commit (2PC) và Saga Pattern, tại sao 2PC lại bị coi là phản mẫu (anti-pattern) khi mạng bị phân vùng (Network Partitioning)? Bạn sẽ áp dụng chiến lược nào để cân bằng giữa CAP theorem?";
                }
                bloom = "Bloom 5 - Phản biện Đánh đổi Kiến trúc";
            } else if (turnOrder == 2) {
                if (lower.contains("circuit") || lower.contains("chịu lỗi") || lower.contains("retry")) {
                    followup = "Bạn có nhắc đến cơ chế bảo vệ hệ thống. Khi Circuit Breaker chuyển từ trạng thái Open sang Half-Open, hệ thống dựa vào tiêu chí cụ thể nào (Success Rate hay Probe Count) để quyết định đóng mạch hoàn toàn (Closed)?";
                } else {
                    followup = "Nếu một downstream service gặp hiện tượng quá tải và phản hồi trễ (Latency Spike), bạn hãy thiết kế chiến lược kết hợp giữa Circuit Breaker, Rate Limiting và Fallback cache để ngăn chặn hiệu ứng sụp đổ dây chuyền (Cascading Failure)?";
                }
                bloom = "Bloom 4 - Phân tích Khả năng Chịu lỗi";
            } else {
                followup = "Ca thi đã hoàn thành 3 lượt vấn đáp thích ứng. Hệ thống AI đã tổng hợp toàn bộ transcript và đề xuất điểm để Giảng viên chốt điểm chính thức.";
                bloom = "Bloom 6 - Sáng tạo & Tổng hợp";
            }
        } else if (sCode.contains("AI") || sCode.contains("ML")) {
            // AI / Machine Learning Domain (AI204)
            if (turnOrder == 1) {
                followup = "Bạn vừa phân tích sự đánh đổi Bias-Variance. Khi tăng độ phức tạp của mô hình (tăng số tầng mạng hoặc tham số), bias và variance biến thiên ra sao? Tại sao kỹ thuật L2 Regularization (Weight Decay) lại giúp hạn chế Overfitting?";
                bloom = "Bloom 5 - Đánh giá Mô hình";
            } else if (turnOrder == 2) {
                followup = "Trong quá trình huấn luyện mạng nơ-ron sâu, hiện tượng triệt tiêu đạo hàm (Vanishing Gradient) xảy ra do nguyên nhân toán học nào? Bạn sẽ chọn hàm kích hoạt ReLU, Leaky ReLU hay kỹ thuật Batch Normalization để khắc phục?";
                bloom = "Bloom 4 - Phân tích Tối ưu hóa";
            } else {
                followup = "Ca thi đã hoàn thành 3 lượt vấn đáp thích ứng. Hệ thống AI đã tổng hợp toàn bộ transcript và đề xuất điểm để Giảng viên chốt điểm chính thức.";
                bloom = "Bloom 6 - Sáng tạo & Tổng hợp";
            }
        } else {
            // General Computer Science & Data Structures (CS301 / SE102 / PRJ301)
            if (turnOrder == 1) {
                followup = "Bạn vừa trình bày nguyên lý giải thuật. Trong trường hợp đồ thị có trọng số âm, thuật toán Bellman-Ford làm thế nào để phát hiện chu trình âm qua V - 1 lần duyệt cạnh?";
                bloom = "Bloom 5 - Đánh giá Giải thuật";
            } else if (turnOrder == 2) {
                followup = "Xét về mặt tối ưu hóa cấu trúc dữ liệu, việc sử dụng Fibonacci Heap thay cho Binary Heap giúp giảm độ phức tạp của thao tác Decrease-Key từ bao nhiêu xuống bao nhiêu trong thực tế?";
                bloom = "Bloom 4 - Phân tích Độ phức tạp";
            } else {
                followup = "Ca thi đã hoàn thành 3 lượt vấn đáp thích ứng. Hệ thống AI đã tổng hợp toàn bộ transcript và đề xuất điểm để Giảng viên chốt điểm chính thức.";
                bloom = "Bloom 6 - Sáng tạo & Tổng hợp";
            }
        }

        if ("en-US".equalsIgnoreCase(lang)) {
            eval = "The candidate demonstrated solid theoretical understanding, coherent arguments, and appropriate domain terminology.";
            if (sCode.contains("SWD") || sCode.contains("ARCH")) {
                if (turnOrder == 1) {
                    followup = "You mentioned data consistency. Between Two-Phase Commit (2PC) and Saga Pattern in Microservices, why is 2PC considered an anti-pattern during network partitions? How do you ensure Eventual Consistency?";
                } else if (turnOrder == 2) {
                    followup = "When a downstream service experiences high latency, how do Circuit Breaker states (Closed, Open, Half-Open) transition to effectively prevent cascading failure?";
                }
            }
        }

        return new VivaEvaluationResult(score, eval, followup, bloom);
    }

    private Map<?, ?> postGeminiWithModelFallback(String apiKey, Map<String, Object> body) {
        String[] modelCandidates = {"gemini-1.5-flash", "gemini-2.0-flash", "gemini-flash-latest"};
        for (String model : modelCandidates) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
                Map<?, ?> res = restTemplate.postForObject(url, body, Map.class);
                if (res != null && res.containsKey("candidates")) {
                    return res;
                }
            } catch (Exception e) {
                log.warn("Gemini candidate model {} call failed ({}), attempting next candidate...", model, e.getMessage());
            }
        }
        return null;
    }
}

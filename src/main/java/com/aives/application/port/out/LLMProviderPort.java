package com.aives.application.port.out;

import com.aives.dto.response.VivaEvaluationResult;
import com.aives.entity.Rubric;
import com.aives.entity.Subject;

import java.util.List;
import java.util.Map;

public interface LLMProviderPort {
    VivaEvaluationResult evaluateVivaTurn(
            String subjectCode,
            int turnOrder,
            String currentQuestion,
            String studentTranscript,
            List<String> ragSnippets,
            List<Rubric> rubrics,
            String language
    );

    List<Map<String, Object>> generateQuestionsFromSyllabus(
            Subject subject,
            String topic,
            String bloomLevel,
            String difficulty,
            int count,
            String ragContext
    );

    /**
     * Biến thể nhận thêm context tự do từ giảng viên.
     * customContext được đưa vào prompt với ưu tiên cao hơn ragContext.
     */
    default List<Map<String, Object>> generateQuestionsFromSyllabus(
            Subject subject,
            String topic,
            String bloomLevel,
            String difficulty,
            int count,
            String ragContext,
            String customContext
    ) {
        return generateQuestionsFromSyllabus(subject, topic, bloomLevel, difficulty, count, ragContext);
    }

    default List<Map<String, Object>> generateQuestionsFromSyllabus(
            Subject subject,
            String topic,
            String bloomLevel,
            int count,
            String ragContext
    ) {
        return generateQuestionsFromSyllabus(subject, topic, bloomLevel, "MEDIUM", count, ragContext);
    }
}

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
            int count,
            String ragContext
    );
}

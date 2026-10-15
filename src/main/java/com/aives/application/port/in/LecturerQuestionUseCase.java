package com.aives.application.port.in;

import com.aives.dto.request.QuestionGenerateRequest;
import com.aives.dto.request.QuestionRequest;
import com.aives.dto.response.QuestionResponse;

import java.util.List;
import java.util.UUID;

public interface LecturerQuestionUseCase {
    List<QuestionResponse> getQuestions(UUID subjectUuid, String difficulty, String keyword, String status);
    QuestionResponse getQuestionById(UUID id);
    QuestionResponse createQuestion(QuestionRequest request);
    QuestionResponse updateQuestion(UUID id, QuestionRequest request);
    void deleteQuestion(UUID id);
    QuestionResponse toggleApproveQuestion(UUID id);
    int approveAllPending(UUID subjectUuid, String subjectCode);
    List<QuestionResponse> generateQuestionsRAG(QuestionGenerateRequest request);
}

package com.aives.application.port.in;

import com.aives.dto.request.ExamStartRequest;
import com.aives.dto.request.LecturerFinalizeScoreRequest;
import com.aives.dto.request.VivaTurnSubmitRequest;
import com.aives.dto.request.VivaTurnWsRequest;
import com.aives.dto.response.ExamSessionResponse;
import com.aives.dto.response.VivaTurnResponse;
import com.aives.dto.response.VivaTurnWsResponse;

import java.util.List;
import java.util.UUID;

public interface StudentExamUseCase {
    List<ExamSessionResponse> getStudentSessions(UUID studentId);
    ExamSessionResponse getSessionById(UUID sessionId);
    List<VivaTurnResponse> getSessionTurns(UUID sessionId);
    ExamSessionResponse startExamSession(UUID studentId, ExamStartRequest request);
    VivaTurnResponse submitVivaTurn(UUID sessionId, VivaTurnSubmitRequest request);
    ExamSessionResponse finishExamSession(UUID sessionId);
    VivaTurnWsResponse processVivaTurn(UUID sessionId, VivaTurnWsRequest request);
    ExamSessionResponse finalizeExamSessionScore(UUID sessionId, LecturerFinalizeScoreRequest request);
}

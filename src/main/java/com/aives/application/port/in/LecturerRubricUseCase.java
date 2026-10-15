package com.aives.application.port.in;

import com.aives.dto.request.RubricRequest;
import com.aives.dto.response.RubricResponse;

import java.util.List;
import java.util.UUID;

public interface LecturerRubricUseCase {
    List<RubricResponse> getRubricsBySubject(UUID subjectUuid);
    List<RubricResponse> getRubricsByQuestion(UUID questionUuid);
    RubricResponse createRubric(RubricRequest request);
    RubricResponse updateRubric(UUID id, RubricRequest request);
    void deleteRubric(UUID id);
}

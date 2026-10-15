package com.aives.application.port.in;

import com.aives.dto.request.AssignmentPermissionRequest;
import com.aives.dto.request.SubjectAssignRequest;
import com.aives.dto.request.SubjectLanguageConfigDto;
import com.aives.dto.request.SubjectRequest;
import com.aives.dto.response.SubjectAssignmentResponse;
import com.aives.dto.response.SubjectResponse;

import java.util.List;
import java.util.UUID;

public interface SubjectUseCase {
    List<SubjectResponse> getAllSubjects();
    SubjectResponse getSubjectById(UUID id);
    SubjectResponse getSubjectByCode(String code);
    SubjectResponse createSubject(SubjectRequest request);
    SubjectResponse updateSubject(UUID id, SubjectRequest request);
    void deleteSubject(UUID id);
    List<SubjectAssignmentResponse> getAllAssignments();
    List<SubjectAssignmentResponse> getLecturerAssignments(UUID lecturerId);
    List<SubjectAssignmentResponse> getSubjectLecturers(UUID subjectId);
    SubjectAssignmentResponse assignSubject(SubjectAssignRequest request);
    SubjectAssignmentResponse updateAssignmentPermissions(UUID assignmentId, AssignmentPermissionRequest request);
    void removeAssignment(UUID assignmentId);
    void removeLecturerFromSubject(UUID subjectId, UUID lecturerId);
    SubjectResponse updateLanguageConfig(UUID subjectId, SubjectLanguageConfigDto request);
}

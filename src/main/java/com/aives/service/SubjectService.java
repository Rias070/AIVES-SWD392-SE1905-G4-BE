package com.aives.service;

import com.aives.dto.request.AssignmentPermissionRequest;
import com.aives.dto.request.SubjectAssignRequest;
import com.aives.dto.request.SubjectRequest;
import com.aives.dto.response.SubjectAssignmentResponse;
import com.aives.dto.response.SubjectResponse;
import com.aives.entity.Subject;
import com.aives.entity.SubjectLecturer;
import com.aives.entity.User;
import com.aives.enums.ErrorCode;
import com.aives.exception.AppException;
import com.aives.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final SubjectLecturerRepository subjectLecturerRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final DocumentRepository documentRepository;

    public List<SubjectResponse> getAllSubjects() {
        return subjectRepository.findAll().stream()
                .filter(s -> !"INACTIVE".equalsIgnoreCase(s.getStatus()))
                .map(this::mapToSubjectResponse)
                .collect(Collectors.toList());
    }

    public SubjectResponse getSubjectById(UUID id) {
        Subject subject = subjectRepository.findById(id)
                .filter(s -> !"INACTIVE".equalsIgnoreCase(s.getStatus()))
                .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));
        return mapToSubjectResponse(subject);
    }

    @Transactional
    public SubjectResponse createSubject(SubjectRequest request) {
        log.info("Creating subject with code: {}", request.getCode());
        if (subjectRepository.existsByCode(request.getCode())) {
            throw new AppException(ErrorCode.SUBJECT_ALREADY_EXISTS, "Mã môn học đã tồn tại: " + request.getCode());
        }

        Subject subject = Subject.builder()
                .code(request.getCode().trim().toUpperCase())
                .name(request.getName().trim())
                .description(request.getDescription())
                .credits(request.getCredits() != null ? request.getCredits() : 3)
                .status("ACTIVE")
                .build();

        subject = subjectRepository.save(subject);
        return mapToSubjectResponse(subject);
    }

    @Transactional
    public SubjectResponse updateSubject(UUID id, SubjectRequest request) {
        log.info("Updating subject: {}", id);
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));

        if (!subject.getCode().equalsIgnoreCase(request.getCode().trim())
                && subjectRepository.existsByCode(request.getCode().trim())) {
            throw new AppException(ErrorCode.SUBJECT_ALREADY_EXISTS, "Mã môn học đã tồn tại: " + request.getCode());
        }

        subject.setCode(request.getCode().trim().toUpperCase());
        subject.setName(request.getName().trim());
        subject.setDescription(request.getDescription());
        if (request.getCredits() != null) {
            subject.setCredits(request.getCredits());
        }

        subject = subjectRepository.save(subject);
        return mapToSubjectResponse(subject);
    }

    @Transactional
    public void deleteSubject(UUID id) {
        log.info("Soft deleting subject: {}", id);
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));
        subject.setStatus("INACTIVE");
        subjectRepository.save(subject);
    }

    // ==========================================
    // LECTURER ASSIGNMENTS
    // ==========================================
    public List<SubjectAssignmentResponse> getAllAssignments() {
        return subjectLecturerRepository.findAll().stream()
                .map(this::mapToAssignmentResponse)
                .collect(Collectors.toList());
    }

    public List<SubjectAssignmentResponse> getLecturerAssignments(UUID lecturerId) {
        return subjectLecturerRepository.findByLecturerUuid(lecturerId).stream()
                .map(this::mapToAssignmentResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public SubjectAssignmentResponse assignSubject(SubjectAssignRequest request) {
        log.info("Assigning subject {} to lecturer {}", request.getSubjectUuid(), request.getLecturerUuid());

        Subject subject = subjectRepository.findById(request.getSubjectUuid())
                .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));

        User lecturer = userRepository.findById(request.getLecturerUuid())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED, "Không tìm thấy giảng viên"));

        if (subjectLecturerRepository.existsBySubjectUuidAndLecturerUuid(subject.getUuid(), lecturer.getUuid())) {
            throw new AppException(ErrorCode.INVALID_KEY, "Giảng viên này đã được phân công môn học này trước đó.");
        }

        SubjectLecturer assignment = SubjectLecturer.builder()
                .subject(subject)
                .lecturer(lecturer)
                .canApproveRag(request.getCanApproveRag() != null ? request.getCanApproveRag() : true)
                .canEditRubric(request.getCanEditRubric() != null ? request.getCanEditRubric() : true)
                .build();

        assignment = subjectLecturerRepository.save(assignment);
        return mapToAssignmentResponse(assignment);
    }

    @Transactional
    public SubjectAssignmentResponse updateAssignmentPermissions(UUID assignmentId, AssignmentPermissionRequest request) {
        SubjectLecturer assignment = subjectLecturerRepository.findById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_KEY, "Phân công môn học không tồn tại."));

        if (request.getCanApproveRag() != null) {
            assignment.setCanApproveRag(request.getCanApproveRag());
        }
        if (request.getCanEditRubric() != null) {
            assignment.setCanEditRubric(request.getCanEditRubric());
        }

        assignment = subjectLecturerRepository.save(assignment);
        return mapToAssignmentResponse(assignment);
    }

    @Transactional
    public void removeAssignment(UUID assignmentId) {
        if (!subjectLecturerRepository.existsById(assignmentId)) {
            throw new AppException(ErrorCode.INVALID_KEY, "Phân công môn học không tồn tại.");
        }
        subjectLecturerRepository.deleteById(assignmentId);
    }

    private SubjectResponse mapToSubjectResponse(Subject s) {
        int lecturerCount = subjectLecturerRepository.findBySubjectUuid(s.getUuid()).size();
        int questionCount = questionRepository.findBySubjectUuid(s.getUuid()).size();
        int docCount = documentRepository.findBySubjectUuidOrderByCreatedAtDesc(s.getUuid()).size();

        return SubjectResponse.builder()
                .uuid(s.getUuid())
                .code(s.getCode())
                .name(s.getName())
                .description(s.getDescription())
                .credits(s.getCredits())
                .lecturerCount(lecturerCount)
                .questionCount(questionCount)
                .documentCount(docCount)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private SubjectAssignmentResponse mapToAssignmentResponse(SubjectLecturer sl) {
        return SubjectAssignmentResponse.builder()
                .id(sl.getUuid())
                .subjectId(sl.getSubject().getUuid())
                .subjectCode(sl.getSubject().getCode())
                .subjectName(sl.getSubject().getName())
                .lecturerId(sl.getLecturer().getUuid())
                .lecturerName(sl.getLecturer().getFullName())
                .lecturerEmail(sl.getLecturer().getEmail())
                .canApproveRAG(sl.getCanApproveRag())
                .canEditRubric(sl.getCanEditRubric())
                .assignedAt(sl.getAssignedAt())
                .build();
    }
}

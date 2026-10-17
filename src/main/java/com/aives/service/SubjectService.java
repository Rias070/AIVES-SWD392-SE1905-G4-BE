package com.aives.service;

import com.aives.dto.request.AssignmentPermissionRequest;
import com.aives.dto.request.SubjectAssignRequest;
import com.aives.dto.request.SubjectLanguageConfigDto;
import com.aives.dto.request.SubjectRequest;
import com.aives.dto.response.SubjectAssignmentResponse;
import com.aives.dto.response.SubjectResponse;
import com.aives.entity.Subject;
import com.aives.entity.SubjectLecturer;
import com.aives.entity.User;
import com.aives.enums.ErrorCode;
import com.aives.exception.AppException;
import com.aives.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.aives.application.port.in.SubjectUseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SubjectService implements SubjectUseCase {

    private final SubjectRepository subjectRepository;
    private final SubjectLecturerRepository subjectLecturerRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final DocumentRepository documentRepository;

    public SubjectService(SubjectRepository subjectRepository, SubjectLecturerRepository subjectLecturerRepository,
                          UserRepository userRepository, QuestionRepository questionRepository,
                          DocumentRepository documentRepository) {
        this.subjectRepository = subjectRepository;
        this.subjectLecturerRepository = subjectLecturerRepository;
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.documentRepository = documentRepository;
    }

    public List<SubjectResponse> getAllSubjects() {
        return subjectRepository.findAll().stream()
                .filter(s -> s.getDeletedAt() == null && !"INACTIVE".equalsIgnoreCase(s.getStatus()))
                .map(this::mapToSubjectResponse)
                .collect(Collectors.toList());
    }

    public SubjectResponse getSubjectById(UUID id) {
        Subject subject = subjectRepository.findById(id)
                .filter(s -> s.getDeletedAt() == null && !"INACTIVE".equalsIgnoreCase(s.getStatus()))
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
                .language(request.getLanguage() != null && !request.getLanguage().isBlank() ? request.getLanguage().trim() : "vi-VN")
                .technicalTerms(request.getTechnicalTerms())
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
        if (request.getLanguage() != null && !request.getLanguage().isBlank()) {
            subject.setLanguage(request.getLanguage().trim());
        }
        if (request.getTechnicalTerms() != null) {
            subject.setTechnicalTerms(request.getTechnicalTerms());
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
        subject.setDeletedAt(java.time.LocalDateTime.now());
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

    public List<SubjectAssignmentResponse> getSubjectLecturers(UUID subjectId) {
        return subjectLecturerRepository.findBySubjectUuid(subjectId).stream()
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

    @Transactional
    public void removeLecturerFromSubject(UUID subjectId, UUID lecturerId) {
        List<SubjectLecturer> assignments = subjectLecturerRepository.findBySubjectUuid(subjectId);
        SubjectLecturer match = assignments.stream()
                .filter(sl -> sl.getLecturer().getUuid().equals(lecturerId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_KEY, "Giảng viên chưa được phân công môn học này."));
        subjectLecturerRepository.delete(match);
    }

    // ==========================================
    // EXAMINATION LANGUAGE CONFIGURATION (STT/TTS)
    // Dual authorization: ADMIN can override, LECTURER must be assigned
    // ==========================================
    @Transactional
    public SubjectResponse updateLanguageConfig(UUID subjectId, SubjectLanguageConfigDto request) {
        log.info("Updating examination language config for subject: {}", subjectId);
        Subject subject = subjectRepository.findById(subjectId)
                .filter(s -> s.getDeletedAt() == null && !"INACTIVE".equalsIgnoreCase(s.getStatus()))
                .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));

        verifyLanguageConfigPermission(subject.getUuid());

        if (request.getLanguage() != null && !request.getLanguage().isBlank()) {
            String lang = request.getLanguage().trim();
            if ("en".equalsIgnoreCase(lang)) {
                lang = "en-US";
            } else if ("vi".equalsIgnoreCase(lang)) {
                lang = "vi-VN";
            }
            subject.setLanguage(lang);
        }

        if (request.getTechnicalTerms() != null) {
            subject.setTechnicalTerms(request.getTechnicalTerms().trim());
        }

        subject = subjectRepository.save(subject);
        return mapToSubjectResponse(subject);
    }

    public SubjectResponse getSubjectByCode(String code) {
        Subject subject = subjectRepository.findByCode(code.trim().toUpperCase())
                .filter(s -> s.getDeletedAt() == null && !"INACTIVE".equalsIgnoreCase(s.getStatus()))
                .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));
        return mapToSubjectResponse(subject);
    }

    private void verifyLanguageConfigPermission(UUID subjectUuid) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Vui lòng đăng nhập để thực hiện thao tác");
        }

        // Check if user is ADMIN
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) || "ADMIN".equalsIgnoreCase(a.getAuthority()));
        if (isAdmin) {
            log.info("Admin authorized to manage language config for subject {}", subjectUuid);
            return;
        }

        // Lecturer check: must be assigned to this subject
        String principalName = auth.getName();
        UUID userUuid;
        try {
            userUuid = UUID.fromString(principalName);
        } catch (IllegalArgumentException e) {
            User user = userRepository.findByEmail(principalName)
                    .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Không tìm thấy thông tin tài khoản"));
            userUuid = user.getUuid();
        }

        boolean isAssigned = subjectLecturerRepository.existsBySubjectUuidAndLecturerUuid(subjectUuid, userUuid);
        if (!isAssigned) {
            log.warn("Lecturer {} is not assigned to subject {}", userUuid, subjectUuid);
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Bạn không có quyền cấu hình môn học này. Chỉ giảng viên được phân công hoặc Quản trị viên mới có quyền thao tác.");
        }
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
                .language(s.getLanguage() != null && !s.getLanguage().isBlank() ? s.getLanguage() : "vi-VN")
                .technicalTerms(s.getTechnicalTerms())
                .lecturerCount(lecturerCount)
                .questionCount(questionCount)
                .documentCount(docCount)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .deletedAt(s.getDeletedAt())
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

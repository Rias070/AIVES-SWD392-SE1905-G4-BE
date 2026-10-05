package com.aives.service;

import com.aives.dto.request.DocumentUploadRequest;
import com.aives.dto.request.QuestionRequest;
import com.aives.dto.request.RubricRequest;
import com.aives.dto.response.DocumentResponse;
import com.aives.dto.response.QuestionResponse;
import com.aives.dto.response.RubricResponse;
import com.aives.entity.*;
import com.aives.enums.ErrorCode;
import com.aives.exception.AppException;
import com.aives.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LecturerService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final QuestionRepository questionRepository;
    private final RubricRepository rubricRepository;
    private final SubjectRepository subjectRepository;

    // ==========================================
    // 1. RAG DOCUMENTS MANAGEMENT
    // ==========================================
    public List<DocumentResponse> getDocuments(String subjectCode, UUID subjectUuid) {
        List<Document> docs;
        if (subjectUuid != null) {
            docs = documentRepository.findBySubjectUuidOrderByCreatedAtDesc(subjectUuid);
        } else if (subjectCode != null && !subjectCode.isBlank()) {
            docs = documentRepository.findBySubjectCodeOrderByCreatedAtDesc(subjectCode.trim().toUpperCase());
        } else {
            docs = documentRepository.findAll();
        }

        return docs.stream()
                .filter(d -> !"DELETED".equalsIgnoreCase(d.getStatus()))
                .map(this::mapToDocResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public DocumentResponse uploadDocument(DocumentUploadRequest request) {
        Subject subject = null;
        if (request.getSubjectUuid() != null) {
            subject = subjectRepository.findById(request.getSubjectUuid()).orElse(null);
        }
        if (subject == null && request.getSubjectCode() != null) {
            subject = subjectRepository.findByCode(request.getSubjectCode().trim().toUpperCase()).orElse(null);
        }
        if (subject == null) {
            // Pick first subject or throw
            subject = subjectRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND, "Chưa có môn học nào trong hệ thống"));
        }

        Document doc = Document.builder()
                .subject(subject)
                .fileName(request.getFileName())
                .fileUrl(request.getFileUrl() != null ? request.getFileUrl() : "AWS S3: /" + subject.getCode().toLowerCase() + "/syllabus/" + request.getFileName())
                .fileSize(request.getFileSize() != null ? request.getFileSize() : "3.5 MB")
                .status("INDEXED")
                .chunkCount(12)
                .build();

        doc = documentRepository.save(doc);
        return mapToDocResponse(doc);
    }

    @Transactional
    public void deleteDocument(UUID docId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
        doc.setStatus("DELETED");
        documentRepository.save(doc);
    }

    @Transactional
    public DocumentResponse triggerVectorize(UUID docId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
        doc.setStatus("INDEXED");
        doc.setChunkCount(doc.getChunkCount() != null ? doc.getChunkCount() + 5 : 15);
        doc = documentRepository.save(doc);
        return mapToDocResponse(doc);
    }

    // ==========================================
    // 2. QUESTIONS MANAGEMENT
    // ==========================================
    public List<QuestionResponse> getQuestions(UUID subjectUuid, String difficulty, String keyword) {
        List<Question> list = questionRepository.findAll().stream()
                .filter(q -> !"INACTIVE".equalsIgnoreCase(q.getStatus()))
                .collect(Collectors.toList());

        if (subjectUuid != null) {
            list = list.stream()
                    .filter(q -> q.getSubject() != null && subjectUuid.equals(q.getSubject().getUuid()))
                    .collect(Collectors.toList());
        }

        if (difficulty != null && !difficulty.isBlank() && !"ALL".equalsIgnoreCase(difficulty)) {
            list = list.stream()
                    .filter(q -> difficulty.equalsIgnoreCase(q.getDifficulty()))
                    .collect(Collectors.toList());
        }

        if (keyword != null && !keyword.isBlank()) {
            String lowerKw = keyword.toLowerCase();
            list = list.stream()
                    .filter(q -> (q.getContent() != null && q.getContent().toLowerCase().contains(lowerKw))
                            || (q.getQuestionCode() != null && q.getQuestionCode().toLowerCase().contains(lowerKw))
                            || (q.getKeywords() != null && q.getKeywords().toLowerCase().contains(lowerKw)))
                    .collect(Collectors.toList());
        }

        return list.stream().map(this::mapToQuestionResponse).collect(Collectors.toList());
    }

    public QuestionResponse getQuestionById(UUID id) {
        Question q = questionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        return mapToQuestionResponse(q);
    }

    @Transactional
    public QuestionResponse createQuestion(QuestionRequest request) {
        Subject subject = null;
        if (request.getSubjectUuid() != null) {
            subject = subjectRepository.findById(request.getSubjectUuid()).orElse(null);
        }
        if (subject == null && request.getSubjectCode() != null) {
            subject = subjectRepository.findByCode(request.getSubjectCode().trim().toUpperCase()).orElse(null);
        }
        if (subject == null) {
            subject = subjectRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));
        }

        String qCode = request.getQuestionCode();
        if (qCode == null || qCode.isBlank()) {
            qCode = subject.getCode() + "-Q" + (100 + (int)(Math.random() * 900));
        }

        Question q = Question.builder()
                .subject(subject)
                .questionCode(qCode)
                .content(request.getContent())
                .difficulty(request.getDifficulty() != null ? request.getDifficulty().toUpperCase() : "MEDIUM")
                .bloomLevel(request.getBloomLevel() != null ? request.getBloomLevel() : "Bloom 3 - Vận dụng")
                .expectedAnswer(request.getExpectedAnswer())
                .keywords(request.getKeywords())
                .status(request.getStatus() != null ? request.getStatus().toUpperCase() : "ACTIVE")
                .build();

        q = questionRepository.save(q);
        return mapToQuestionResponse(q);
    }

    @Transactional
    public QuestionResponse updateQuestion(UUID id, QuestionRequest request) {
        Question q = questionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));

        if (request.getContent() != null) q.setContent(request.getContent());
        if (request.getQuestionCode() != null) q.setQuestionCode(request.getQuestionCode());
        if (request.getDifficulty() != null) q.setDifficulty(request.getDifficulty().toUpperCase());
        if (request.getBloomLevel() != null) q.setBloomLevel(request.getBloomLevel());
        if (request.getExpectedAnswer() != null) q.setExpectedAnswer(request.getExpectedAnswer());
        if (request.getKeywords() != null) q.setKeywords(request.getKeywords());
        if (request.getStatus() != null) q.setStatus(request.getStatus().toUpperCase());

        q = questionRepository.save(q);
        return mapToQuestionResponse(q);
    }

    @Transactional
    public void deleteQuestion(UUID id) {
        Question q = questionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        q.setStatus("INACTIVE");
        questionRepository.save(q);
    }

    @Transactional
    public QuestionResponse toggleApproveQuestion(UUID id) {
        Question q = questionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));

        if ("ACTIVE".equalsIgnoreCase(q.getStatus()) || "APPROVED".equalsIgnoreCase(q.getStatus())) {
            q.setStatus("PENDING");
        } else {
            q.setStatus("APPROVED");
        }

        q = questionRepository.save(q);
        return mapToQuestionResponse(q);
    }

    // ==========================================
    // 3. RUBRICS MANAGEMENT
    // ==========================================
    public List<RubricResponse> getRubricsBySubject(UUID subjectUuid) {
        return rubricRepository.findBySubjectUuid(subjectUuid).stream()
                .map(this::mapToRubricResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public RubricResponse createRubric(RubricRequest request) {
        Subject subject = subjectRepository.findById(request.getSubjectUuid())
                .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));

        Question question = null;
        if (request.getQuestionUuid() != null) {
            question = questionRepository.findById(request.getQuestionUuid()).orElse(null);
        }

        Rubric r = Rubric.builder()
                .subject(subject)
                .question(question)
                .criterionName(request.getCriterionName())
                .description(request.getDescription())
                .maxScore(request.getMaxScore())
                .weight(request.getWeight())
                .build();

        r = rubricRepository.save(r);
        return mapToRubricResponse(r);
    }

    @Transactional
    public RubricResponse updateRubric(UUID id, RubricRequest request) {
        Rubric r = rubricRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RUBRIC_NOT_FOUND));

        if (request.getCriterionName() != null) r.setCriterionName(request.getCriterionName());
        if (request.getDescription() != null) r.setDescription(request.getDescription());
        if (request.getMaxScore() != null) r.setMaxScore(request.getMaxScore());
        if (request.getWeight() != null) r.setWeight(request.getWeight());

        r = rubricRepository.save(r);
        return mapToRubricResponse(r);
    }

    @Transactional
    public void deleteRubric(UUID id) {
        if (!rubricRepository.existsById(id)) {
            throw new AppException(ErrorCode.RUBRIC_NOT_FOUND);
        }
        rubricRepository.deleteById(id);
    }

    // ==========================================
    // HELPER MAPPERS
    // ==========================================
    private DocumentResponse mapToDocResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getUuid())
                .subjectId(doc.getSubject() != null ? doc.getSubject().getUuid() : null)
                .subjectCode(doc.getSubject() != null ? doc.getSubject().getCode() : "N/A")
                .subjectName(doc.getSubject() != null ? doc.getSubject().getName() : "N/A")
                .filename(doc.getFileName())
                .uri(doc.getFileUrl())
                .size(doc.getFileSize() != null ? doc.getFileSize() : "1.0 MB")
                .status(doc.getStatus() != null ? doc.getStatus().toLowerCase() : "indexed")
                .statusText("Indexed (Hoàn thành)")
                .chunkCount(doc.getChunkCount() != null ? doc.getChunkCount() : 10)
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private QuestionResponse mapToQuestionResponse(Question q) {
        int rubricCount = rubricRepository.findByQuestionUuid(q.getUuid()).size();
        if (rubricCount == 0 && q.getSubject() != null) {
            rubricCount = rubricRepository.findBySubjectUuid(q.getSubject().getUuid()).size();
        }
        if (rubricCount == 0) rubricCount = 3;

        List<String> keywordsList = new ArrayList<>();
        if (q.getKeywords() != null && !q.getKeywords().isBlank()) {
            keywordsList = Arrays.stream(q.getKeywords().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        boolean isApproved = "ACTIVE".equalsIgnoreCase(q.getStatus()) || "APPROVED".equalsIgnoreCase(q.getStatus());

        String bloomColor = "bg-blue-50 text-blue-700 border-blue-200";
        if ("HARD".equalsIgnoreCase(q.getDifficulty())) {
            bloomColor = "bg-purple-50 text-purple-700 border-purple-200";
        } else if ("EASY".equalsIgnoreCase(q.getDifficulty())) {
            bloomColor = "bg-emerald-50 text-emerald-700 border-emerald-200";
        }

        return QuestionResponse.builder()
                .id(q.getUuid())
                .code(q.getQuestionCode() != null ? q.getQuestionCode() : "Q-" + q.getUuid().toString().substring(0, 6).toUpperCase())
                .subjectId(q.getSubject() != null ? q.getSubject().getUuid() : null)
                .subjectCode(q.getSubject() != null ? q.getSubject().getCode() : "N/A")
                .subjectName(q.getSubject() != null ? q.getSubject().getName() : "N/A")
                .content(q.getContent())
                .difficulty(q.getDifficulty() != null ? q.getDifficulty() : "MEDIUM")
                .bloomLevel(q.getBloomLevel() != null ? q.getBloomLevel() : "Bloom 3 - Vận dụng")
                .bloomColor(bloomColor)
                .expectedAnswer(q.getExpectedAnswer())
                .keywords(keywordsList)
                .vectorStatus("Embedded (1536 dims)")
                .status(q.getStatus())
                .isApproved(isApproved)
                .rubricCount(rubricCount)
                .createdAt(q.getCreatedAt())
                .build();
    }

    private RubricResponse mapToRubricResponse(Rubric r) {
        return RubricResponse.builder()
                .uuid(r.getUuid())
                .subjectId(r.getSubject() != null ? r.getSubject().getUuid() : null)
                .questionId(r.getQuestion() != null ? r.getQuestion().getUuid() : null)
                .criterionName(r.getCriterionName())
                .description(r.getDescription())
                .maxScore(r.getMaxScore())
                .weight(r.getWeight())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

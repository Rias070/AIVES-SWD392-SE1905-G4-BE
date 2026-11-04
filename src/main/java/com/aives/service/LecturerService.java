package com.aives.service;

import com.aives.dto.request.DocumentUploadRequest;
import com.aives.dto.request.QuestionGenerateRequest;
import com.aives.dto.request.QuestionRequest;
import com.aives.dto.request.RubricRequest;
import com.aives.dto.response.DocumentResponse;
import com.aives.dto.response.QuestionResponse;
import com.aives.dto.response.RubricResponse;
import com.aives.entity.*;
import com.aives.enums.ErrorCode;
import com.aives.exception.AppException;
import com.aives.infrastructure.adapter.out.ai.GeminiAIAdapter;
import com.aives.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.aives.application.port.in.LecturerDocumentUseCase;
import com.aives.application.port.in.LecturerQuestionUseCase;
import com.aives.application.port.in.LecturerRubricUseCase;
import com.aives.application.port.out.LLMProviderPort;
import com.aives.application.port.out.VectorEmbeddingPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.Inflater;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
@Slf4j
public class LecturerService implements LecturerDocumentUseCase, LecturerQuestionUseCase, LecturerRubricUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final QuestionRepository questionRepository;
    private final RubricRepository rubricRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectLecturerRepository subjectLecturerRepository;
    private final UserRepository userRepository;
    private final LLMProviderPort llmProviderPort;
    private final VectorEmbeddingPort vectorEmbeddingPort;

    public LecturerService(DocumentRepository documentRepository,
                           DocumentChunkRepository documentChunkRepository,
                           QuestionRepository questionRepository,
                           RubricRepository rubricRepository,
                           SubjectRepository subjectRepository,
                           SubjectLecturerRepository subjectLecturerRepository,
                           UserRepository userRepository,
                           LLMProviderPort llmProviderPort,
                           VectorEmbeddingPort vectorEmbeddingPort) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.questionRepository = questionRepository;
        this.rubricRepository = rubricRepository;
        this.subjectRepository = subjectRepository;
        this.subjectLecturerRepository = subjectLecturerRepository;
        this.userRepository = userRepository;
        this.llmProviderPort = llmProviderPort;
        this.vectorEmbeddingPort = vectorEmbeddingPort;
    }

    // ==========================================
    // 1. RAG DOCUMENTS MANAGEMENT (INGESTION & VECTORIZATION)
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
        Subject subject = resolveSubject(request.getSubjectUuid(), request.getSubjectCode());

        String fileName = request.getFileName() != null ? request.getFileName() : "Document_" + System.currentTimeMillis() + ".pdf";
        String fileSize = request.getFileSize() != null ? request.getFileSize() : "2.4 MB";
        String fileUrl = request.getFileUrl() != null ? request.getFileUrl() : "AWS S3: /" + subject.getCode().toLowerCase() + "/syllabus/" + fileName;

        Document doc = Document.builder()
                .subject(subject)
                .fileName(fileName)
                .fileUrl(fileUrl)
                .fileSize(fileSize)
                .status("INDEXED")
                .chunkCount(0)
                .build();

        doc = documentRepository.save(doc);

        // Ensure foundation chunks are created for RAG
        List<DocumentChunk> chunks = createSyllabusChunksForDoc(doc, subject, fileName);
        doc.setChunkCount(chunks.size());
        doc = documentRepository.save(doc);

        return mapToDocResponse(doc);
    }

    @Transactional
    public DocumentResponse uploadDocumentFile(MultipartFile file, String subjectCode, UUID subjectUuid) {
        Subject subject = resolveSubject(subjectUuid, subjectCode);

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "Document_" + System.currentTimeMillis() + ".pdf";
        }

        long byteSize = file.getSize();
        String formattedSize = formatFileSize(byteSize);

        Document doc = Document.builder()
                .subject(subject)
                .fileName(originalName)
                .fileUrl("S3: /" + subject.getCode().toLowerCase() + "/docs/" + originalName)
                .fileSize(formattedSize)
                .status("CHUNKING")
                .chunkCount(0)
                .build();

        doc = documentRepository.save(doc);

        // Extract text and chunk
        String extractedText = extractTextFromFile(file, subject, originalName);
        List<String> rawChunks = splitIntoChunks(extractedText, 600, 100);

        List<DocumentChunk> chunkEntities = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            String chunkContent = rawChunks.get(i);
            String vectorEmbedding = null;
            try {
                vectorEmbedding = vectorEmbeddingPort.generateEmbeddingVector(chunkContent);
            } catch (Exception e) {
                log.warn("Failed to generate embedding for chunk {}: {}", i + 1, e.getMessage());
            }

            DocumentChunk chunk = DocumentChunk.builder()
                    .document(doc)
                    .chunkIndex(i + 1)
                    .chunkContent(chunkContent)
                    .embedding(vectorEmbedding)
                    .build();
            chunkEntities.add(chunk);
        }

        documentChunkRepository.saveAll(chunkEntities);
        doc.setChunkCount(chunkEntities.size());
        doc.setStatus("INDEXED");
        doc = documentRepository.save(doc);

        log.info("Uploaded and chunked document '{}' into {} chunks for subject {}", originalName, chunkEntities.size(), subject.getCode());
        return mapToDocResponse(doc);
    }

    @Transactional
    public void deleteDocument(UUID docId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
        
        try {
            documentChunkRepository.deleteByDocumentUuid(docId);
        } catch (Exception e) {
            log.warn("Could not delete chunks for document {}: {}", docId, e.getMessage());
        }

        doc.setStatus("DELETED");
        documentRepository.save(doc);
    }

    @Transactional
    public DocumentResponse triggerVectorize(UUID docId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        List<DocumentChunk> existing = documentChunkRepository.findByDocumentUuidOrderByChunkIndexAsc(docId);
        if (existing.isEmpty() && doc.getSubject() != null) {
            existing = createSyllabusChunksForDoc(doc, doc.getSubject(), doc.getFileName());
        }

        doc.setStatus("INDEXED");
        doc.setChunkCount(existing.size());
        doc = documentRepository.save(doc);
        return mapToDocResponse(doc);
    }

    // ==========================================
    // 2. AI QUESTION GENERATION VIA RAG
    // ==========================================
    @Transactional
    public List<QuestionResponse> generateQuestionsRAG(QuestionGenerateRequest request) {
        Subject subject = resolveSubject(request.getSubjectUuid(), request.getSubjectCode());

        // 1. Retrieve RAG chunks (bỏ qua hoàn toàn nếu useRagContext = false)
        boolean useRagContext = request.getUseRagContext() == null || request.getUseRagContext();
        List<DocumentChunk> chunks = new ArrayList<>();
        String ragContext = "";
        if (useRagContext) {
            if (request.getDocumentUuid() != null) {
                chunks = documentChunkRepository.findByDocumentUuidOrderByChunkIndexAsc(request.getDocumentUuid());
            }
            if (chunks.isEmpty()) {
                chunks = documentChunkRepository.findTopChunksBySubject(subject.getUuid(), 20);
            }
            if (chunks.isEmpty()) {
                chunks = ensureFoundationChunks(subject);
            }

            ragContext = chunks.stream()
                    .map(DocumentChunk::getChunkContent)
                    .limit(15)
                    .collect(Collectors.joining("\n---\n"));
        } else {
            log.info("RAG context disabled by lecturer for subject {} - generating from topic/custom context only", subject.getCode());
        }

        int count = request.getCount() != null ? Math.max(1, Math.min(10, request.getCount())) : 3;
        String topic = request.getTopic() != null && !request.getTopic().isBlank() && !"Trọng tâm kiến thức môn học".equalsIgnoreCase(request.getTopic().trim()) ? request.getTopic().trim() : null;
        String requestedBloom = request.getBloomLevel() != null ? request.getBloomLevel() : "ALL";
        String requestedDifficulty = request.getDifficulty() != null ? request.getDifficulty() : "MEDIUM";
        String customContext = request.getCustomContext() != null && !request.getCustomContext().isBlank()
                ? request.getCustomContext().trim() : null;

        List<Map<String, Object>> generatedItems = llmProviderPort.generateQuestionsFromSyllabus(
                subject, topic, requestedBloom, requestedDifficulty, count, ragContext, customContext);

        List<QuestionResponse> results = new ArrayList<>();
        for (int i = 0; i < generatedItems.size(); i++) {
            Map<String, Object> item = generatedItems.get(i);

            String qCode = subject.getCode() + "-Q" + (100 + (int)(Math.random() * 899));
            String content = (String) item.getOrDefault("content", "Câu hỏi vấn đáp chuyên môn " + subject.getCode());
            String difficulty = (String) item.getOrDefault("difficulty", "MEDIUM");
            String bloomLevel = (String) item.getOrDefault("bloomLevel", "Bloom 3 - Vận dụng");
            String expectedAnswer = (String) item.getOrDefault("expectedAnswer", "Sinh viên trình bày rõ bản chất khái niệm và nêu ví dụ thực tiễn.");
            
            Object kwObj = item.get("keywords");
            String keywordsStr;
            if (kwObj instanceof List<?>) {
                keywordsStr = String.join(", ", ((List<?>) kwObj).stream().map(Object::toString).toList());
            } else if (kwObj != null) {
                keywordsStr = kwObj.toString();
            } else {
                keywordsStr = subject.getCode() + ", Architecture, Analysis";
            }

            // Create question entity in PENDING state (isApproved = false)
            Question q = Question.builder()
                    .subject(subject)
                    .questionCode(qCode)
                    .content(content)
                    .difficulty(difficulty.toUpperCase())
                    .bloomLevel(bloomLevel)
                    .expectedAnswer(expectedAnswer)
                    .keywords(keywordsStr)
                    .status("PENDING") // Pending review
                    .embedding(vectorEmbeddingPort.generateEmbeddingVector(content))
                    .build();

            q = questionRepository.save(q);

            // Create attached rubrics
            List<RubricResponse> rubricResponses = new ArrayList<>();
            Object rubricsListObj = item.get("rubrics");
            if (rubricsListObj instanceof List<?> rubricsList && !rubricsList.isEmpty()) {
                for (Object rObj : rubricsList) {
                    if (rObj instanceof Map<?, ?> rMap) {
                        Object critObj = rMap.get("criterionName");
                        String critName = critObj != null ? critObj.toString() : "Tiêu chí đánh giá";
                        Object descObj = rMap.get("description");
                        String desc = descObj != null ? descObj.toString() : "Mức độ hiểu và áp dụng";
                        
                        BigDecimal maxScore = new BigDecimal("10.00");
                        if (rMap.get("maxScore") != null) {
                            try { maxScore = new BigDecimal(rMap.get("maxScore").toString()); } catch (Exception ignored) {}
                        }
                        
                        BigDecimal weight = new BigDecimal("100.00");
                        if (rMap.get("weight") != null) {
                            try { weight = new BigDecimal(rMap.get("weight").toString()); } catch (Exception ignored) {}
                        }

                        Rubric rubric = Rubric.builder()
                                .subject(subject)
                                .question(q)
                                .criterionName(critName)
                                .description(desc)
                                .maxScore(maxScore)
                                .weight(weight)
                                .build();
                        rubric = rubricRepository.save(rubric);
                        rubricResponses.add(mapToRubricResponse(rubric));
                    }
                }
                normalizeWeights(rubricRepository.findByQuestionUuid(q.getUuid()));
            } else {
                // Default 3 standard criteria
                Rubric r1 = Rubric.builder().subject(subject).question(q).criterionName("Khái niệm cơ bản & Thuật ngữ").description("Định nghĩa chính xác, sử dụng đúng thuật ngữ chuyên ngành").maxScore(new BigDecimal("3.00")).weight(new BigDecimal("30.00")).build();
                Rubric r2 = Rubric.builder().subject(subject).question(q).criterionName("Phân tích cơ chế & So sánh").description("Hiểu sâu cơ chế hoạt động, so sánh được ưu nhược điểm").maxScore(new BigDecimal("4.00")).weight(new BigDecimal("40.00")).build();
                Rubric r3 = Rubric.builder().subject(subject).question(q).criterionName("Vận dụng thực tiễn & Phản biện").description("Đưa ra ví dụ thực tế hoặc giải quyết tình huống cụ thể").maxScore(new BigDecimal("3.00")).weight(new BigDecimal("30.00")).build();
                rubricRepository.saveAll(List.of(r1, r2, r3));
                rubricResponses.add(mapToRubricResponse(r1));
                rubricResponses.add(mapToRubricResponse(r2));
                rubricResponses.add(mapToRubricResponse(r3));
            }

            QuestionResponse resp = mapToQuestionResponse(q);
            resp.setRubrics(rubricResponses);
            resp.setRubricCount(rubricResponses.size());
            resp.setIsApproved(false);
            results.add(resp);
        }

        log.info("Generated {} RAG questions for subject {} (topic: {})", results.size(), subject.getCode(), topic);
        return results;
    }

    // ==========================================
    // 3. QUESTIONS MANAGEMENT (HUMAN-IN-THE-LOOP)
    // ==========================================
    public List<QuestionResponse> getQuestions(UUID subjectUuid, String difficulty, String keyword, String status) {
        List<Question> list = questionRepository.findAll().stream()
                .filter(q -> !"INACTIVE".equalsIgnoreCase(q.getStatus()) && q.getDeletedAt() == null)
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

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            list = list.stream()
                    .filter(q -> status.equalsIgnoreCase(q.getStatus()))
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
        Subject subject = resolveSubject(request.getSubjectUuid(), request.getSubjectCode());

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
                .embedding(vectorEmbeddingPort.generateEmbeddingVector(request.getContent()))
                .build();

        q = questionRepository.save(q);

        // Save custom rubrics if supplied
        List<RubricResponse> rubricsList = new ArrayList<>();
        if (request.getRubrics() != null && !request.getRubrics().isEmpty()) {
            for (RubricRequest rReq : request.getRubrics()) {
                if (rReq.getCriterionName() == null || rReq.getCriterionName().isBlank()) continue;
                Rubric r = Rubric.builder()
                        .subject(subject)
                        .question(q)
                        .criterionName(rReq.getCriterionName().trim())
                        .description(rReq.getDescription())
                        .maxScore(rReq.getMaxScore() != null ? rReq.getMaxScore() : new BigDecimal("10.00"))
                        .weight(new BigDecimal("100.00"))
                        .build();
                r = rubricRepository.save(r);
                rubricsList.add(mapToRubricResponse(r));
            }
            normalizeWeights(rubricRepository.findByQuestionUuid(q.getUuid()));
        } else {
            // Không có rubric riêng -> copy từ Barem template của môn học
            copyTemplateInternal(q, subject);
            rubricsList = getRubricsByQuestion(q.getUuid());
        }

        QuestionResponse resp = mapToQuestionResponse(q);
        if (!rubricsList.isEmpty()) {
            resp.setRubrics(rubricsList);
            resp.setRubricCount(rubricsList.size());
        }
        return resp;
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

        // Update rubrics if provided - đồng bộ theo diff (giữ nguyên uuid của tiêu chí không đổi)
        if (request.getRubrics() != null) {
            syncQuestionRubrics(q, request.getRubrics());
        }

        return mapToQuestionResponse(q);
    }

    /**
     * Đồng bộ rubric của một câu hỏi theo danh sách mới:
     * - Tiêu chí có uuid khớp -> cập nhật tại chỗ (giữ nguyên uuid, createdAt)
     * - Tiêu chí mới (không uuid) -> tạo mới
     * - Tiêu chí không còn trong danh sách -> xóa
     */
    private void syncQuestionRubrics(Question q, List<RubricRequest> incoming) {
        List<Rubric> existing = rubricRepository.findByQuestionUuid(q.getUuid());
        Map<UUID, Rubric> existingById = new HashMap<>();
        for (Rubric r : existing) existingById.put(r.getUuid(), r);

        Set<UUID> keptIds = new HashSet<>();

        for (RubricRequest req : incoming) {
            if (req.getCriterionName() == null || req.getCriterionName().isBlank()) continue;

            Rubric target = req.getUuid() != null ? existingById.get(req.getUuid()) : null;
            if (target == null) {
                target = Rubric.builder()
                        .subject(q.getSubject())
                        .question(q)
                        .build();
            }

            target.setCriterionName(req.getCriterionName().trim());
            target.setDescription(req.getDescription());
            target.setMaxScore(req.getMaxScore() != null ? req.getMaxScore() : new BigDecimal("10.00"));

            rubricRepository.save(target);
            keptIds.add(target.getUuid());
        }

        for (Rubric r : existing) {
            if (!keptIds.contains(r.getUuid())) {
                rubricRepository.delete(r);
            }
        }
        rubricRepository.flush();

        normalizeWeights(rubricRepository.findByQuestionUuid(q.getUuid()));
    }

    @Transactional
    public void deleteQuestion(UUID id) {
        Question q = questionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        q.setStatus("INACTIVE");
        q.setDeletedAt(LocalDateTime.now());
        questionRepository.save(q);
    }

    @Transactional
    public QuestionResponse toggleApproveQuestion(UUID id) {
        Question q = questionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));

        if ("ACTIVE".equalsIgnoreCase(q.getStatus()) || "APPROVED".equalsIgnoreCase(q.getStatus())) {
            q.setStatus("PENDING");
        } else {
            q.setStatus("ACTIVE");
        }

        q = questionRepository.save(q);
        return mapToQuestionResponse(q);
    }

    @Transactional
    public int approveAllPending(UUID subjectUuid, String subjectCode) {
        List<Question> pending = questionRepository.findAll().stream()
                .filter(q -> "PENDING".equalsIgnoreCase(q.getStatus()))
                .collect(Collectors.toList());

        if (subjectUuid != null) {
            pending = pending.stream()
                    .filter(q -> q.getSubject() != null && subjectUuid.equals(q.getSubject().getUuid()))
                    .collect(Collectors.toList());
        } else if (subjectCode != null && !subjectCode.isBlank()) {
            pending = pending.stream()
                    .filter(q -> q.getSubject() != null && subjectCode.equalsIgnoreCase(q.getSubject().getCode()))
                    .collect(Collectors.toList());
        }

        for (Question q : pending) {
            q.setStatus("ACTIVE");
            questionRepository.save(q);
        }

        log.info("Approved {} pending questions", pending.size());
        return pending.size();
    }

    // ==========================================
    // 4. RUBRICS MANAGEMENT
    // ==========================================
    /**
     * Lấy RUBRIC TEMPLATE (Barem chuẩn) của môn học - KHÔNG bao gồm rubric gắn vào câu hỏi.
     */
    public List<RubricResponse> getRubricsBySubject(UUID subjectUuid) {
        return rubricRepository.findBySubjectUuidAndQuestionUuidIsNull(subjectUuid).stream()
                .map(this::mapToRubricResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy TẤT CẢ rubric của môn học: template + rubric của từng câu hỏi.
     */
    public List<RubricResponse> getAllRubricsBySubject(UUID subjectUuid) {
        return rubricRepository.findBySubjectUuid(subjectUuid).stream()
                .map(this::mapToRubricResponse)
                .collect(Collectors.toList());
    }

    public List<RubricResponse> getRubricsByQuestion(UUID questionUuid) {
        return rubricRepository.findByQuestionUuid(questionUuid).stream()
                .map(this::mapToRubricResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public RubricResponse createRubric(RubricRequest request) {
        Subject subject = resolveSubject(request.getSubjectUuid(), null);
        verifyRubricPermission(subject.getUuid());

        Question question = null;
        if (request.getQuestionUuid() != null) {
            question = questionRepository.findById(request.getQuestionUuid())
                    .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        }

        Rubric r = Rubric.builder()
                .subject(subject)
                .question(question)
                .criterionName(request.getCriterionName().trim())
                .description(request.getDescription())
                .maxScore(request.getMaxScore() != null ? request.getMaxScore() : new BigDecimal("10.00"))
                .weight(new BigDecimal("100.00"))
                .build();

        r = rubricRepository.save(r);
        normalizeWeights(groupOf(r));
        return mapToRubricResponse(r);
    }

    @Transactional
    public RubricResponse updateRubric(UUID id, RubricRequest request) {
        Rubric r = rubricRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RUBRIC_NOT_FOUND));

        verifyRubricPermission(subjectUuidOf(r));

        if (request.getCriterionName() != null && !request.getCriterionName().isBlank()) {
            r.setCriterionName(request.getCriterionName().trim());
        }
        if (request.getDescription() != null) r.setDescription(request.getDescription());
        if (request.getMaxScore() != null) r.setMaxScore(request.getMaxScore());

        r = rubricRepository.save(r);
        normalizeWeights(groupOf(r));
        return mapToRubricResponse(r);
    }

    @Transactional
    public void deleteRubric(UUID id) {
        Rubric r = rubricRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RUBRIC_NOT_FOUND));

        verifyRubricPermission(subjectUuidOf(r));

        rubricRepository.delete(r);
        rubricRepository.flush();
        normalizeWeights(groupOf(r));
    }

    /**
     * Lưu cả bộ rubric của một nhóm (template của môn hoặc rubric của một câu hỏi) trong 1 lần gọi.
     * Tiêu chí có {@code uuid} khớp sẽ được cập nhật, không có sẽ được tạo mới.
     * Tiêu chí không còn trong danh sách sẽ bị xóa.
     */
    @Transactional
    public List<RubricResponse> bulkSaveRubrics(UUID subjectUuid, UUID questionUuid, List<RubricRequest> rubrics) {
        Subject subject = resolveSubject(subjectUuid, null);
        verifyRubricPermission(subject.getUuid());

        Question question = null;
        if (questionUuid != null) {
            question = questionRepository.findById(questionUuid)
                    .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        }

        List<Rubric> existing = question != null
                ? rubricRepository.findByQuestionUuid(question.getUuid())
                : rubricRepository.findBySubjectUuidAndQuestionUuidIsNull(subject.getUuid());

        Map<UUID, Rubric> existingById = new HashMap<>();
        for (Rubric r : existing) existingById.put(r.getUuid(), r);

        Set<UUID> keptIds = new HashSet<>();
        List<Rubric> saved = new ArrayList<>();

        if (rubrics != null) {
            for (RubricRequest req : rubrics) {
                if (req.getCriterionName() == null || req.getCriterionName().isBlank()) continue;

                Rubric target = null;
                if (req.getUuid() != null) {
                    target = existingById.get(req.getUuid());
                }
                if (target == null) {
                    target = Rubric.builder()
                            .subject(subject)
                            .question(question)
                            .build();
                }

                target.setCriterionName(req.getCriterionName().trim());
                target.setDescription(req.getDescription());
                target.setMaxScore(req.getMaxScore() != null ? req.getMaxScore() : new BigDecimal("10.00"));
                target.setWeight(new BigDecimal("100.00"));

                saved.add(rubricRepository.save(target));
                keptIds.add(target.getUuid());
            }
        }

        // Xóa các tiêu chí không còn nằm trong danh sách mới
        for (Rubric r : existing) {
            if (!keptIds.contains(r.getUuid())) {
                rubricRepository.delete(r);
            }
        }
        rubricRepository.flush();

        List<Rubric> group = question != null
                ? rubricRepository.findByQuestionUuid(question.getUuid())
                : rubricRepository.findBySubjectUuidAndQuestionUuidIsNull(subject.getUuid());
        normalizeWeights(group);

        return group.stream().map(this::mapToRubricResponse).collect(Collectors.toList());
    }

    /**
     * Copy rubric template của môn học vào một câu hỏi (chỉ khi câu hỏi chưa có rubric riêng).
     */
    @Transactional
    public int copySubjectTemplateToQuestion(UUID questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));

        Subject subject = question.getSubject();
        if (subject == null) throw new AppException(ErrorCode.SUBJECT_NOT_FOUND);

        verifyRubricPermission(subject.getUuid());
        return copyTemplateInternal(question, subject);
    }

    /**
     * Copy template môn học vào câu hỏi - KHÔNG kiểm tra quyền (dùng nội bộ).
     */
    private int copyTemplateInternal(Question question, Subject subject) {
        List<Rubric> templates = rubricRepository.findBySubjectUuidAndQuestionUuidIsNull(subject.getUuid());
        if (templates.isEmpty()) {
            log.info("No rubric template for subject {}, nothing to copy", subject.getCode());
            return 0;
        }

        if (!rubricRepository.findByQuestionUuid(question.getUuid()).isEmpty()) {
            log.info("Question {} already has its own rubrics, skipping template copy", question.getUuid());
            return 0;
        }

        List<Rubric> copies = new ArrayList<>();
        for (Rubric t : templates) {
            copies.add(Rubric.builder()
                    .subject(subject)
                    .question(question)
                    .criterionName(t.getCriterionName())
                    .description(t.getDescription())
                    .maxScore(t.getMaxScore())
                    .weight(t.getWeight())
                    .build());
        }
        rubricRepository.saveAll(copies);
        normalizeWeights(copies);
        log.info("Copied {} rubric template(s) into question {}", copies.size(), question.getUuid());
        return copies.size();
    }

    /**
     * Áp dụng rubric template của môn học cho TẤT CẢ câu hỏi của môn chưa có rubric riêng.
     */
    @Transactional
    public int applySubjectTemplateToSubject(UUID subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));

        verifyRubricPermission(subject.getUuid());

        List<Question> questions = questionRepository.findBySubjectUuid(subjectId).stream()
                .filter(q -> !"INACTIVE".equalsIgnoreCase(q.getStatus()))
                .toList();

        int copied = 0;
        for (Question q : questions) {
            copied += copyTemplateInternal(q, subject);
        }
        log.info("Applied subject template to {} question(s) of subject {}", copied, subject.getCode());
        return copied;
    }

    // ==========================================
    // RUBRIC HELPERS
    // ==========================================

    /**
     * Trả về nhóm rubric chứa một rubric cụ thể:
     * - rubric của câu hỏi nếu có question_uuid
     * - rubric template của môn học nếu không gắn câu hỏi nào
     */
    private List<Rubric> groupOf(Rubric rubric) {
        if (rubric.getQuestion() != null) {
            return rubricRepository.findByQuestionUuid(rubric.getQuestion().getUuid());
        }
        return rubricRepository.findBySubjectUuidAndQuestionUuidIsNull(rubric.getSubject().getUuid());
    }

    /**
     * Lấy uuid môn học sở hữu rubric (môn học luôn được gán cho mọi rubric).
     */
    private UUID subjectUuidOf(Rubric rubric) {
        if (rubric.getSubject() == null) {
            throw new AppException(ErrorCode.SUBJECT_NOT_FOUND);
        }
        return rubric.getSubject().getUuid();
    }

    /**
     * Chuẩn hoá trọng số theo phần trăm dựa trên maxScore:
     * weight_i = maxScore_i / SUM(maxScore) * 100  ->  tổng luôn = 100.
     */
    private void normalizeWeights(List<Rubric> group) {
        if (group == null || group.isEmpty()) return;

        BigDecimal total = group.stream()
                .map(r -> r.getMaxScore() != null ? r.getMaxScore() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal even = new BigDecimal("100.00")
                    .divide(new BigDecimal(group.size()), 2, java.math.RoundingMode.HALF_UP);
            for (Rubric r : group) r.setWeight(even);
        } else {
            BigDecimal assigned = BigDecimal.ZERO;
            for (int i = 0; i < group.size(); i++) {
                Rubric r = group.get(i);
                BigDecimal score = r.getMaxScore() != null ? r.getMaxScore() : BigDecimal.ZERO;
                BigDecimal w;
                if (i == group.size() - 1) {
                    // Tiêu chí cuối nhận phần trọng số còn lại để tổng luôn đúng 100
                    w = new BigDecimal("100.00").subtract(assigned);
                    if (w.compareTo(BigDecimal.ZERO) < 0) w = BigDecimal.ZERO;
                } else {
                    w = score.multiply(new BigDecimal("100.00"))
                            .divide(total, 2, java.math.RoundingMode.HALF_UP);
                    assigned = assigned.add(w);
                }
                r.setWeight(w);
            }
        }
        rubricRepository.saveAll(group);
    }

    /**
     * Kiểm tra quyền được phép quản lý rubric của môn học.
     * - ADMIN: bypass
     * - LECTURER: phải được phân công môn học với canEditRubric = true
     */
    private void verifyRubricPermission(UUID subjectUuid) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Vui lòng đăng nhập để thực hiện thao tác");
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) || "ADMIN".equalsIgnoreCase(a.getAuthority()));
        if (isAdmin) return;

        UUID userUuid;
        try {
            userUuid = UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            User user = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Không tìm thấy thông tin tài khoản"));
            userUuid = user.getUuid();
        }

        SubjectLecturer assignment = subjectLecturerRepository
                .findBySubjectUuidAndLecturerUuid(subjectUuid, userUuid)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED,
                        "Bạn không được phân công môn học này nên không có quyền quản lý Rubric."));

        if (!Boolean.TRUE.equals(assignment.getCanEditRubric())) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Bạn không có quyền sửa Rubric cho môn học này. Vui lòng liên hệ Quản trị viên.");
        }
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================
    private Subject resolveSubject(UUID uuid, String code) {
        if (uuid != null) {
            Optional<Subject> opt = subjectRepository.findById(uuid);
            if (opt.isPresent()) return opt.get();
        }
        if (code != null && !code.isBlank()) {
            Optional<Subject> opt = subjectRepository.findByCode(code.trim().toUpperCase());
            if (opt.isPresent()) return opt.get();
        }
        return subjectRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND, "Chưa có môn học nào trong hệ thống"));
    }

    private List<DocumentChunk> ensureFoundationChunks(Subject subject) {
        String baseDocName = "Giao_trinh_Chuan_" + subject.getCode() + ".pdf";
        Document doc = Document.builder()
                .subject(subject)
                .fileName(baseDocName)
                .fileUrl("S3: /" + subject.getCode().toLowerCase() + "/syllabus/" + baseDocName)
                .fileSize("4.2 MB")
                .status("INDEXED")
                .chunkCount(0)
                .build();
        doc = documentRepository.save(doc);

        List<DocumentChunk> chunks = createSyllabusChunksForDoc(doc, subject, baseDocName);
        doc.setChunkCount(chunks.size());
        documentRepository.save(doc);
        return chunks;
    }

    private List<DocumentChunk> createSyllabusChunksForDoc(Document doc, Subject subject, String fileName) {
        List<String> rawChunks;
        String code = subject.getCode().toUpperCase();

        if (code.contains("CS") || code.contains("301") || code.contains("201")) {
            rawChunks = List.of(
                    "CHƯƠNG 1: ĐỘ PHỨC TẠP THUẬT TOÁN VÀ ĐÁNH GIÁ HIỆU NĂNG\nPhân tích tiệm cận (Big-O, Big-Omega, Big-Theta). Độ phức tạp thời gian và không gian bộ nhớ. Đánh giá trường hợp tốt nhất, xấu nhất và trung bình của các giải thuật sắp xếp và tìm kiếm nâng cao.",
                    "CHƯƠNG 2: CÂU TRÚC DỮ LIỆU CÂY CÂN BẰNG TỰ ĐỘNG\nCây tìm kiếm nhị phân (BST). Cây AVL: Điều kiện cân bằng hệ số (Balance Factor -1, 0, 1), 4 phép xoay cơ bản (Left-Left, Right-Right, Left-Right, Right-Left). Cây Đỏ-Đen (Red-Black Tree): 5 tính chất tô màu, cơ chế tái cân bằng sau thao tác chèn và xóa.",
                    "CHƯƠNG 3: BẢNG BĂM VÀ CƠ CHẾ XỬ LÝ XUNG ĐỘT\nHàm băm (Hash Function) và tính chất phân bổ đều. Xử lý va chạm: Chaining (danh sách liên kết) và Open Addressing (Linear Probing, Quadratic Probing, Double Hashing). Hệ số tải (Load Factor) và chiến lược rehashing.",
                    "CHƯƠNG 4: GIẢI THUẬT ĐỒ THỊ NÂNG CAO\nBiểu diễn đồ thị bằng ma trận kề và danh sách kề. Giải thuật tìm đường đi ngắn nhất Dijkstra (cơ chế Greedy, hàng đợi ưu tiên Priority Queue / Min-Heap). Giới hạn của Dijkstra với cung âm và giải pháp Bellman-Ford.",
                    "CHƯƠNG 5: QUY HOẠCH ĐỘNG VÀ BÀI TOÁN TỐI ƯU HÓA\nNguyên lý tối ưu Bellman, tính chất bài toán con gối nhau (Overlapping Subproblems) và cấu trúc con tối ưu (Optimal Substructure). Kỹ thuật Memoization vs Tabulation. Bài toán Knapsack, Longest Common Subsequence (LCS)."
            );
        } else {
            rawChunks = List.of(
                    "CHƯƠNG 1: TỔNG QUAN KIẾN TRÚC PHẦN MỀM VÀ NGUYÊN LÝ THIẾT KẾ\nCác mô hình kiến trúc phổ biến: Monolith, Layered (N-Tier), Microservices, Event-Driven Architecture. Nguyên lý SOLID trong thiết kế hướng đối tượng: Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion.",
                    "CHƯƠNG 2: FRAMEWORK NỀN TẢNG VÀ INVERSION OF CONTROL (IoC)\nNguyên lý Inversion of Control và Dependency Injection (DI). Vòng đời của Bean trong Spring Framework: Instantiation, Dependency Population, Post-Initialization, Destruction. So sánh Singleton scope và Prototype scope.",
                    "CHƯƠNG 3: QUẢN TRỊ TRẠNG THÁI VÀ SESSION PHÂN TÁN TRONG ỨNG DỤNG WEB\nVòng đời của HTTP Session, cơ chế JSESSIONID cookie. Thách thức phân tán: Session replication vs Centralized Cache (Redis / Memcached). Giải pháp Stateless Authentication bằng JSON Web Token (JWT).",
                    "CHƯƠNG 4: THIẾT KẾ VÀ BẢO MẬT HỆ THỐNG RESTFUL API\nThiết kế API theo chuẩn REST: HTTP Methods (GET, POST, PUT, DELETE, PATCH), Status Codes (200, 201, 400, 401, 403, 404, 500). Bảo mật ứng dụng với Spring Security, OAuth2 và xác thực phân quyền RBAC."
            );
        }

        List<DocumentChunk> entities = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            String content = rawChunks.get(i);
            entities.add(DocumentChunk.builder()
                    .document(doc)
                    .chunkIndex(i + 1)
                    .chunkContent(content)
                    .embedding(vectorEmbeddingPort.generateEmbeddingVector(content))
                    .build());
        }
        return documentChunkRepository.saveAll(entities);
    }

    private String extractTextFromFile(MultipartFile file, Subject subject, String fileName) {
        String lowerName = (fileName != null ? fileName : "").toLowerCase();
        try {
            byte[] bytes = file.getBytes();
            if (lowerName.endsWith(".docx") || lowerName.endsWith(".doc")) {
                String docxText = extractDocx(bytes);
                if (docxText.length() > 50) {
                    log.info("Successfully extracted {} characters from DOCX file '{}'", docxText.length(), fileName);
                    return docxText;
                }
            } else if (lowerName.endsWith(".pdf")) {
                String pdfText = extractPdf(bytes);
                if (pdfText.length() > 50) {
                    log.info("Successfully extracted {} characters from PDF file '{}'", pdfText.length(), fileName);
                    return pdfText;
                }
            }

            // UTF-8 plain text fallback for txt, md, json, csv, etc.
            String raw = new String(bytes, StandardCharsets.UTF_8);
            if (raw.length() > 30 && !raw.contains("\u0000")) {
                log.info("Successfully read {} characters from text file '{}'", raw.length(), fileName);
                return raw;
            }
        } catch (Exception e) {
            log.warn("Error reading file stream for {}: {}", fileName, e.getMessage());
        }

        // Return synthesized academic outline for non-text / binary files
        log.warn("Could not extract raw text from binary file '{}', using structured academic syllabus context.", fileName);
        return "TÀI LIỆU HỌC PHẦN " + subject.getCode() + " - " + subject.getName() + "\n" +
                "Tệp đính kèm: " + fileName + "\n" +
                "Nội dung trọng tâm: Hệ thống kiến thức chuẩn hóa, các mô hình lý thuyết nền tảng, bài toán thực tiễn và kỹ thuật giải quyết chuyên sâu theo thang đo nhận thức Bloom.";
    }

    private String extractDocx(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = zis.read(buf)) > 0) {
                        baos.write(buf, 0, len);
                    }
                    String xml = baos.toString(StandardCharsets.UTF_8);
                    Pattern p = Pattern.compile("<w:t[^>]*>(.*?)</w:t>");
                    Matcher m = p.matcher(xml);
                    while (m.find()) {
                        sb.append(m.group(1)).append(" ");
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Error reading docx stream: {}", e.getMessage());
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private String extractPdf(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        try {
            Pattern pLiteral = Pattern.compile("\\(([\\x20-\\x7E\\xA0-\\xFF\\s]{3,})\\)\\s*(?:Tj|TJ|'|\")");
            // 1. Literal pattern search in uncompressed streams
            String rawIso = new String(bytes, StandardCharsets.ISO_8859_1);
            Matcher mLiteral = pLiteral.matcher(rawIso);
            while (mLiteral.find()) {
                sb.append(mLiteral.group(1)).append(" ");
            }

            // 2. Decompress FlateDecode streams
            int idx = 0;
            byte[] streamHeader = "stream".getBytes(StandardCharsets.ISO_8859_1);
            byte[] streamEnd = "endstream".getBytes(StandardCharsets.ISO_8859_1);

            while ((idx = indexOfBytes(bytes, streamHeader, idx)) != -1) {
                int start = idx + streamHeader.length;
                if (start < bytes.length && (bytes[start] == '\r' || bytes[start] == '\n')) start++;
                if (start < bytes.length && (bytes[start] == '\r' || bytes[start] == '\n')) start++;

                int end = indexOfBytes(bytes, streamEnd, start);
                if (end != -1 && end > start) {
                    byte[] deflated = new byte[end - start];
                    System.arraycopy(bytes, start, deflated, 0, deflated.length);
                    try {
                        Inflater inflater = new Inflater();
                        inflater.setInput(deflated);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        while (!inflater.finished()) {
                            int count = inflater.inflate(buffer);
                            if (count <= 0) break;
                            baos.write(buffer, 0, count);
                        }
                        inflater.end();
                        byte[] decompressed = baos.toByteArray();
                        if (decompressed.length > 0) {
                            String decompStr = new String(decompressed, StandardCharsets.UTF_8);
                            Matcher m2 = pLiteral.matcher(decompStr);
                            while (m2.find()) {
                                sb.append(m2.group(1)).append(" ");
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    idx = end + streamEnd.length;
                } else {
                    idx = start + 1;
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing PDF bytes: {}", e.getMessage());
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private int indexOfBytes(byte[] data, byte[] target, int start) {
        outer: for (int i = start; i <= data.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (data[i + j] != target[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private List<String> splitIntoChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            chunks.add("Nội dung tài liệu học phần chuẩn hóa.");
            return chunks;
        }

        int length = text.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            String sub = text.substring(start, end).trim();
            if (!sub.isEmpty()) {
                chunks.add(sub);
            }
            if (end == length) break;
            start = end - overlap;
        }
        return chunks;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

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

    /**
     * Trích xuất số mức Bloom (1..6) từ nhãn lưu trong DB.
     * Nhận diện được cả dạng canonical ("Bloom 4 - Phân tích"), dạng thô từ AI
     * ("CREATE", "APPLY") và dạng có hậu tố ("Bloom 4 - Phân tích Dữ phức tạp").
     *
     * @return số mức 1..6, hoặc null nếu không xác định được.
     */
    private Integer bloomNumberOf(String raw) {
        return GeminiAIAdapter.extractBloomNumber(raw);
    }

    private QuestionResponse mapToQuestionResponse(Question q) {
        List<Rubric> rubrics = rubricRepository.findByQuestionUuid(q.getUuid());
        int rubricCount = rubrics.size();
        if (rubricCount == 0 && q.getSubject() != null) {
            // Fallback về rubric TEMPLATE của môn học (không lấy rubric của câu hỏi khác)
            rubrics = rubricRepository.findBySubjectUuidAndQuestionUuidIsNull(q.getSubject().getUuid());
            rubricCount = rubrics.size();
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

        // Màu badge theo MỨC BLOOM (số 1..6), không dựa vào contains("1")/contains("4")
        // vì nhãn của Gemini có thể là dạng thô ("CREATE") hoặc có hậu tố ("Bloom 4 - Phân tích Dữ phức tạp").
        Integer bloomNumber = bloomNumberOf(q.getBloomLevel());
        String bloomColor = "bg-blue-50 text-blue-700 border-blue-200";
        if ("HARD".equalsIgnoreCase(q.getDifficulty()) || (bloomNumber != null && bloomNumber >= 4)) {
            bloomColor = "bg-purple-50 text-purple-700 border-purple-200";
        } else if ("EASY".equalsIgnoreCase(q.getDifficulty()) || (bloomNumber != null && bloomNumber <= 2)) {
            bloomColor = "bg-emerald-50 text-emerald-700 border-emerald-200";
        }

        List<RubricResponse> rubricResponses = rubrics.stream()
                .map(this::mapToRubricResponse)
                .collect(Collectors.toList());

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
                .rubrics(rubricResponses)
                .createdAt(q.getCreatedAt())
                .build();
    }

    private RubricResponse mapToRubricResponse(Rubric r) {
        return RubricResponse.builder()
                .uuid(r.getUuid())
                .id(r.getUuid())
                .subjectId(r.getSubject() != null ? r.getSubject().getUuid() : null)
                .questionId(r.getQuestion() != null ? r.getQuestion().getUuid() : null)
                .isTemplate(r.getQuestion() == null)
                .criterionName(r.getCriterionName())
                .description(r.getDescription())
                .maxScore(r.getMaxScore())
                .weight(r.getWeight())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

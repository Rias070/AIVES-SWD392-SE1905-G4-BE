package com.aives.service;

import com.aives.dto.request.ExamStartRequest;
import com.aives.dto.request.LecturerCreateExamRequest;
import com.aives.dto.request.VivaTurnSubmitRequest;
import com.aives.dto.response.ExamSessionResponse;
import com.aives.dto.response.VivaTurnResponse;
import com.aives.entity.*;
import com.aives.enums.ErrorCode;
import com.aives.enums.ExamSessionStatus;
import com.aives.exception.AppException;
import com.aives.repository.*;
import lombok.extern.slf4j.Slf4j;
import com.aives.application.port.in.StudentExamUseCase;
import com.aives.application.port.out.LLMProviderPort;
import com.aives.dto.response.VivaEvaluationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aives.dto.request.LecturerFinalizeScoreRequest;
import com.aives.dto.request.VivaTurnWsRequest;
import com.aives.dto.response.VivaTurnWsResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StudentExamService implements StudentExamUseCase {

    private final ExamSessionRepository examSessionRepository;
    private final VivaTurnRepository vivaTurnRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final RubricRepository rubricRepository;
    private final LLMProviderPort llmProviderPort;

    public StudentExamService(ExamSessionRepository examSessionRepository,
                              VivaTurnRepository vivaTurnRepository,
                              SubjectRepository subjectRepository,
                              UserRepository userRepository,
                              QuestionRepository questionRepository,
                              DocumentChunkRepository documentChunkRepository,
                              RubricRepository rubricRepository,
                              LLMProviderPort llmProviderPort) {
        this.examSessionRepository = examSessionRepository;
        this.vivaTurnRepository = vivaTurnRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.rubricRepository = rubricRepository;
        this.llmProviderPort = llmProviderPort;
    }

    public List<ExamSessionResponse> getStudentSessions(UUID studentId) {
        List<ExamSession> sessions;
        if (studentId != null) {
            sessions = examSessionRepository.findByStudentUuidOrderByScheduledAtDesc(studentId);
        } else {
            sessions = examSessionRepository.findAll();
        }
        return sessions.stream().map(this::mapToSessionResponse).collect(Collectors.toList());
    }

    public ExamSessionResponse getSessionById(UUID sessionId) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_SESSION_NOT_FOUND));
        return mapToSessionResponse(session);
    }

    public List<VivaTurnResponse> getSessionTurns(UUID sessionId) {
        return vivaTurnRepository.findByExamSessionUuidOrderByTurnOrderAsc(sessionId).stream()
                .map(this::mapToTurnResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ExamSessionResponse startExamSession(UUID studentId, ExamStartRequest request) {
        log.info("Starting exam session for student: {}", studentId);

        User student = null;
        if (studentId != null) {
            student = userRepository.findById(studentId).orElse(null);
        }
        if (student == null) {
            student = userRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        }

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

        String sessionCode = request.getSessionCode();
        if (sessionCode != null && !sessionCode.isBlank()) {
            java.util.Optional<ExamSession> existingOpt = examSessionRepository.findBySessionCode(sessionCode.trim());
            if (existingOpt.isPresent()) {
                ExamSession existing = existingOpt.get();
                if (existing.getStatus() == ExamSessionStatus.COMPLETED || existing.getStatus() == ExamSessionStatus.WAITING_SCORE_CONFIRMATION) {
                    log.warn("Student attempted to retake completed session: " + sessionCode);
                    throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                }
                existing.setStatus(ExamSessionStatus.IN_PROGRESS);
                if (existing.getStartedAt() == null) {
                    existing.setStartedAt(LocalDateTime.now());
                }
                return mapToSessionResponse(examSessionRepository.save(existing));
            }
        }
        if (sessionCode == null || sessionCode.isBlank()) {
            sessionCode = "VIVA-" + subject.getCode() + "-" + (1000 + (int)(Math.random() * 9000));
        }

        ExamSession session = ExamSession.builder()
                .sessionCode(sessionCode)
                .subject(subject)
                .student(student)
                .scheduledAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .status(ExamSessionStatus.IN_PROGRESS)
                .build();

        session = examSessionRepository.save(session);
        return mapToSessionResponse(session);
    }

    @Transactional
    public VivaTurnResponse submitVivaTurn(UUID sessionId, VivaTurnSubmitRequest request) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseGet(() -> {
                    log.warn("Session {} not found in DB during REST submitTurn, auto-creating active session", sessionId);
                    Subject subject = subjectRepository.findAll().stream().findFirst().orElse(null);
                    User student = userRepository.findAll().stream().findFirst().orElse(null);
                    return examSessionRepository.save(ExamSession.builder()
                            .uuid(sessionId)
                            .sessionCode("VIVA-REST-" + (1000 + (int)(Math.random() * 9000)))
                            .subject(subject)
                            .student(student)
                            .scheduledAt(LocalDateTime.now())
                            .startedAt(LocalDateTime.now())
                            .status(ExamSessionStatus.IN_PROGRESS)
                            .build());
                });

        Question question = null;
        if (request.getQuestionUuid() != null) {
            question = questionRepository.findById(request.getQuestionUuid()).orElse(null);
        }
        if (question == null && request.getQuestionContent() != null && !request.getQuestionContent().isBlank()) {
            String qContent = request.getQuestionContent().trim();
            Subject subj = session.getSubject() != null ? session.getSubject() : subjectRepository.findAll().stream().findFirst().orElse(null);
            Question newQ = Question.builder()
                    .subject(subj)
                    .content(qContent)
                    .questionCode("VIVA-Q-" + (100 + (int)(Math.random() * 900)))
                    .bloomLevel(request.getBloomLevel() != null ? request.getBloomLevel() : "Bloom 3 - Vận dụng")
                    .difficulty("MEDIUM")
                    .status("ACTIVE")
                    .build();
            try {
                question = questionRepository.save(newQ);
            } catch (Exception e) {
                log.warn("Could not persist transient question: {}", e.getMessage());
            }
        }

        UUID subjectUuid = session.getSubject() != null ? session.getSubject().getUuid() : null;
        String subjectCode = session.getSubject() != null ? session.getSubject().getCode() : "CS301";

        // 1. RAG snippets from document_chunks
        List<String> ragSnippets = new ArrayList<>();
        if (subjectUuid != null) {
            try {
                List<DocumentChunk> chunks = documentChunkRepository.findTopChunksBySubject(subjectUuid, 3);
                for (DocumentChunk c : chunks) {
                    if (c.getChunkContent() != null && !c.getChunkContent().isBlank()) {
                        ragSnippets.add(c.getChunkContent());
                    }
                }
            } catch (Exception e) {
                log.warn("Could not query document_chunks for RAG: {}", e.getMessage());
            }
        }
        if (ragSnippets.isEmpty()) {
            ragSnippets.add("Đề cương môn học " + subjectCode + ": Nắm vững thuật toán và cấu trúc dữ liệu, phân tích độ phức tạp.");
        }

        // 2. Rubric criteria
        List<Rubric> rubrics = Collections.emptyList();
        if (question != null) {
            try {
                rubrics = rubricRepository.findByQuestionUuid(question.getUuid());
            } catch (Exception e) {
                log.warn("Could not fetch rubrics for question: {}", e.getMessage());
            }
        }
        if (rubrics.isEmpty() && subjectUuid != null) {
            try {
                rubrics = rubricRepository.findBySubjectUuidAndQuestionUuidIsNull(subjectUuid);
            } catch (Exception e) {
                log.warn("Could not fetch rubric template for subject: {}", e.getMessage());
            }
        }

        String rawAnswer = request.getStudentAnswer();
        if (rawAnswer == null || rawAnswer.isBlank()) {
            rawAnswer = request.getAudioTranscript();
        }
        boolean isUnanswered = (rawAnswer == null || rawAnswer.isBlank()
                || rawAnswer.trim().equalsIgnoreCase("(Thí sinh không trả lời)")
                || rawAnswer.trim().equalsIgnoreCase("Thí sinh không trả lời")
                || rawAnswer.trim().length() < 3);

        String studentAnswer = isUnanswered ? "(Thí sinh không trả lời)" : rawAnswer.trim();
        String audioTranscript = isUnanswered ? "(Không ghi nhận giọng nói từ thí sinh)" : rawAnswer.trim();

        String questionContent = question != null ? question.getContent() : (request.getQuestionContent() != null ? request.getQuestionContent() : "Câu hỏi kiểm tra vấn đáp");
        int turnOrder = request.getTurnOrder() != null ? request.getTurnOrder() : 1;

        VivaEvaluationResult evalResult;
        if (isUnanswered) {
            evalResult = new VivaEvaluationResult(
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    "Thí sinh không trả lời hoặc không ghi nhận được âm thanh trả lời cho câu hỏi này. Điểm đề xuất: 0.0.",
                    null,
                    "Bloom 1 - Nhận biết"
            );
        } else {
            evalResult = llmProviderPort.evaluateVivaTurn(
                    subjectCode,
                    turnOrder,
                    questionContent,
                    studentAnswer,
                    ragSnippets,
                    rubrics,
                    "vi-VN"
            );
        }

        VivaTurn turn = VivaTurn.builder()
                .examSession(session)
                .question(question)
                .turnOrder(turnOrder)
                .studentAnswer(studentAnswer)
                .audioTranscript(audioTranscript)
                .aiEvaluation(evalResult.aiEvaluation)
                .score(evalResult.score)
                .turnDurationSeconds(request.getTurnDurationSeconds() != null ? request.getTurnDurationSeconds() : 60)
                .build();

        turn = vivaTurnRepository.save(turn);
        VivaTurnResponse response = mapToTurnResponse(turn);
        response.setFollowupQuestion(evalResult.followupQuestion);
        response.setBloomLevel(evalResult.bloomLevel);
        return response;
    }

    @Transactional
    public ExamSessionResponse finishExamSession(UUID sessionId) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseGet(() -> {
                    log.warn("Session {} not found in DB during finishExamSession, auto-creating completed session", sessionId);
                    Subject subject = subjectRepository.findAll().stream().findFirst().orElse(null);
                    User student = userRepository.findAll().stream().findFirst().orElse(null);
                    return examSessionRepository.save(ExamSession.builder()
                            .uuid(sessionId)
                            .sessionCode("VIVA-FIN-" + (1000 + (int)(Math.random() * 9000)))
                            .subject(subject)
                            .student(student)
                            .scheduledAt(LocalDateTime.now())
                            .startedAt(LocalDateTime.now())
                            .status(ExamSessionStatus.IN_PROGRESS)
                            .build());
                });

        List<VivaTurn> turns = vivaTurnRepository.findByExamSessionUuidOrderByTurnOrderAsc(sessionId);
        BigDecimal avgScore = BigDecimal.ZERO;
        String feedbackMsg;
        if (!turns.isEmpty()) {
            BigDecimal sum = turns.stream()
                    .map(t -> t.getScore() != null ? t.getScore() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgScore = sum.divide(BigDecimal.valueOf(turns.size()), 2, RoundingMode.HALF_UP);
            if (avgScore.compareTo(BigDecimal.valueOf(1.0)) <= 0) {
                feedbackMsg = "Thí sinh không trả lời hoặc bỏ qua các câu hỏi trong ca thi. Điểm đề xuất: " + avgScore + "/10.";
            } else if (avgScore.compareTo(BigDecimal.valueOf(5.0)) < 0) {
                feedbackMsg = "Thí sinh trả lời còn thiếu sót, cần trau dồi thêm. Điểm đề xuất: " + avgScore + "/10.";
            } else {
                feedbackMsg = "Sinh viên đã hoàn thành ca thi vấn đáp AI. Điểm AI đề xuất: " + avgScore + "/10.";
            }
        } else {
            feedbackMsg = "Thí sinh đã rời phòng thi hoặc không trả lời câu hỏi nào. Điểm đề xuất: 0.0.";
        }

        session.setStatus(ExamSessionStatus.WAITING_SCORE_CONFIRMATION);
        session.setEndedAt(LocalDateTime.now());
        session.setTotalScore(avgScore);
        session.setFeedback(feedbackMsg);

        session = examSessionRepository.save(session);
        return mapToSessionResponse(session);
    }

    private ExamSessionResponse mapToSessionResponse(ExamSession es) {
        int turnsCount = vivaTurnRepository.findByExamSessionUuidOrderByTurnOrderAsc(es.getUuid()).size();

        String rubricBadge = "Rubric: Standard";
        String badgeColor = "bg-sky-50 text-sky-700 border-sky-200";
        if (es.getTotalScore() != null) {
            double score = es.getTotalScore().doubleValue();
            if (score >= 9.0) {
                rubricBadge = "Rubric: Outstanding";
                badgeColor = "bg-amber-50 text-amber-700 border-amber-200";
            } else if (score >= 8.0) {
                rubricBadge = "Rubric: Excellent";
                badgeColor = "bg-emerald-50 text-emerald-700 border-emerald-200";
            }
        }

        return ExamSessionResponse.builder()
                .id(es.getUuid())
                .sessionCode(es.getSessionCode())
                .subjectId(es.getSubject() != null ? es.getSubject().getUuid() : null)
                .subjectCode(es.getSubject() != null ? es.getSubject().getCode() : "N/A")
                .subjectName(es.getSubject() != null ? es.getSubject().getName() : "N/A")
                .studentId(es.getStudent() != null ? es.getStudent().getUuid() : null)
                .studentName(es.getStudent() != null ? es.getStudent().getFullName() : "N/A")
                .studentEmail(es.getStudent() != null ? es.getStudent().getEmail() : "N/A")
                .scheduledAt(es.getScheduledAt())
                .startedAt(es.getStartedAt())
                .endedAt(es.getEndedAt())
                .status(es.getStatus())
                .totalScore(es.getTotalScore())
                .feedback(es.getFeedback())
                .turnsCount(turnsCount)
                .rubricBadge(rubricBadge)
                .badgeColor(badgeColor)
                .language(es.getSubject() != null && es.getSubject().getLanguage() != null ? es.getSubject().getLanguage() : "vi-VN")
                .technicalTerms(es.getSubject() != null ? es.getSubject().getTechnicalTerms() : null)
                .build();
    }

    private VivaTurnResponse mapToTurnResponse(VivaTurn t) {
        return VivaTurnResponse.builder()
                .uuid(t.getUuid())
                .turnOrder(t.getTurnOrder())
                .questionId(t.getQuestion() != null ? t.getQuestion().getUuid() : null)
                .questionContent(t.getQuestion() != null ? t.getQuestion().getContent() : "Câu hỏi Viva")
                .studentAnswer(t.getStudentAnswer())
                .audioTranscript(t.getAudioTranscript())
                .aiEvaluation(t.getAiEvaluation())
                .score(t.getScore())
                .turnDurationSeconds(t.getTurnDurationSeconds())
                .createdAt(t.getCreatedAt())
                .build();
    }

    @Transactional
    public VivaTurnWsResponse processVivaTurn(UUID sessionId, VivaTurnWsRequest request) {
        log.info("Processing WebSocket viva turn for session: {}, turn: {}", sessionId, request.getTurnOrder());

        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseGet(() -> {
                    log.warn("Session {} not found in DB, auto-creating active session for live viva room", sessionId);
                    Subject subject = subjectRepository.findAll().stream().findFirst().orElse(null);
                    User student = userRepository.findAll().stream().findFirst().orElse(null);
                    return examSessionRepository.save(ExamSession.builder()
                            .uuid(sessionId)
                            .sessionCode("VIVA-WS-" + (1000 + (int)(Math.random() * 9000)))
                            .subject(subject)
                            .student(student)
                            .scheduledAt(LocalDateTime.now())
                            .startedAt(LocalDateTime.now())
                            .status(ExamSessionStatus.IN_PROGRESS)
                            .build());
                });

        UUID subjectUuid = session.getSubject() != null ? session.getSubject().getUuid() : null;
        String subjectCode = session.getSubject() != null ? session.getSubject().getCode() : "CS301";

        // 1. RAG context retrieval from document_chunks (pgvector)
        List<String> ragSnippets = new ArrayList<>();
        if (subjectUuid != null) {
            try {
                List<DocumentChunk> chunks = documentChunkRepository.findTopChunksBySubject(subjectUuid, 3);
                for (DocumentChunk c : chunks) {
                    if (c.getChunkContent() != null && !c.getChunkContent().isBlank()) {
                        ragSnippets.add(c.getChunkContent());
                    }
                }
            } catch (Exception e) {
                log.warn("Could not query document_chunks for RAG: {}", e.getMessage());
            }
        }
        if (ragSnippets.isEmpty()) {
            ragSnippets.add("Đề cương " + subjectCode + ": Nắm vững thuật toán, phân tích độ phức tạp O(V+E), tính bất biến và phát hiện trường hợp biên.");
            ragSnippets.add("Barem Viva: Đánh giá khả năng lập luận phản biện, giải thích nguyên lý và liên hệ thực tế.");
        }

        // 2. Rubric criteria
        List<Rubric> rubrics = Collections.emptyList();
        if (request.getQuestionUuid() != null) {
            try {
                rubrics = rubricRepository.findByQuestionUuid(request.getQuestionUuid());
            } catch (Exception e) {
                log.warn("Could not fetch rubrics for question: {}", e.getMessage());
            }
        }
        if (rubrics.isEmpty() && subjectUuid != null) {
            try {
                rubrics = rubricRepository.findBySubjectUuidAndQuestionUuidIsNull(subjectUuid);
            } catch (Exception e) {
                log.warn("Could not fetch rubric template for subject: {}", e.getMessage());
            }
        }

        // 3. Transcript resolution
        String rawTranscript = request.getStudentTranscript();
        if (rawTranscript == null || rawTranscript.isBlank()) {
            rawTranscript = request.getStudentAnswer();
        }
        boolean isUnanswered = (rawTranscript == null || rawTranscript.isBlank()
                || rawTranscript.trim().equalsIgnoreCase("(Thí sinh không trả lời)")
                || rawTranscript.trim().equalsIgnoreCase("Thí sinh không trả lời")
                || rawTranscript.trim().length() < 3);

        String studentTranscript = isUnanswered ? "(Thí sinh không trả lời)" : rawTranscript.trim();
        int turnOrder = request.getTurnOrder() != null ? request.getTurnOrder() : 1;
        String lang = request.getExamLang() != null ? request.getExamLang() : "vi-VN";

        VivaEvaluationResult evalResult;
        if (isUnanswered) {
            evalResult = new VivaEvaluationResult(
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    "Thí sinh không trả lời hoặc không ghi nhận được âm thanh trả lời cho câu hỏi này. Điểm đề xuất: 0.0.",
                    null,
                    "Bloom 1 - Nhận biết"
            );
        } else {
            evalResult = llmProviderPort.evaluateVivaTurn(
                    subjectCode,
                    turnOrder,
                    request.getQuestionContent(),
                    studentTranscript,
                    ragSnippets,
                    rubrics,
                    lang
            );
        }

        // 4. Save to viva_turns
        Question question = null;
        if (request.getQuestionUuid() != null) {
            question = questionRepository.findById(request.getQuestionUuid()).orElse(null);
        }
        if (question == null && request.getQuestionContent() != null && !request.getQuestionContent().isBlank()) {
            String qContent = request.getQuestionContent().trim();
            Subject subj = session.getSubject() != null ? session.getSubject() : subjectRepository.findAll().stream().findFirst().orElse(null);
            Question newQ = Question.builder()
                    .subject(subj)
                    .content(qContent)
                    .questionCode("VIVA-Q-" + (100 + (int)(Math.random() * 900)))
                    .bloomLevel("Bloom 3 - Vận dụng")
                    .difficulty("MEDIUM")
                    .status("ACTIVE")
                    .build();
            try {
                question = questionRepository.save(newQ);
            } catch (Exception e) {
                log.warn("Could not persist transient question: {}", e.getMessage());
            }
        }

        VivaTurn turn = VivaTurn.builder()
                .examSession(session)
                .question(question)
                .turnOrder(turnOrder)
                .studentAnswer(studentTranscript)
                .audioTranscript(studentTranscript)
                .aiEvaluation(evalResult.aiEvaluation)
                .score(evalResult.score)
                .turnDurationSeconds(request.getTurnDurationSeconds() != null ? request.getTurnDurationSeconds() : 60)
                .build();
        vivaTurnRepository.save(turn);

        // 5. Update exam_session state
        boolean isLast = turnOrder >= 3;
        if (isLast) {
            session.setStatus(ExamSessionStatus.WAITING_SCORE_CONFIRMATION);
            session.setEndedAt(LocalDateTime.now());
            List<VivaTurn> allTurns = vivaTurnRepository.findByExamSessionUuidOrderByTurnOrderAsc(session.getUuid());
            BigDecimal sum = allTurns.stream()
                    .map(t -> t.getScore() != null ? t.getScore() : evalResult.score)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(Math.max(1, allTurns.size())), 2, RoundingMode.HALF_UP);
            session.setTotalScore(avg);
            session.setFeedback(evalResult.aiEvaluation);
        } else {
            session.setStatus(ExamSessionStatus.IN_PROGRESS);
        }
        examSessionRepository.save(session);

        return VivaTurnWsResponse.builder()
                .sessionId(session.getUuid())
                .turnOrder(turnOrder)
                .suggestedScore(evalResult.score)
                .aiEvaluation(evalResult.aiEvaluation)
                .followupQuestion(evalResult.followupQuestion)
                .bloomLevel(evalResult.bloomLevel)
                .ragContextSnippets(ragSnippets)
                .lastTurn(isLast)
                .status(session.getStatus().name())
                .build();
    }

    @Transactional
    public ExamSessionResponse finalizeExamSessionScore(UUID sessionId, LecturerFinalizeScoreRequest request) {
        log.info("Finalizing exam session {} with lecturer official score: {}", sessionId, request.getFinalScore());
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_SESSION_NOT_FOUND));

        session.setTotalScore(request.getFinalScore());
        if (request.getLecturerFeedback() != null && !request.getLecturerFeedback().isBlank()) {
            session.setFeedback(request.getLecturerFeedback());
        }
        session.setStatus(ExamSessionStatus.COMPLETED);
        if (session.getEndedAt() == null) {
            session.setEndedAt(LocalDateTime.now());
        }
        if (request.getExaminerId() != null) {
            userRepository.findById(request.getExaminerId()).ifPresent(session::setExaminer);
        }

        session = examSessionRepository.save(session);
        return mapToSessionResponse(session);
    }

    @Transactional
    @Override
    public ExamSessionResponse createExamSession(UUID examinerId, LecturerCreateExamRequest request) {
        log.info("Lecturer {} creating new exam session: {}", examinerId, request.getSessionCode());

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

        User examiner = null;
        if (examinerId != null) {
            examiner = userRepository.findById(examinerId).orElse(null);
        }

        User student = null;
        if (request.getStudentUuid() != null) {
            student = userRepository.findById(request.getStudentUuid()).orElse(null);
        }
        if (student == null) {
            student = userRepository.findByEmail("student@aives.edu.vn").orElse(null);
        }
        if (student == null) {
            student = userRepository.findAll().stream().findFirst().orElse(null);
        }

        LocalDateTime scheduledAt = request.getScheduledAt() != null ? request.getScheduledAt() : LocalDateTime.now();
        LocalDateTime endedAt = request.getEndedAt() != null ? request.getEndedAt() : scheduledAt.plusDays(7);

        StringBuilder fb = new StringBuilder();
        if (request.getQuestionUuids() != null && !request.getQuestionUuids().isEmpty()) {
            fb.append("QUESTIONS:").append(request.getQuestionUuids().stream().map(UUID::toString).collect(Collectors.joining(",")));
        }
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            if (fb.length() > 0) fb.append(" | ");
            fb.append(request.getNotes().trim());
        }

        ExamSession session = ExamSession.builder()
                .sessionCode(request.getSessionCode().trim())
                .subject(subject)
                .student(student)
                .examiner(examiner)
                .scheduledAt(scheduledAt)
                .endedAt(endedAt)
                .status(ExamSessionStatus.SCHEDULED)
                .feedback(fb.toString())
                .build();

        session = examSessionRepository.save(session);
        return mapToSessionResponse(session);
    }

    @Transactional
    @Override
    public void deleteExamSession(UUID sessionId) {
        log.info("Deleting exam session: {}", sessionId);
        if (examSessionRepository.existsById(sessionId)) {
            vivaTurnRepository.deleteByExamSessionUuid(sessionId);
            examSessionRepository.deleteById(sessionId);
        }
    }
}

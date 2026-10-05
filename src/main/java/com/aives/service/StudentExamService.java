package com.aives.service;

import com.aives.dto.request.ExamStartRequest;
import com.aives.dto.request.VivaTurnSubmitRequest;
import com.aives.dto.response.ExamSessionResponse;
import com.aives.dto.response.VivaTurnResponse;
import com.aives.entity.*;
import com.aives.enums.ErrorCode;
import com.aives.enums.ExamSessionStatus;
import com.aives.exception.AppException;
import com.aives.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentExamService {

    private final ExamSessionRepository examSessionRepository;
    private final VivaTurnRepository vivaTurnRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;

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
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_SESSION_NOT_FOUND));

        Question question = null;
        if (request.getQuestionUuid() != null) {
            question = questionRepository.findById(request.getQuestionUuid()).orElse(null);
        }

        // Mock evaluation score between 7.5 and 9.5 for demo
        double mockScore = 7.5 + (Math.random() * 2.0);
        BigDecimal turnScore = BigDecimal.valueOf(mockScore).setScale(2, RoundingMode.HALF_UP);

        VivaTurn turn = VivaTurn.builder()
                .examSession(session)
                .question(question)
                .turnOrder(request.getTurnOrder())
                .studentAnswer(request.getStudentAnswer())
                .audioTranscript(request.getAudioTranscript())
                .aiEvaluation("Đánh giá AI: Luận điểm rõ ràng, lập luận logic, thuật ngữ chuyên ngành chính xác.")
                .score(turnScore)
                .turnDurationSeconds(request.getTurnDurationSeconds() != null ? request.getTurnDurationSeconds() : 60)
                .build();

        turn = vivaTurnRepository.save(turn);
        return mapToTurnResponse(turn);
    }

    @Transactional
    public ExamSessionResponse finishExamSession(UUID sessionId) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_SESSION_NOT_FOUND));

        List<VivaTurn> turns = vivaTurnRepository.findByExamSessionUuidOrderByTurnOrderAsc(sessionId);
        BigDecimal avgScore = BigDecimal.valueOf(8.5);
        if (!turns.isEmpty()) {
            BigDecimal sum = turns.stream()
                    .map(t -> t.getScore() != null ? t.getScore() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgScore = sum.divide(BigDecimal.valueOf(turns.size()), 2, RoundingMode.HALF_UP);
        }

        session.setStatus(ExamSessionStatus.COMPLETED);
        session.setEndedAt(LocalDateTime.now());
        session.setTotalScore(avgScore);
        session.setFeedback("Sinh viên nắm vững kiến thức trọng tâm môn học, phản xạ vấn đáp tốt và sử dụng đúng thuật ngữ chuyên môn.");

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
}

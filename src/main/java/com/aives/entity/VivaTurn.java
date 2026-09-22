package com.aives.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "viva_turns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VivaTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", updatable = false, nullable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_session_uuid", nullable = false)
    private ExamSession examSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_uuid")
    private Question question;

    @Column(name = "turn_order", nullable = false)
    private Integer turnOrder;

    @Column(name = "student_answer", columnDefinition = "TEXT")
    private String studentAnswer;

    @Column(name = "audio_transcript", columnDefinition = "TEXT")
    private String audioTranscript;

    @Column(name = "ai_evaluation", columnDefinition = "TEXT")
    private String aiEvaluation;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "turn_duration_seconds")
    private Integer turnDurationSeconds;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

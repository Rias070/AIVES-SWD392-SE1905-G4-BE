package com.aives.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "questions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", updatable = false, nullable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_uuid", nullable = false)
    private Subject subject;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "question_code", length = 50)
    private String questionCode;

    @Column(name = "bloom_level", length = 50)
    private String bloomLevel; // Bloom 1 - Nhớ, Bloom 2 - Hiểu, Bloom 3 - Vận dụng, Bloom 4 - Phân tích, etc.

    @Column(name = "difficulty", length = 20)
    @Builder.Default
    private String difficulty = "MEDIUM"; // EASY, MEDIUM, HARD

    @Column(name = "expected_answer", columnDefinition = "TEXT")
    private String expectedAnswer;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords;

    /**
     * Vector embedding representation for pgvector semantic search (RAG).
     */
    @Column(name = "embedding", columnDefinition = "vector")
    private String embedding;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

package com.aives.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subject_lecturers", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"subject_uuid", "lecturer_uuid"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectLecturer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", updatable = false, nullable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_uuid", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lecturer_uuid", nullable = false)
    private User lecturer;

    @Column(name = "can_approve_rag")
    @Builder.Default
    private Boolean canApproveRag = true;

    @Column(name = "can_edit_rubric")
    @Builder.Default
    private Boolean canEditRubric = true;

    @CreationTimestamp
    @Column(name = "assigned_at", updatable = false)
    private LocalDateTime assignedAt;
}

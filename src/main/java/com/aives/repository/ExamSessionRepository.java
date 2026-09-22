package com.aives.repository;

import com.aives.entity.ExamSession;
import com.aives.enums.ExamSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamSessionRepository extends JpaRepository<ExamSession, UUID> {
    Optional<ExamSession> findBySessionCode(String sessionCode);
    List<ExamSession> findByStudentUuidOrderByScheduledAtDesc(UUID studentUuid);
    List<ExamSession> findBySubjectUuid(UUID subjectUuid);
    List<ExamSession> findByStatus(ExamSessionStatus status);
}

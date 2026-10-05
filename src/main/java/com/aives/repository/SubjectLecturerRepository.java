package com.aives.repository;

import com.aives.entity.SubjectLecturer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubjectLecturerRepository extends JpaRepository<SubjectLecturer, UUID> {
    List<SubjectLecturer> findBySubjectUuid(UUID subjectUuid);
    List<SubjectLecturer> findByLecturerUuid(UUID lecturerUuid);
    Optional<SubjectLecturer> findBySubjectUuidAndLecturerUuid(UUID subjectUuid, UUID lecturerUuid);
    boolean existsBySubjectUuidAndLecturerUuid(UUID subjectUuid, UUID lecturerUuid);
}

package com.aives.repository;

import com.aives.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findBySubjectUuidOrderByCreatedAtDesc(UUID subjectUuid);
    List<Document> findBySubjectCodeOrderByCreatedAtDesc(String subjectCode);
}

package com.aives.repository;

import com.aives.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    List<DocumentChunk> findByDocumentUuidOrderByChunkIndexAsc(UUID documentUuid);
    void deleteByDocumentUuid(UUID documentUuid);
}

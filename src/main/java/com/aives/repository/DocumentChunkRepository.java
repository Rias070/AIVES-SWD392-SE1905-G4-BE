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

    @org.springframework.data.jpa.repository.Query(value = "SELECT dc.* FROM document_chunks dc " +
            "JOIN documents d ON dc.document_uuid = d.uuid " +
            "WHERE d.subject_uuid = :subjectUuid " +
            "ORDER BY dc.chunk_index ASC LIMIT :limit", nativeQuery = true)
    List<DocumentChunk> findTopChunksBySubject(@org.springframework.data.repository.query.Param("subjectUuid") UUID subjectUuid,
                                               @org.springframework.data.repository.query.Param("limit") int limit);

    @org.springframework.data.jpa.repository.Query(value = "SELECT dc.* FROM document_chunks dc " +
            "JOIN documents d ON dc.document_uuid = d.uuid " +
            "WHERE d.subject_uuid = :subjectUuid AND dc.embedding IS NOT NULL " +
            "ORDER BY dc.embedding <=> CAST(:queryEmbedding AS vector) LIMIT :limit", nativeQuery = true)
    List<DocumentChunk> findSimilarChunksBySubject(@org.springframework.data.repository.query.Param("subjectUuid") UUID subjectUuid,
                                                   @org.springframework.data.repository.query.Param("queryEmbedding") String queryEmbedding,
                                                   @org.springframework.data.repository.query.Param("limit") int limit);
}

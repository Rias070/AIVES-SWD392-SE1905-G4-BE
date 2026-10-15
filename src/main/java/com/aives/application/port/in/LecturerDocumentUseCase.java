package com.aives.application.port.in;

import com.aives.dto.request.DocumentUploadRequest;
import com.aives.dto.response.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface LecturerDocumentUseCase {
    List<DocumentResponse> getDocuments(String subjectCode, UUID subjectUuid);
    DocumentResponse uploadDocument(DocumentUploadRequest request);
    DocumentResponse uploadDocumentFile(MultipartFile file, String subjectCode, UUID subjectUuid);
    void deleteDocument(UUID docId);
    DocumentResponse triggerVectorize(UUID docId);
}

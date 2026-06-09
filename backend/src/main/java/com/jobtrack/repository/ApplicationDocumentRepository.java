package com.jobtrack.repository;

import com.jobtrack.entity.ApplicationDocument;
import com.jobtrack.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {
    List<ApplicationDocument> findByJobApplicationIdOrderByUploadedAtDesc(Long applicationId);
    Optional<ApplicationDocument> findByJobApplicationIdAndDocumentType(Long applicationId, DocumentType documentType);
}

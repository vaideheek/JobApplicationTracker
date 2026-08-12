package com.jobtrack.repository;

import com.jobtrack.entity.ApplicationDocument;
import com.jobtrack.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {

    List<ApplicationDocument> findByJobApplicationIdAndJobApplicationUserIdOrderByUploadedAtDesc(Long applicationId, Long userId);

    Optional<ApplicationDocument> findByIdAndJobApplicationUserId(Long id, Long userId);

    Optional<ApplicationDocument> findByJobApplicationIdAndDocumentTypeAndJobApplicationUserId(Long applicationId, DocumentType documentType, Long userId);
}

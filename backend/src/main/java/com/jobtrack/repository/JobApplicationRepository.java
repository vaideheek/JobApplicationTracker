package com.jobtrack.repository;

import com.jobtrack.entity.JobApplication;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.enums.ApplicationPriority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    List<JobApplication> findAllByUserId(Long userId);

    Optional<JobApplication> findByCompanyNameIgnoreCaseAndJobTitleIgnoreCaseAndUserId(String companyName, String jobTitle, Long userId);

    long countByStatusAndUserId(ApplicationStatus status, Long userId);

    long countByUserId(Long userId);

    long countByPriorityAndUserId(ApplicationPriority priority, Long userId);

    long countByDateAppliedAfterAndUserId(LocalDate date, Long userId);

    @Query("SELECT COUNT(j) FROM JobApplication j WHERE " +
           "j.user.id = :userId AND " +
           "j.status NOT IN (com.jobtrack.enums.ApplicationStatus.REJECTED, com.jobtrack.enums.ApplicationStatus.WITHDRAWN, com.jobtrack.enums.ApplicationStatus.NO_RESPONSE, com.jobtrack.enums.ApplicationStatus.OFFER) " +
           "AND j.followUpDate IS NOT NULL AND j.followUpDate <= :today")
    long countFollowUpNeeded(@Param("today") LocalDate today, @Param("userId") Long userId);

    @Query("SELECT COUNT(j) FROM JobApplication j WHERE " +
           "j.user.id = :userId AND " +
           "j.status = com.jobtrack.enums.ApplicationStatus.INTERVIEW " +
           "AND j.followUpDate IS NOT NULL AND j.followUpDate BETWEEN :today AND :endDate")
    long countUpcomingInterviews(@Param("today") LocalDate today, @Param("endDate") LocalDate endDate, @Param("userId") Long userId);

    @Query("SELECT COUNT(j) FROM JobApplication j WHERE " +
           "j.user.id = :userId AND " +
           "j.status NOT IN (com.jobtrack.enums.ApplicationStatus.REJECTED, com.jobtrack.enums.ApplicationStatus.WITHDRAWN, com.jobtrack.enums.ApplicationStatus.NO_RESPONSE, com.jobtrack.enums.ApplicationStatus.OFFER) " +
           "AND j.lastUpdatedAt <= :staleTime")
    long countStaleApplications(@Param("staleTime") LocalDateTime staleTime, @Param("userId") Long userId);

    @Query("SELECT COUNT(j) FROM JobApplication j WHERE " +
           "j.user.id = :userId AND " +
           "j.status NOT IN (com.jobtrack.enums.ApplicationStatus.REJECTED, com.jobtrack.enums.ApplicationStatus.WITHDRAWN, com.jobtrack.enums.ApplicationStatus.NO_RESPONSE, com.jobtrack.enums.ApplicationStatus.OFFER) " +
           "AND (" +
           "  (SELECT COUNT(d) FROM ApplicationDocument d WHERE d.jobApplication = j AND d.documentType = com.jobtrack.enums.DocumentType.CV) = 0 " +
           "  OR " +
           "  (SELECT COUNT(d) FROM ApplicationDocument d WHERE d.jobApplication = j AND d.documentType = com.jobtrack.enums.DocumentType.COVER_LETTER) = 0" +
           ")")
    long countApplicationsMissingDocuments(@Param("userId") Long userId);

    @Query("SELECT j FROM JobApplication j WHERE " +
           "j.user.id = :userId AND " +
           "j.status NOT IN (com.jobtrack.enums.ApplicationStatus.REJECTED, com.jobtrack.enums.ApplicationStatus.WITHDRAWN, com.jobtrack.enums.ApplicationStatus.NO_RESPONSE, com.jobtrack.enums.ApplicationStatus.OFFER)")
    List<JobApplication> findActiveApplications(@Param("userId") Long userId);

    @Query("SELECT j.companyName, COUNT(j) FROM JobApplication j WHERE j.user.id = :userId GROUP BY j.companyName ORDER BY COUNT(j) DESC")
    List<Object[]> findTopCompanies(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT j FROM JobApplication j WHERE " +
           "j.user.id = :userId AND " +
           "(:search IS NULL OR :search = '' OR " +
           " LOWER(j.companyName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(j.jobTitle) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(j.location) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(j.source) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR j.status = :status) " +
           "AND (:priority IS NULL OR j.priority = :priority) " +
           "AND (:dateFrom IS NULL OR j.dateApplied >= :dateFrom) " +
           "AND (:dateTo IS NULL OR j.dateApplied <= :dateTo) " +
           "AND (:documentState IS NULL OR :documentState = '' OR " +
           "      (:documentState = 'HAS_DOCUMENTS' AND j.documents IS NOT EMPTY) OR " +
           "      (:documentState = 'NO_DOCUMENTS' AND j.documents IS EMPTY))")
    Page<JobApplication> findWithFilters(
            @Param("search") String search,
            @Param("status") ApplicationStatus status,
            @Param("priority") ApplicationPriority priority,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("documentState") String documentState,
            @Param("userId") Long userId,
            Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE JobApplication j SET j.user = :newOwner WHERE j.user.id = :oldOwnerId")
    void reassignApplications(@Param("oldOwnerId") Long oldOwnerId, @Param("newOwner") com.jobtrack.entity.User newOwner);
}

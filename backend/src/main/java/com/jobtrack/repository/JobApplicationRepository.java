package com.jobtrack.repository;

import com.jobtrack.entity.JobApplication;
import com.jobtrack.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jobtrack.enums.ApplicationPriority;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    Optional<JobApplication> findByCompanyNameIgnoreCaseAndJobTitleIgnoreCase(String companyName, String jobTitle);

    long countByStatus(ApplicationStatus status);

    long countByPriority(ApplicationPriority priority);

    long countByDateAppliedAfter(LocalDate date);

    @Query("SELECT COUNT(j) FROM JobApplication j WHERE " +
           "j.status NOT IN (com.jobtrack.enums.ApplicationStatus.REJECTED, com.jobtrack.enums.ApplicationStatus.WITHDRAWN, com.jobtrack.enums.ApplicationStatus.OFFER) " +
           "AND j.followUpDate IS NOT NULL AND j.followUpDate <= :today")
    long countFollowUpNeeded(@Param("today") LocalDate today);

    @Query("SELECT COUNT(j) FROM JobApplication j WHERE " +
           "j.status = com.jobtrack.enums.ApplicationStatus.INTERVIEW " +
           "AND j.followUpDate IS NOT NULL AND j.followUpDate BETWEEN :today AND :endDate")
    long countUpcomingInterviews(@Param("today") LocalDate today, @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(j) FROM JobApplication j WHERE " +
           "j.status NOT IN (com.jobtrack.enums.ApplicationStatus.REJECTED, com.jobtrack.enums.ApplicationStatus.WITHDRAWN, com.jobtrack.enums.ApplicationStatus.OFFER) " +
           "AND j.lastUpdatedAt <= :staleTime")
    long countStaleApplications(@Param("staleTime") LocalDateTime staleTime);

    @Query("SELECT j FROM JobApplication j WHERE " +
           "j.status NOT IN (com.jobtrack.enums.ApplicationStatus.REJECTED, com.jobtrack.enums.ApplicationStatus.WITHDRAWN, com.jobtrack.enums.ApplicationStatus.OFFER)")
    List<JobApplication> findActiveApplications();

    @Query("SELECT j.companyName, COUNT(j) FROM JobApplication j GROUP BY j.companyName ORDER BY COUNT(j) DESC")
    List<Object[]> findTopCompanies(Pageable pageable);

    @Query("SELECT j FROM JobApplication j WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           " LOWER(j.companyName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(j.jobTitle) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR j.status = :status) " +
           "ORDER BY j.lastUpdatedAt DESC")
    Page<JobApplication> findWithFilters(
            @Param("search") String search,
            @Param("status") ApplicationStatus status,
            Pageable pageable);
}

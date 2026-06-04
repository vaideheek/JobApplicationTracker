package com.jobtrack.repository;

import com.jobtrack.entity.JobApplication;
import com.jobtrack.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    long countByStatus(ApplicationStatus status);

    long countByDateAppliedAfter(LocalDate date);

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

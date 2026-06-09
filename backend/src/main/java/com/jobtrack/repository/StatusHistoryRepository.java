package com.jobtrack.repository;

import com.jobtrack.entity.StatusHistory;
import com.jobtrack.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByJobApplicationIdOrderByChangedAtDesc(Long jobApplicationId);

    @Query("SELECT COUNT(DISTINCT s.jobApplication.id) FROM StatusHistory s WHERE s.toStatus = :status")
    long countDistinctApplicationsByToStatus(@Param("status") ApplicationStatus status);
}

package com.jobtrack.repository;

import com.jobtrack.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByJobApplicationIdOrderByChangedAtDesc(Long jobApplicationId);
}

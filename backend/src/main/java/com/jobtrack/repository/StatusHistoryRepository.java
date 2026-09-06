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

    @Query("SELECT COUNT(DISTINCT s.jobApplication.id) FROM StatusHistory s WHERE s.jobApplication.user.id = :userId AND s.toStatus = :status AND (s.fromStatus IS NULL OR s.fromStatus <> s.toStatus)")
    long countDistinctApplicationsByToStatusAndUserId(@Param("status") ApplicationStatus status, @Param("userId") Long userId);

    @Query("SELECT COUNT(DISTINCT s.jobApplication.id) FROM StatusHistory s WHERE s.jobApplication.user.id = :userId " +
           "AND s.toStatus IN :statuses " +
           "AND (s.fromStatus IS NULL OR s.fromStatus <> s.toStatus) " +
           "AND s.changedAt >= :start AND s.changedAt < :endExclusive " +
           "AND (s.note IS NULL OR (s.note != 'Bulk imported application' AND LOWER(s.note) NOT LIKE '%via bulk import%'))")
    long countDistinctResponsesInRange(
            @Param("userId") Long userId,
            @Param("statuses") java.util.Collection<ApplicationStatus> statuses,
            @Param("start") java.time.LocalDateTime start,
            @Param("endExclusive") java.time.LocalDateTime endExclusive);

    @Query("SELECT COUNT(DISTINCT s.jobApplication.id) FROM StatusHistory s WHERE s.jobApplication.user.id = :userId " +
           "AND s.toStatus = :status " +
           "AND (s.fromStatus IS NULL OR s.fromStatus <> s.toStatus) " +
           "AND s.changedAt >= :start AND s.changedAt < :endExclusive " +
           "AND (s.note IS NULL OR (s.note != 'Bulk imported application' AND LOWER(s.note) NOT LIKE '%via bulk import%'))")
    long countDistinctStatusInRange(
            @Param("userId") Long userId,
            @Param("status") ApplicationStatus status,
            @Param("start") java.time.LocalDateTime start,
            @Param("endExclusive") java.time.LocalDateTime endExclusive);

    @Query("SELECT COUNT(DISTINCT s.jobApplication.id) FROM StatusHistory s WHERE s.jobApplication.user.id = :userId " +
           "AND s.toStatus IN :statuses " +
           "AND (s.fromStatus IS NULL OR s.fromStatus <> s.toStatus) " +
           "AND (s.note IS NULL OR (s.note != 'Bulk imported application' AND LOWER(s.note) NOT LIKE '%via bulk import%'))")
    long countAllDistinctResponses(
            @Param("userId") Long userId,
            @Param("statuses") java.util.Collection<ApplicationStatus> statuses);

    @Query("SELECT COUNT(DISTINCT s.jobApplication.id) FROM StatusHistory s WHERE s.jobApplication.user.id = :userId " +
           "AND s.toStatus = :status " +
           "AND (s.fromStatus IS NULL OR s.fromStatus <> s.toStatus) " +
           "AND (s.note IS NULL OR (s.note != 'Bulk imported application' AND LOWER(s.note) NOT LIKE '%via bulk import%'))")
    long countAllDistinctStatus(
            @Param("userId") Long userId,
            @Param("status") ApplicationStatus status);
}

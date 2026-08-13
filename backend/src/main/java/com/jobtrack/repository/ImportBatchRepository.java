package com.jobtrack.repository;

import com.jobtrack.entity.ImportBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ib FROM ImportBatch ib WHERE ib.id = :id AND ib.user.id = :userId")
    Optional<ImportBatch> findByIdAndUserIdForUpdate(@Param("id") String id, @Param("userId") Long userId);

    Optional<ImportBatch> findByUserIdAndManifestHashAndState(Long userId, String manifestHash, String state);

    Optional<ImportBatch> findByIdAndUserId(String id, Long userId);
}

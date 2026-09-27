package com.jobtrack.repository;

import com.jobtrack.entity.JobApplication;
import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface JobApplicationRepositoryCustom {

    Page<JobApplication> findWithFilters(
            String search,
            ApplicationStatus status,
            ApplicationPriority priority,
            LocalDate dateFrom,
            LocalDate dateTo,
            String documentState,
            Long userId,
            Pageable pageable);
}

package com.jobtrack.entity;

import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "job_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String jobTitle;

    private String location;

    private String jobUrl;

    private LocalDate dateApplied;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    private String stage;

    private String recruiterEmail;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // --- New fields ---
    private String source;

    private String companyCareerUrl;

    private String salaryRange;

    @Enumerated(EnumType.STRING)
    private ApplicationPriority priority;

    private LocalDate followUpDate;

    private LocalDate deadlineDate;

    // --- Timestamps ---
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastUpdatedAt;

    // --- Relationships ---
    @OneToMany(mappedBy = "jobApplication", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt DESC")
    @Builder.Default
    private List<StatusHistory> statusHistory = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastUpdatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdatedAt = LocalDateTime.now();
    }
}

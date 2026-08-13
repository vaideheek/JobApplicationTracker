package com.jobtrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "import_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportBatch {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "manifest_hash", nullable = false)
    private String manifestHash;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private LocalDateTime expiry;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;
}

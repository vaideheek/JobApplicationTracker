package com.jobtrack.dto;

import com.jobtrack.enums.ApplicationStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusHistoryResponse {

    private Long id;
    private ApplicationStatus fromStatus;
    private ApplicationStatus toStatus;
    private LocalDateTime changedAt;
    private String note;
}

package com.jobtrack.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumeBucket {
    private String bucketStart;
    private String label;
    private long applicationsSubmitted;
}

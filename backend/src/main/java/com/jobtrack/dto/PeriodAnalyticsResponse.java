package com.jobtrack.dto;

import com.jobtrack.enums.PeriodRange;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodAnalyticsResponse {
    private PeriodRange range;
    private LocalDate from;
    private LocalDate to;
    private LocalDate previousFrom;
    private LocalDate previousTo;
    private long unknownDateApplications;
    private PeriodMetrics current;
    private PeriodMetrics previous;
    private List<VolumeBucket> volume;
    private String historyMetricNote;

    public boolean isComparisonAvailable() {
        return previous != null;
    }
}

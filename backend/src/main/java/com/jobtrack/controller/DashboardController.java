package com.jobtrack.controller;

import com.jobtrack.dto.CurrentPipelineResponse;
import com.jobtrack.dto.DashboardInsightsResponse;
import com.jobtrack.dto.DashboardStats;
import com.jobtrack.dto.PeriodAnalyticsResponse;
import com.jobtrack.enums.PeriodRange;
import com.jobtrack.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }

    @GetMapping("/insights")
    public ResponseEntity<DashboardInsightsResponse> getInsights() {
        return ResponseEntity.ok(dashboardService.getInsights());
    }

    @GetMapping("/pipeline")
    public ResponseEntity<CurrentPipelineResponse> getPipeline() {
        return ResponseEntity.ok(dashboardService.getCurrentPipeline());
    }

    @GetMapping("/period")
    public ResponseEntity<PeriodAnalyticsResponse> getPeriod(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean compare,
            @RequestParam(required = false) String timezone) {

        PeriodRange periodRange = PeriodRange.THIS_MONTH;
        if (range != null && !range.isBlank()) {
            try {
                periodRange = PeriodRange.valueOf(range.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unsupported period range: " + range);
            }
        }

        ZoneId zoneId = ZoneOffset.UTC;
        if (timezone != null && !timezone.isBlank()) {
            try {
                zoneId = ZoneId.of(timezone.trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid timezone: " + timezone);
            }
        }

        return ResponseEntity.ok(dashboardService.getPeriodAnalytics(periodRange, from, to, compare, zoneId));
    }
}

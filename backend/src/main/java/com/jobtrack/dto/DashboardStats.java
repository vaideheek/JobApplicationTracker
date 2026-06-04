package com.jobtrack.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStats {

    private long totalApplications;
    private long interviews;
    private long offers;
    private long rejections;
    private long applicationsThisWeek;
}

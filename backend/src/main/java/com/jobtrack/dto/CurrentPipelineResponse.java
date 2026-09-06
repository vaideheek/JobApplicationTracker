package com.jobtrack.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentPipelineResponse {
    private long applied;
    private long inReview;
    private long assessment;
    private long interview;
    private long offer;
    private long noResponse;
    private long rejected;
    private long withdrawn;
    private long totalActive;
}

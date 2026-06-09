package com.jobtrack.dto;

import com.jobtrack.enums.ApplicationPriority;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrioritySuggestionResponse {
    private ApplicationPriority priority;
    private String explanation;
}

package com.jobtrack.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkConfirmRequest {
    private String scanId;
    private List<BulkConfirmApplication> applications;
}

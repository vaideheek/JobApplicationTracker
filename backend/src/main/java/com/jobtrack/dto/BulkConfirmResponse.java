package com.jobtrack.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkConfirmResponse {
    private int successCount;
    private int failureCount;
    private List<DocumentFailureInfo> documentFailures;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentFailureInfo {
        private String fileName;
        private String error;
    }
}

package com.jobtrack.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkConfirmDocument {
    private String tempDocId;
    private String documentType;
}

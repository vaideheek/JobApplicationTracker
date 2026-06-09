package com.jobtrack.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailParseRequest {
    @NotBlank(message = "Email text cannot be empty")
    private String rawEmailText;
}

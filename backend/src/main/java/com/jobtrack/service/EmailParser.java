package com.jobtrack.service;

import com.jobtrack.dto.EmailParseResponse;

public interface EmailParser {
    EmailParseResponse parse(String rawText);
}

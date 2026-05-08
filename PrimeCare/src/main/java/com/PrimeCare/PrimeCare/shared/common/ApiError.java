package com.PrimeCare.PrimeCare.shared.common;

import lombok.*;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {
    private boolean success;
    private String code;
    private String message;
    private Instant timestamp;
    private int status;
    private String path;
    private Map<String, Object> details;
}

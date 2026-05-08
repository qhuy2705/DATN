package com.PrimeCare.PrimeCare.modules.encounter.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EncounterTimelineItemResponse {
    private String id;
    private String eventType;
    private String stage;
    private String title;
    private String description;
    private LocalDateTime occurredAt;
    private String actorName;
    private String status;
    private String referenceCode;
}

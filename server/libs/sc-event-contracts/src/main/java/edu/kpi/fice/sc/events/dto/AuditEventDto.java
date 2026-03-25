package edu.kpi.fice.sc.events.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventDto(
    UUID id,
    String sourceService,
    String actorId,
    String actorType,
    String eventType,
    String objectType,
    String objectId,
    Map<String, Object> payload,
    Instant occurredAt,
    Map<String, Object> context) {}

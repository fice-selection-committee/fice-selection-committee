package edu.kpi.fice.sc.events.dto;

public record ApplicationEventDto(
    Long applicationId,
    Long applicantUserId,
    String status,
    Long operatorUserId,
    String rejectionReason,
    String eventType) {}

package edu.kpi.fice.telegram_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FeatureFlagResponse(
    String key,
    Boolean enabled,
    String description,
    String flagType,
    String owner,
    String status,
    Integer rolloutPercentage,
    String expiresAt) {}

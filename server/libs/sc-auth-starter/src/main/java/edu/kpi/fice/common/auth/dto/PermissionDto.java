package edu.kpi.fice.common.auth.dto;

import lombok.Builder;

@Builder
public record PermissionDto(Long id, String name) {}

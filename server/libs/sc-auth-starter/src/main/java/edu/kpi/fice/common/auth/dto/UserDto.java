package edu.kpi.fice.common.auth.dto;

import java.util.Set;

public record UserDto(
    Long id,
    String email,
    RoleDto role,
    String firstName,
    String middleName,
    String lastName,
    Set<PermissionDto> extraPermissions) {}

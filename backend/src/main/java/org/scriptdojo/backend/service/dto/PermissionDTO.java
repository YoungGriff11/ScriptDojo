package org.scriptdojo.backend.service.dto;

import java.time.LocalDateTime;

public record PermissionDTO(
        Long id,
        Long fileId,
        String identifier,  // Either "Guest1234" or "User_1000"
        String role,        // "VIEW" or "EDIT"
        boolean canEdit,
        boolean isGuest,
        LocalDateTime grantedAt
) {}
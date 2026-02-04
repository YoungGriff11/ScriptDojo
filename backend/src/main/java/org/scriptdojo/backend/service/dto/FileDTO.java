package org.scriptdojo.backend.service.dto;

import java.time.LocalDateTime;

public record FileDTO(
        Long id,
        String name,
        String content,
        String language,
        Long ownerId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
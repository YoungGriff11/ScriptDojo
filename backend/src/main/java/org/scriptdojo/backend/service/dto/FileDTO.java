package org.scriptdojo.backend.service.dto;

public record FileDTO(
        Long id,
        String name,
        String content,
        String language,
        Long ownerId,
        String ownerUsername  // optional – for display in dashboard/guest
) {}

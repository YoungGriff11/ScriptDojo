package org.scriptdojo.backend.service.dto;

import java.time.LocalDateTime;

/**
 * Immutable record used to transfer file data between the service layer and
 * API responses without exposing the internal {@link org.scriptdojo.backend.entity.FileEntity}
 * structure or its JPA associations (e.g. the owner relationship).
 * Returned by all endpoints in {@link org.scriptdojo.backend.controller.FileController}
 * and produced by {@link org.scriptdojo.backend.service.FileService#getFileDTOById}.
 */
public record FileDTO(
        Long id, //the unique database identifier of the file
        String name, //the display name of the file (e.g. "Main.java")
        String content, //the full source code content of the file
        String language, //the programming language of the file (e.g. "java")
        Long ownerId, //the programming language of the file (e.g. "java")
        LocalDateTime createdAt, //the database ID of the user who owns this file
        LocalDateTime updatedAt  //the timestamp of the most recent update to the file
) {}
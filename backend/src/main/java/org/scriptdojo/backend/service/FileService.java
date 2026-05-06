package org.scriptdojo.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.entity.UserEntity;
import org.scriptdojo.backend.repository.FileEntityRepository;
import org.scriptdojo.backend.repository.UserRepository;
import org.scriptdojo.backend.service.dto.FileDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for all file management operations in ScriptDojo.
 * Acts as the primary intermediary between {@link org.scriptdojo.backend.controller.FileController}
 * and the persistence layer, handling file creation, retrieval, content updates,
 * metadata updates, renaming, and deletion.
 * Also called by {@link org.scriptdojo.backend.controller.CollaborationController}
 * on every real-time edit to persist the latest editor content to the database.
 * Deletion cascades to permission records via {@link PermissionService} to ensure
 * no orphaned permission entries remain after a file is removed.
 */
@Service
@RequiredArgsConstructor // Lombok: generates constructor injecting all final fields
@Slf4j
public class FileService {

    // Handles all FileEntity persistence and owner-scoped queries
    private final FileEntityRepository fileRepository;

    // Used during file creation to resolve the owner UserEntity by ID
    private final UserRepository userRepository;

    // Used during file deletion to clean up associated permission records
    private final PermissionService permissionService;

    /**
     * Returns all files owned by the given user, ordered by most recently updated first.
     * Results are mapped to DTOs so the internal entity structure is not exposed
     * directly to the controller layer.
     * @param userId the database ID of the user whose files should be returned
     * @return a list of {@link FileDTO} records, may be empty if the user has no files
     */
    public List<FileDTO> getUserFiles(Long userId) {
        List<FileEntity> files = fileRepository.findByOwnerIdOrderByUpdatedAtDesc(userId);
        return files.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Returns the {@link FileEntity} for the given ID.
     * Throws a RuntimeException if no file exists with that ID, which propagates
     * as a 500 response — a @ControllerAdvice mapping to 404 would be preferable.
     *
     * @param id the database ID of the file to retrieve
     * @return the matching FileEntity
     * @throws RuntimeException if no file exists with the given ID
     */
    public FileEntity getFileById(Long id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found with id: " + id));
    }

    /**
     * Returns the file with the given ID as a {@link FileDTO}.
     * Delegates to {@link #getFileById} and maps the result via {@link #toDTO}.
     * @param id the database ID of the file to retrieve
     * @return the matching file as a DTO
     */
    public FileDTO getFileDTOById(Long id) {
        FileEntity file = getFileById(id);
        return toDTO(file);
    }

    /**
     * Creates and persists a new file owned by the given user.
     * Resolves the owner UserEntity by ID before building the FileEntity so
     * that the owner association is correctly populated on the saved entity.
     * @param name     the display name of the new file
     * @param content  the initial source code content of the file
     * @param language the programming language of the file (e.g. "java")
     * @param ownerId  the database ID of the user who will own the file
     * @return the persisted {@link FileEntity} with its generated ID populated
     * @throws RuntimeException if no user exists with the given ownerId
     */
    @Transactional
    public FileEntity createFile(String name, String content, String language, Long ownerId) {
        UserEntity owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + ownerId));

        FileEntity file = FileEntity.builder()
                .name(name)
                .content(content)
                .language(language)
                .owner(owner)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        FileEntity saved = fileRepository.save(file);
        log.info("✅ File created: ID={}, Name={}", saved.getId(), saved.getName());

        return saved;
    }

    /**
     * Replaces the content of an existing file and updates its timestamp.
     * Called on every real-time edit by CollaborationController to persist
     * the latest editor state, as well as directly by FileController for
     * explicit content update requests.
     * @param id      the database ID of the file to update
     * @param content the complete replacement content for the file
     * @return the updated and persisted {@link FileEntity}
     */
    @Transactional
    public FileEntity updateFileContent(Long id, String content) {
        log.info("💾 Updating file ID={}", id);
        log.info("   New content length: {} characters", content.length());

        FileEntity file = getFileById(id);
        int oldLength = file.getContent() != null ? file.getContent().length() : 0;

        file.setContent(content);
        file.setUpdatedAt(LocalDateTime.now());

        FileEntity updated = fileRepository.save(file);

        log.info("✅ File updated successfully");
        log.info("   Old length: {} → New length: {}", oldLength, content.length());

        return updated;
    }

    /**
     * Renames an existing file and updates its timestamp.
     * @param id      the database ID of the file to rename
     * @param newName the replacement name for the file
     * @return the updated and persisted {@link FileEntity}
     */
    @Transactional
    public FileEntity renameFile(Long id, String newName) {
        FileEntity file = getFileById(id);

        String oldName = file.getName();
        file.setName(newName);
        file.setUpdatedAt(LocalDateTime.now());

        FileEntity updated = fileRepository.save(file);
        log.info("📝 File renamed: '{}' → '{}'", oldName, newName);

        return updated;
    }

    /**
     * Deletes a file and all of its associated permission records.
     * Permission cleanup is performed first via PermissionService to ensure
     * no orphaned records remain in the permission table after deletion.
     * @param id the database ID of the file to delete
     */
    @Transactional
    public void deleteFile(Long id) {
        FileEntity file = getFileById(id);

        // Remove all guest and user permission records for this file before
        // deleting the file itself to avoid orphaned permission entries
        permissionService.deleteFilePermissions(id);

        fileRepository.delete(file);
        log.info("🗑️ File deleted: ID={}, Name={}", id, file.getName());
    }

    /**
     * Returns true if the given user is the owner of the given file.
     * Used by controllers to enforce ownership before mutating operations.
     * @param fileId the database ID of the file to check
     * @param userId the database ID of the user to verify ownership for
     * @return true if the user owns the file, false otherwise
     */
    public boolean isOwner(Long fileId, Long userId) {
        FileEntity file = getFileById(fileId);
        return file.getOwner().getId().equals(userId);
    }

    /**
     * Returns the database ID of the user who owns the given file.
     * @param fileId the database ID of the file to query
     * @return the owner's user ID
     */
    public Long getFileOwnerId(Long fileId) {
        FileEntity file = getFileById(fileId);
        return file.getOwner().getId();
    }

    /**
     * Updates the name and/or language of an existing file.
     * Only non-blank values are applied — null or blank fields in the request
     * are ignored, leaving the existing values unchanged.
     * @param id       the database ID of the file to update
     * @param name     the new display name, or null/blank to leave unchanged
     * @param language the new language identifier, or null/blank to leave unchanged
     * @return the updated and persisted {@link FileEntity}
     */
    @Transactional
    public FileEntity updateFileMetadata(Long id, String name, String language) {
        FileEntity file = getFileById(id);

        if (name != null && !name.isBlank()) {
            file.setName(name);
        }

        if (language != null && !language.isBlank()) {
            file.setLanguage(language);
        }

        file.setUpdatedAt(LocalDateTime.now());

        return fileRepository.save(file);
    }

    /**
     * Converts a {@link FileEntity} to a {@link FileDTO} for API responses.
     * Extracts the owner ID from the owner association rather than exposing
     * the full UserEntity, keeping the DTO free of JPA-managed references.
     * @param entity the file entity to convert
     * @return a DTO representation safe for serialisation in API responses
     */
    private FileDTO toDTO(FileEntity entity) {
        return new FileDTO(
                entity.getId(),
                entity.getName(),
                entity.getContent(),
                entity.getLanguage(),
                entity.getOwner().getId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
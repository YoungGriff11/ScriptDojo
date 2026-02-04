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

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileEntityRepository fileRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    /**
     * Get all files for a specific user
     */
    public List<FileDTO> getUserFiles(Long userId) {
        List<FileEntity> files = fileRepository.findByOwnerIdOrderByUpdatedAtDesc(userId);
        return files.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get a single file by ID
     */
    public FileEntity getFileById(Long id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found with id: " + id));
    }

    /**
     * Get file as DTO
     */
    public FileDTO getFileDTOById(Long id) {
        FileEntity file = getFileById(id);
        return toDTO(file);
    }

    /**
     * Create a new file
     */
    @Transactional
    public FileEntity createFile(String name, String content, String language, Long ownerId) {
        // Fetch the owner UserEntity
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
     * Update file content
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
     * Rename a file
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
     * Delete a file (with permission cleanup)
     */
    @Transactional
    public void deleteFile(Long id) {
        FileEntity file = getFileById(id);

        // Clean up all permissions for this file first
        permissionService.deleteFilePermissions(id);

        fileRepository.delete(file);

        log.info("🗑️ File deleted: ID={}, Name={}", id, file.getName());
    }

    /**
     * Check if user owns a file
     */
    public boolean isOwner(Long fileId, Long userId) {
        FileEntity file = getFileById(fileId);
        return file.getOwner().getId().equals(userId);
    }

    /**
     * Get file owner ID
     */
    public Long getFileOwnerId(Long fileId) {
        FileEntity file = getFileById(fileId);
        return file.getOwner().getId();
    }

    /**
     * Update file metadata (name, language)
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
     * Convert entity to DTO
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
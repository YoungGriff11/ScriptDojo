package org.scriptdojo.backend.service;

import org.scriptdojo.backend.service.dto.FileDTO;
import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.repository.FileEntityRepository;
import org.scriptdojo.backend.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileEntityRepository fileRepository;

    private CustomUserDetails getCurrentUser() {
        return (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    public List<FileEntity> getUserFiles() {
        Long userId = getCurrentUser().getId();
        return fileRepository.findByOwnerIdOrderByUpdatedAtDesc(userId);
    }

    public FileEntity getFileById(Long id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
    }

    @Transactional
    public FileEntity createFile(String name, String content, String language) {
        FileEntity file = new FileEntity();
        file.setName(name);
        file.setContent(content != null ? content : "");
        file.setLanguage(language != null ? language : "java");
        file.setOwner(getCurrentUser().getUser());
        file.setCreatedAt(LocalDateTime.now());
        file.setUpdatedAt(LocalDateTime.now());

        FileEntity saved = fileRepository.save(file);
        log.info("✅ File created: ID={}, Name={}", saved.getId(), saved.getName());

        return saved;
    }

    @Transactional
    public FileEntity updateFile(Long id, String newContent) {
        log.info("💾 Updating file ID={}", id);
        log.info("   New content length: {} characters", newContent != null ? newContent.length() : 0);

        FileEntity file = getFileById(id);

        String oldContent = file.getContent();
        file.setContent(newContent != null ? newContent : "");
        file.setUpdatedAt(LocalDateTime.now());

        FileEntity saved = fileRepository.save(file);

        log.info("✅ File updated successfully");
        log.info("   Old length: {} → New length: {}",
                oldContent != null ? oldContent.length() : 0,
                saved.getContent().length());

        return saved;
    }

    @Transactional
    public FileEntity updateFile(Long id, String newName, String newContent) {
        FileEntity file = getFileById(id);

        if (newName != null && !newName.isBlank()) {
            file.setName(newName);
        }
        if (newContent != null) {
            file.setContent(newContent);
        }
        file.setUpdatedAt(LocalDateTime.now());

        return fileRepository.save(file);
    }

    @Transactional
    public void deleteFile(Long id) {
        FileEntity file = getFileById(id);
        fileRepository.delete(file);
        log.info("🗑️ File deleted: ID={}, Name={}", id, file.getName());
    }

    public FileDTO toDTO(FileEntity entity) {
        return new FileDTO(
                entity.getId(),
                entity.getName(),
                entity.getContent(),
                entity.getLanguage(),
                entity.getOwner().getId(),
                entity.getOwner().getUsername()
        );
    }
}
package org.scriptdojo.backend.service;

import lombok.RequiredArgsConstructor;
import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.entity.UserEntity;
import org.scriptdojo.backend.repository.FileEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileEntityRepository fileRepository;
    private final UserService userService;

    public List<FileEntity> getMyFiles() {
        UserEntity currentUser = userService.getCurrentUser();
        return fileRepository.findByOwnerOrderByUpdatedAtDesc(currentUser);
    }

    public FileEntity createFile(String name, String content, String language) {
        UserEntity owner = userService.getCurrentUser();
        FileEntity file = FileEntity.builder()
                .name(name)
                .content(content != null ? content : "")
                .language(language != null ? language : "java")
                .owner(owner)
                .build();
        return fileRepository.save(file);
    }

    public FileEntity getFile(Long id) {
        FileEntity file = fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found: " + id));

        if (!file.getOwner().equals(userService.getCurrentUser())) {
            throw new RuntimeException("Access denied");
        }
        return file;
    }

    // 1. FOR COLLABORATION (real-time editing in room)
    public FileEntity updateFile(Long fileId, String content) {
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        file.setContent(content);
        file.setUpdatedAt(LocalDateTime.now());
        return fileRepository.save(file);
    }

    // 2. FOR REST API (rename + update content from dashboard)
    public FileEntity updateFile(Long fileId, String name, String content) {
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        file.setName(name);
        file.setContent(content);
        file.setUpdatedAt(LocalDateTime.now());
        return fileRepository.save(file);
    }

    public void deleteFile(Long id) {
        FileEntity file = getFile(id);
        fileRepository.delete(file);
    }

    public FileEntity getFileById(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found with id: " + fileId));
    }

    public FileEntity getFileByIdAndOwner(Long fileId, Long ownerId) {
        return fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have access to this file"));
    }
}
package org.scriptdojo.backend.service;

import lombok.RequiredArgsConstructor;
import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.entity.UserEntity;
import org.scriptdojo.backend.repository.FileEntityRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor  // ← THIS WAS MISSING
public class FileService {

    private final FileEntityRepository fileRepository;
    private final UserService userService;

    public List<FileEntity> getMyFiles() {
        UserEntity currentUser = userService.getCurrentUser();
        return fileRepository.findByOwnerOrderByUpdatedAtDesc(currentUser);
    }

    public FileEntity createFile(String name, String content) {
        UserEntity owner = userService.getCurrentUser();
        FileEntity file = FileEntity.builder()
                .name(name)
                .content(content != null ? content : "")
                .owner(owner)
                .build();
        return fileRepository.save(file);
    }

    public FileEntity getFile(Long id) {
        FileEntity file = fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found: " + id));
        // Optional: check ownership
        if (!file.getOwner().equals(userService.getCurrentUser())) {
            throw new RuntimeException("Access denied");
        }
        return file;
    }

    @Transactional
    public FileEntity updateFile(Long id, String name, String content) {
        FileEntity file = getFile(id);
        if (name != null && !name.isBlank()) file.setName(name);
        if (content != null) file.setContent(content);
        return fileRepository.save(file);
    }

    public void deleteFile(Long id) {
        FileEntity file = getFile(id);
        fileRepository.delete(file);
    }
}
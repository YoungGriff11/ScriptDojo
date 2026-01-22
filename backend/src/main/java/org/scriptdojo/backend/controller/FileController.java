package org.scriptdojo.backend.controller;

import org.scriptdojo.backend.service.dto.FileDTO;
import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @GetMapping
    public ResponseEntity<List<FileDTO>> getUserFiles() {
        List<FileEntity> files = fileService.getUserFiles();
        List<FileDTO> dtos = files.stream()
                .map(fileService::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileDTO> getFile(@PathVariable Long id) {
        FileEntity file = fileService.getFileById(id);
        return ResponseEntity.ok(fileService.toDTO(file));
    }

    @PostMapping
    public ResponseEntity<FileDTO> createFile(@RequestBody FileDTO dto) {
        FileEntity created = fileService.createFile(dto.name(), dto.content(), dto.language());
        return ResponseEntity.ok(fileService.toDTO(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FileDTO> updateFile(@PathVariable Long id, @RequestBody FileDTO dto) {
        FileEntity updated = fileService.updateFile(id, dto.name(), dto.content());
        return ResponseEntity.ok(fileService.toDTO(updated));
    }

    @PutMapping("/{id}/content")
    public ResponseEntity<FileDTO> updateContent(@PathVariable Long id, @RequestBody String newContent) {
        FileEntity updated = fileService.updateFile(id, newContent);
        return ResponseEntity.ok(fileService.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return ResponseEntity.noContent().build();
    }
}
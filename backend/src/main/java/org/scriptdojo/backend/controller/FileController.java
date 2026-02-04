package org.scriptdojo.backend.controller;

import org.scriptdojo.backend.service.dto.FileDTO;
import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.service.FileService;
import org.scriptdojo.backend.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * Get all files for the current user
     * GET /api/files
     */
    @GetMapping
    public ResponseEntity<List<FileDTO>> getUserFiles(Authentication auth) {
        // Extract user ID from authentication
        Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();

        // Use the service method that returns DTOs directly
        List<FileDTO> files = fileService.getUserFiles(userId);

        return ResponseEntity.ok(files);
    }

    /**
     * Get a single file by ID
     * GET /api/files/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<FileDTO> getFile(@PathVariable Long id) {
        // Use the service method that returns DTO directly
        FileDTO file = fileService.getFileDTOById(id);

        return ResponseEntity.ok(file);
    }

    /**
     * Create a new file
     * POST /api/files
     */
    @PostMapping
    public ResponseEntity<FileDTO> createFile(
            @RequestBody FileDTO dto,
            Authentication auth
    ) {
        // Extract user ID from authentication
        Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();

        // Create the file
        FileEntity created = fileService.createFile(
                dto.name(),
                dto.content(),
                dto.language(),
                userId
        );

        // Convert to DTO and return
        FileDTO responseDto = fileService.getFileDTOById(created.getId());

        return ResponseEntity.ok(responseDto);
    }

    /**
     * Update file metadata (name and/or language)
     * PUT /api/files/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<FileDTO> updateFile(
            @PathVariable Long id,
            @RequestBody FileDTO dto
    ) {
        // Update metadata
        FileEntity updated = fileService.updateFileMetadata(id, dto.name(), dto.language());

        // Convert to DTO and return
        FileDTO responseDto = fileService.getFileDTOById(updated.getId());

        return ResponseEntity.ok(responseDto);
    }

    /**
     * Update only file content
     * PUT /api/files/{id}/content
     */
    @PutMapping("/{id}/content")
    public ResponseEntity<FileDTO> updateContent(
            @PathVariable Long id,
            @RequestBody String newContent
    ) {
        // Update content only
        FileEntity updated = fileService.updateFileContent(id, newContent);

        // Convert to DTO and return
        FileDTO responseDto = fileService.getFileDTOById(updated.getId());

        return ResponseEntity.ok(responseDto);
    }

    /**
     * Rename a file
     * PUT /api/files/{id}/rename
     */
    @PutMapping("/{id}/rename")
    public ResponseEntity<FileDTO> renameFile(
            @PathVariable Long id,
            @RequestParam String newName
    ) {
        FileEntity updated = fileService.renameFile(id, newName);

        FileDTO responseDto = fileService.getFileDTOById(updated.getId());

        return ResponseEntity.ok(responseDto);
    }

    /**
     * Delete a file
     * DELETE /api/files/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);

        return ResponseEntity.noContent().build();
    }
}
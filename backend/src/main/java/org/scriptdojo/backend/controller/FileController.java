package org.scriptdojo.backend.controller;

import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.scriptdojo.backend.entity.UserEntity;


@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class FileController {

    private final FileService fileService;

    @GetMapping
    public List<FileEntity> getMyFiles() {
        return fileService.getMyFiles();
    }

    @PostMapping
    public FileEntity createFile(@RequestBody CreateFileRequest req) {
        return fileService.createFile(req.name(), req.content());
    }

    @GetMapping("/{id}")
    public FileEntity getFile(@PathVariable Long id) {
        return fileService.getFile(id);
    }

    @PutMapping("/{id}")
    public FileEntity updateFile(@PathVariable Long id, @RequestBody UpdateFileRequest req) {
        return fileService.updateFile(id, req.name(), req.content());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return ResponseEntity.ok().build();
    }

    public record CreateFileRequest(String name, String content) {}
    public record UpdateFileRequest(String name, String content) {}
}

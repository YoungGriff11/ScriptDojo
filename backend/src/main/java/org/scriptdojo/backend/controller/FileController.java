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

/**
 * REST controller for file management operations in ScriptDojo.
 *
 * Provides standard CRUD endpoints for the files owned by the authenticated user.
 * All endpoints require authentication (enforced by SecurityConfig) and operate
 * on the currently logged-in user's files unless a file ID is supplied directly.
 *
 * A consistent two-step pattern is used for mutating operations (create, update,
 * rename): the service method returns a FileEntity after persistence, which is
 * then immediately re-fetched as a FileDTO via getFileDTOById(). This ensures the
 * response always reflects the fully populated database state (including any
 * server-side defaults or computed fields) rather than the raw entity.
 *
 * Base path: /api/files
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor // Lombok: generates constructor injecting fileService
public class FileController {

    // Handles all file persistence, retrieval, and transformation to DTOs
    private final FileService fileService;

    /**
     * Returns all files belonging to the currently authenticated user.
     *
     * GET /api/files
     *
     * @param auth the Spring Security authentication object; principal is cast to
     *             CustomUserDetails to access the internal user ID
     * @return 200 OK with the list of the user's files as DTOs (may be empty)
     */
    @GetMapping
    public ResponseEntity<List<FileDTO>> getUserFiles(Authentication auth) {
        // Cast the principal to CustomUserDetails to retrieve the database user ID.
        // This cast is safe because CustomUserDetailsService always produces
        // CustomUserDetails instances for authenticated sessions.
        Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();

        List<FileDTO> files = fileService.getUserFiles(userId);

        return ResponseEntity.ok(files);
    }

    /**
     * Returns a single file by its ID.
     *
     * GET /api/files/{id}
     *
     * @param id the database ID of the file to retrieve
     * @return 200 OK with the file as a DTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<FileDTO> getFile(@PathVariable Long id) {
        FileDTO file = fileService.getFileDTOById(id);

        return ResponseEntity.ok(file);
    }

    /**
     * Creates a new file for the currently authenticated user.
     *
     * The file is created with the name, content, and language supplied in the
     * request body. The response is re-fetched from the database after creation
     * to include any server-assigned fields (e.g. generated ID, creation timestamp).
     *
     * POST /api/files
     *
     * @param dto  the file data to create; name, content, and language are read
     *             from this payload (id and other read-only fields are ignored)
     * @param auth the Spring Security authentication object used to associate
     *             the new file with the correct user account
     * @return 200 OK with the newly created file as a fully populated DTO
     */
    @PostMapping
    public ResponseEntity<FileDTO> createFile(
            @RequestBody FileDTO dto,
            Authentication auth
    ) {
        // Resolve the owner ID from the authenticated principal so the file is
        // associated with the correct user record in the database
        Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();

        FileEntity created = fileService.createFile(
                dto.name(),
                dto.content(),
                dto.language(),
                userId
        );

        // Re-fetch as DTO to include all server-populated fields in the response
        FileDTO responseDto = fileService.getFileDTOById(created.getId());

        return ResponseEntity.ok(responseDto);
    }

    /**
     * Updates the metadata (name and/or language) of an existing file.
     * File content is not modified by this endpoint — use PUT /api/files/{id}/content
     * for content-only updates.
     *
     * PUT /api/files/{id}
     *
     * @param id  the database ID of the file to update
     * @param dto the updated metadata; only name and language fields are applied
     * @return 200 OK with the updated file as a fully populated DTO
     */
    @PutMapping("/{id}")
    public ResponseEntity<FileDTO> updateFile(
            @PathVariable Long id,
            @RequestBody FileDTO dto
    ) {
        FileEntity updated = fileService.updateFileMetadata(id, dto.name(), dto.language());

        // Re-fetch as DTO to reflect the persisted state including unchanged fields
        FileDTO responseDto = fileService.getFileDTOById(updated.getId());

        return ResponseEntity.ok(responseDto);
    }

    /**
     * Replaces the content of an existing file without modifying its metadata.
     *
     * This endpoint is also called internally by CollaborationController on every
     * real-time edit to persist the latest editor state to the database.
     *
     * PUT /api/files/{id}/content
     *
     * @param id         the database ID of the file to update
     * @param newContent the complete replacement content for the file
     * @return 200 OK with the updated file as a fully populated DTO
     */
    @PutMapping("/{id}/content")
    public ResponseEntity<FileDTO> updateContent(
            @PathVariable Long id,
            @RequestBody String newContent
    ) {
        FileEntity updated = fileService.updateFileContent(id, newContent);

        // Re-fetch as DTO to include the persisted content and any updated timestamps
        FileDTO responseDto = fileService.getFileDTOById(updated.getId());

        return ResponseEntity.ok(responseDto);
    }

    /**
     * Renames an existing file.
     *
     * Separated from the general metadata update endpoint to allow the frontend
     * to trigger a rename-specific action (e.g. inline rename in the file tree)
     * without constructing a full FileDTO payload.
     *
     * PUT /api/files/{id}/rename
     *
     * @param id      the database ID of the file to rename
     * @param newName the replacement name for the file, supplied as a query parameter
     * @return 200 OK with the renamed file as a fully populated DTO
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
     * Permanently deletes a file by its ID.
     *
     * DELETE /api/files/{id}
     *
     * @param id the database ID of the file to delete
     * @return 204 No Content on successful deletion
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);

        // 204 No Content is the conventional response for a successful DELETE —
        // there is no resource left to return in the body
        return ResponseEntity.noContent().build();
    }
}
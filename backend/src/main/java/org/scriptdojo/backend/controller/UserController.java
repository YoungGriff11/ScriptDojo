package org.scriptdojo.backend.controller;

import lombok.RequiredArgsConstructor;
import org.scriptdojo.backend.entity.UserEntity;
import org.scriptdojo.backend.service.UserService;
import org.scriptdojo.backend.service.dto.UserInfoDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes profile information for the currently
 * authenticated user.
 * Used by the React frontend on initial load to identify the logged-in user
 * (display name, email) without requiring the full user entity to be exposed.
 * The response is intentionally scoped to non-sensitive fields via
 * {@link UserInfoDTO} — password hash and role are never included.
 * Base path: /api/user
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor // Lombok: generates constructor injecting userService
public class UserController {

    // Resolves the current user from the Spring Security context and the database
    private final UserService userService;

    /**
     * Returns profile information for the currently authenticated user.
     * Delegates to {@link UserService#getCurrentUser()}, which resolves the
     * authenticated username from the Spring Security context and loads the
     * corresponding {@link UserEntity} from the database.
     * The response is mapped to a {@link UserInfoDTO} containing only the
     * fields the frontend needs (ID, username, email). Sensitive fields such
     * as the password hash and role are deliberately excluded.
     * GET /api/user/me
     * @return 200 OK with the authenticated user's ID, username, and email
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfoDTO> getCurrentUser() {
        UserEntity user = userService.getCurrentUser();

        // Map to DTO — exposes only the fields safe and necessary for the frontend.
        // The UserEntity itself is never serialised directly to avoid leaking
        // the password hash or internal role representation.
        return ResponseEntity.ok(new UserInfoDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail()
        ));
    }
}
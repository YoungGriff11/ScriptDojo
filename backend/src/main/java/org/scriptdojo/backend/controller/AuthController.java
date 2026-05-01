package org.scriptdojo.backend.controller;

import org.scriptdojo.backend.service.dto.RegisterRequest;
import org.scriptdojo.backend.entity.UserEntity;
import org.scriptdojo.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller handling user authentication operations.
 *
 * Currently exposes a single endpoint for new user registration.
 * Login and logout are handled entirely by Spring Security's built-in
 * form login mechanism configured in SecurityConfig — no controller
 * endpoint is required for those flows.
 *
 * Base path: /api/auth (permitted without authentication in SecurityConfig)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor // Lombok: generates constructor injecting userService
@CrossOrigin(origins = "http://localhost:8080") // Allows requests from the Docker/production origin
@Transactional // All public methods run within a database transaction by default
public class AuthController {

    // Handles user lookup and persistence; also applies BCrypt hashing before saving
    private final UserService userService;

    /**
     * Registers a new user account.
     *
     * Validates the incoming request body against the constraints defined in
     * {@link RegisterRequest} (e.g. non-blank username, valid email format),
     * checks that the chosen username is not already taken, then persists the
     * new account with a hashed password and a default role of USER.
     *
     * POST /api/auth/register
     *
     * @param request the registration payload containing username, password, and email;
     *                validated by Jakarta Bean Validation before this method is invoked
     * @return 200 OK with a confirmation message if registration succeeds,
     *         400 Bad Request with an error message if the username is already taken
     */
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {

        // Reject the request early if the username is already in use.
        // Checked before building the entity to avoid unnecessary object creation.
        if (userService.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.badRequest()
                    .body("Error: Username '" + request.username() + "' is already taken.");
        }

        // Build the new user entity from the validated request fields.
        // Password hashing (BCrypt) is applied inside userService.saveUser(),
        // so the plain-text password is never written to the database directly.
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPassword(request.password()); // Hashed by UserService before persistence
        user.setEmail(request.email());
        user.setRole("USER"); // All self-registered accounts receive the default USER role

        userService.saveUser(user);

        return ResponseEntity.ok("User registered: " + request.username());
    }
}
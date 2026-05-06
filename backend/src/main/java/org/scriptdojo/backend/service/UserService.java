package org.scriptdojo.backend.service;

import lombok.RequiredArgsConstructor;
import org.scriptdojo.backend.entity.UserEntity;
import org.scriptdojo.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Service responsible for user account operations in ScriptDojo.
 * Handles new account persistence and current-user resolution.
 * Password hashing is applied here rather than in the controller layer so
 * that plain-text passwords are never written to the database directly.
 * Note: UserDetailsService is intentionally not implemented here — that
 * responsibility is delegated to
 * {@link org.scriptdojo.backend.security.CustomUserDetailsService} to keep
 * Spring Security concerns separate from general user management.
 */
@Service
@RequiredArgsConstructor // Lombok: generates constructor injecting userRepository and passwordEncoder
public class UserService {

    // Handles UserEntity persistence and username-based lookups
    private final UserRepository userRepository;

    // BCrypt encoder used to hash passwords before persistence
    private final PasswordEncoder passwordEncoder;

    /**
     * Hashes the user's plain-text password and persists the account.
     * Called by {@link org.scriptdojo.backend.controller.AuthController} during
     * registration after the username uniqueness check passes.
     * @param user the UserEntity to persist; its password field must contain
     *             the plain-text password at the time of the call
     * @return the persisted {@link UserEntity} with the hashed password and generated ID
     */
    @Transactional
    public UserEntity saveUser(UserEntity user) {
        // Replace the plain-text password with its BCrypt hash before saving —
        // the raw password is never written to the database
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    /**
     * Looks up a user account by username.
     * Used by {@link org.scriptdojo.backend.controller.AuthController} during
     * registration to check whether the chosen username is already taken, and
     * by {@link org.scriptdojo.backend.security.CustomUserDetailsService} during
     * authentication to load the account for credential verification.
     * @param username the username to search for
     * @return an Optional containing the matching UserEntity, or empty if not found
     */
    public Optional<UserEntity> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Returns the {@link UserEntity} for the currently authenticated user.
     * Resolves the username from the Spring Security context and performs a
     * database lookup — safe to call from any authenticated request context.
     * Used by {@link org.scriptdojo.backend.controller.UserController} to
     * serve the GET /api/user/me endpoint.
     * @return the UserEntity of the currently authenticated user
     * @throws RuntimeException if the authenticated username cannot be found
     *                          in the database, indicating a session inconsistency
     */
    public UserEntity getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }
}
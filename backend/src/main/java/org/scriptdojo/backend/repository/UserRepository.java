package org.scriptdojo.backend.repository;

import org.scriptdojo.backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link UserEntity} persistence operations.
 * Standard CRUD operations are inherited from JpaRepository and used by
 * {@link org.scriptdojo.backend.service.UserService} for account creation
 * and retrieval.
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * Returns the user account with the given username, if one exists.
     * Used during authentication by
     * {@link org.scriptdojo.backend.security.CustomUserDetailsService}
     * to load the user for Spring Security, and during registration by
     * {@link org.scriptdojo.backend.controller.AuthController} to check
     * whether a username is already taken.
     */
    Optional<UserEntity> findByUsername(String username);
}
package org.scriptdojo.backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity representing a registered ScriptDojo user.
 * Maps to the "user" table. Users are the owners of files and the hosts
 * of collaborative editing rooms.
 * Passwords are never stored in plain text — BCrypt hashing is applied by
 * {@link org.scriptdojo.backend.service.UserService} before the entity is persisted.
 * Lombok annotations used: @Getter/@Setter for accessors, @NoArgsConstructor
 * for JPA compliance, @AllArgsConstructor and @Builder for programmatic construction.
 */
@Entity
@Table(name = "user")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserEntity {

    /**
     * Primary key, auto-incremented by the database.
     * Never set manually — assigned by the persistence provider on first save.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The unique login name for this account.
     * Checked for availability at registration time before the entity is created.
     * Must not be null and must be unique across all users.
     */
    @Column(nullable = false, unique = true, length = 255)
    private String username;

    /**
     * The BCrypt hash of the user's password.
     * The plain-text password is never stored — hashing is applied in
     * {@link org.scriptdojo.backend.service.UserService#saveUser(UserEntity)}
     * before this entity is persisted. Must not be null.
     */
    @Column(nullable = false, length = 255)
    private String password;

    /**
     * The user's email address.
     * Collected at registration for account identification purposes.
     * Must not be null and must be unique across all users.
     */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * The access role assigned to this account.
     * Defaults to "USER" for all self-registered accounts.
     * Must not be null.
     */
    @Column(nullable = false, length = 50)
    private String role = "USER";
}
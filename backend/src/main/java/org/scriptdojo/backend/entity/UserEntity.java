package org.scriptdojo.backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity representing a registered ScriptDojo user.
 * Maps to the "user" table. Passwords are stored as BCrypt hashes —
 * the plain-text value is never persisted directly.
 */
@Entity
@Table(name = "user")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String username;

    /** Stored as a BCrypt hash. Never set or read as plain text. */
    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** Access level for this account. Defaults to "USER" for all self-registered accounts. */
    @Column(nullable = false, length = 50)
    private String role = "USER";
}
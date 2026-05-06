package org.scriptdojo.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * JPA entity representing a source code file owned by a registered user.
 * Each file has a name, content, and language, and belongs to exactly one
 * {@link UserEntity} via a many-to-one relationship. Files are the central
 * resource in ScriptDojo — they are opened in the editor, shared via rooms,
 * and collaboratively edited in real time.
 * Timestamp management:
 * - createdAt is set at object construction time and never modified
 * - updatedAt is initialised at construction and automatically refreshed
 *   by the {@link #preUpdate()} JPA lifecycle callback on every save
 * Lombok annotations used:
 * - @Getter / @Setter   — generates accessors for all fields
 * - @NoArgsConstructor  — required by JPA (entities must have a no-arg constructor)
 * - @AllArgsConstructor — supports programmatic construction with all fields
 * - @Builder            — enables the fluent builder pattern for test and service code
 * Maps to the "files" table in the database.
 */
@Entity
@Table(name = "files")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FileEntity {

    /**
     * Primary key, auto-incremented by the database.
     * Never set manually — assigned by the persistence provider on first save.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The display name of the file (e.g. "Main.java").
     * Shown in the editor header and the dashboard file list.
     * Must not be null.
     */
    @Column(nullable = false)
    private String name;

    /**
     * The full source code content of the file.
     * Stored as TEXT to accommodate files of arbitrary length.
     * Defaults to an empty string so newly created files are immediately
     * valid without requiring the caller to supply content.
     */
    @Column(columnDefinition = "TEXT")
    private String content = "";

    /**
     * The programming language of the file, used by Monaco Editor to apply
     * the correct syntax highlighting and by the compiler pipeline to
     * determine how to process the source code.
     * Defaults to "java" as ScriptDojo is primarily a Java IDE.
     * Must not be null.
     */
    @Column(nullable = false)
    private String language = "java";

    /**
     * The user who owns this file.
     * Loaded lazily to avoid fetching the full UserEntity on every file query.
     * Mapped via the "owner_id" foreign key column in the "files" table.
     * Must not be null — every file must have an owner.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;

    /**
     * The timestamp at which this file was first created.
     * Set at object construction time and never subsequently modified.
     */
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * The timestamp of the most recent update to this file.
     * Initialised at construction and automatically refreshed by
     * {@link #preUpdate()} whenever the entity is saved after modification.
     */
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * JPA lifecycle callback invoked automatically before every UPDATE statement.
     * Keeps updatedAt current without requiring callers to set it manually,
     * ensuring the timestamp always reflects the true last-modified time
     * regardless of which service method triggered the save.
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
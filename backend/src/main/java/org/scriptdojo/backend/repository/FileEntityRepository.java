package org.scriptdojo.backend.repository;

import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link FileEntity} persistence operations.
 * Provides standard CRUD via JpaRepository and exposes additional derived
 * query methods for owner-scoped file lookups used throughout the application.
 * All query methods are generated at runtime by Spring Data from their
 * method name — no JPQL or native SQL is required.
 */
@Repository
public interface FileEntityRepository extends JpaRepository<FileEntity, Long> {

    /**
     * Returns all files owned by the given user, ordered by most recently
     * updated first. Preferred over {@link #findByOwnerOrderByUpdatedAtDesc}
     * as it queries by the owner's ID directly, avoiding an unnecessary
     * UserEntity fetch.
     * Used by {@link org.scriptdojo.backend.service.FileService} to populate
     * the dashboard file list.
     */
    List<FileEntity> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    /**
     * Returns all files owned by the given UserEntity, ordered by most recently
     * updated first. Alternative to {@link #findByOwnerIdOrderByUpdatedAtDesc}
     * for call sites that already hold a UserEntity reference.
     */
    List<FileEntity> findByOwnerOrderByUpdatedAtDesc(UserEntity owner);

    /**
     * Returns a single file by its ID if it is owned by the specified user,
     * or an empty Optional if no matching record exists.
     * Used for ownership verification — ensures a user cannot access or modify
     * files belonging to other accounts by supplying an arbitrary file ID.
     */
    Optional<FileEntity> findByIdAndOwnerId(Long id, Long ownerId);

    /**
     * Returns all files owned by the given user without any ordering applied.
     * Available for call sites where sort order is handled at the service layer
     * or is not required.
     */
    List<FileEntity> findByOwnerId(Long ownerId);
}
package org.scriptdojo.backend.repository;

import org.scriptdojo.backend.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/**
 * Spring Data JPA repository for {@link RoomEntity} persistence operations.
 * The primary key is a String (the generated alphanumeric room ID) rather than
 * a Long, reflecting that room IDs are human-generated and URL-safe rather than
 * database auto-incremented values.
 * Standard CRUD operations (save, findById, delete) are inherited from
 * JpaRepository and used directly by
 * {@link org.scriptdojo.backend.controller.RoomController}.
 */
public interface RoomRepository extends JpaRepository<RoomEntity, String> {

    /**
     * Returns the room associated with a given file, if one exists.
     * Used to check whether a shareable room has already been created for a
     * file before creating a new one, preventing duplicate room records
     * for the same file.
     */
    Optional<RoomEntity> findByFileId(Long fileId);
}
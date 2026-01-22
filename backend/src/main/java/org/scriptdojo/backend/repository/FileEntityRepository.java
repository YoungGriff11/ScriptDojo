package org.scriptdojo.backend.repository;

import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileEntityRepository extends JpaRepository<FileEntity, Long> {

    // Recommended: Use owner ID (faster, no entity fetch)
    List<FileEntity> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    // Alternative: if you prefer entity reference
    List<FileEntity> findByOwnerOrderByUpdatedAtDesc(UserEntity owner);

    // For ownership check + single file lookup
    Optional<FileEntity> findByIdAndOwnerId(Long id, Long ownerId);

    // Simple list without ordering (if needed elsewhere)
    List<FileEntity> findByOwnerId(Long ownerId);
}
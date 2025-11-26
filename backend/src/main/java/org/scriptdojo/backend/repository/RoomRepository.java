package org.scriptdojo.backend.repository;

import org.scriptdojo.backend.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface RoomRepository extends JpaRepository<RoomEntity, String> {
    Optional<RoomEntity> findByFileId(Long fileId);
}

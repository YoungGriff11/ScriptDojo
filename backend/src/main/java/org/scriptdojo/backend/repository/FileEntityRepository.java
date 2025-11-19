package org.scriptdojo.backend.repository;

import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface FileEntityRepository extends JpaRepository<FileEntity, Long> {
    List<FileEntity> findByOwner(UserEntity owner);
    List<FileEntity> findByOwnerOrderByUpdatedAtDesc(UserEntity owner);
}

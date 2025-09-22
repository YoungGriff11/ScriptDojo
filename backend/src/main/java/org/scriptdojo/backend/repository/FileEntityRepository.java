package org.scriptdojo.backend.repository;

import org.scriptdojo.backend.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileEntityRepository extends JpaRepository<FileEntity, Long> {
}

package org.scriptdojo.backend.repository;

import org.scriptdojo.backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
}

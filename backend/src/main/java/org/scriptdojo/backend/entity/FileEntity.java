package org.scriptdojo.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class FileEntity {
    @Id
    private Long id;

    private String content;
    private String language;
    private Long ownerId; // Temporary reference to User.id, to be updated with @ManyToOne later
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime lastModified;
}

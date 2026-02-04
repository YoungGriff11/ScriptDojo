package org.scriptdojo.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
public class RoomEntity {
    @Id
    private String id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Required by JPA
    public RoomEntity() {
    }

    public RoomEntity(String id, Long fileId, Long hostId) {
        this.id = id;
        this.fileId = fileId;
        this.hostId = hostId;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public Long getHostId() {
        return hostId;
    }

    public void setHostId(Long hostId) {
        this.hostId = hostId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // ← ADD THIS SETTER
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
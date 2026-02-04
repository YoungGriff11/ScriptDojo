package org.scriptdojo.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ActiveUsersService {

    /**
     * Map: fileId -> Set of usernames currently connected
     */
    private final Map<Long, Set<String>> activeUsers = new ConcurrentHashMap<>();

    /**
     * Add a user to a file's active users
     */
    public void addUser(Long fileId, String username) {
        activeUsers.computeIfAbsent(fileId, k -> ConcurrentHashMap.newKeySet()).add(username);
        log.info("👤 User joined: {} → File {}", username, fileId);
        log.info("   Active users for file {}: {}", fileId, activeUsers.get(fileId));
    }

    /**
     * Remove a user from a file's active users
     */
    public void removeUser(Long fileId, String username) {
        Set<String> users = activeUsers.get(fileId);
        if (users != null) {
            users.remove(username);
            log.info("👋 User left: {} ← File {}", username, fileId);

            // Clean up empty sets
            if (users.isEmpty()) {
                activeUsers.remove(fileId);
            }
        }
    }

    /**
     * Get all active users for a file
     */
    public Set<String> getActiveUsers(Long fileId) {
        return new HashSet<>(activeUsers.getOrDefault(fileId, Collections.emptySet()));
    }

    /**
     * Check if a user is active in a file
     */
    public boolean isUserActive(Long fileId, String username) {
        Set<String> users = activeUsers.get(fileId);
        return users != null && users.contains(username);
    }

    /**
     * Get count of active users for a file
     */
    public int getActiveUserCount(Long fileId) {
        Set<String> users = activeUsers.get(fileId);
        return users != null ? users.size() : 0;
    }

    /**
     * Clear all active users (for testing/cleanup)
     */
    public void clearAll() {
        activeUsers.clear();
        log.info("🧹 Cleared all active users");
    }
}
package org.scriptdojo.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory service that tracks which users are currently connected to each
 * collaborative editing room in ScriptDojo.
 * Maintains a map of file IDs to the set of usernames actively connected to
 * that file's room. Updated by {@link org.scriptdojo.backend.config.WebSocketEventListener}
 * on every WebSocket subscribe and disconnect event, and queried by
 * {@link org.scriptdojo.backend.controller.PermissionController} for the
 * active-users HTTP endpoint.
 * All collections are thread-safe — ConcurrentHashMap and its newKeySet() are
 * used throughout since WebSocket events can arrive concurrently on different threads.
 * State is not persisted — the map is cleared on application restart and rebuilt
 * as clients reconnect.
 */
@Service
@Slf4j
public class ActiveUsersService {

    /**
     * Maps each file ID to the set of display names of currently connected users.
     * Entries are created on first join and removed when the last user leaves,
     * keeping the map free of empty sets.
     */
    private final Map<Long, Set<String>> activeUsers = new ConcurrentHashMap<>();

    /**
     * Adds a user to the active set for the given file.
     * Creates a new entry for the file if one does not already exist.
     * Called by WebSocketEventListener when a participant subscribes to a room topic.
     * @param fileId   the ID of the file the user has joined
     * @param username the display name of the joining user
     */
    public void addUser(Long fileId, String username) {
        activeUsers.computeIfAbsent(fileId, k -> ConcurrentHashMap.newKeySet()).add(username);
        log.info("👤 User joined: {} → File {}", username, fileId);
        log.info("   Active users for file {}: {}", fileId, activeUsers.get(fileId));
    }

    /**
     * Removes a user from the active set for the given file.
     * If the set becomes empty after removal, the file's entry is also removed
     * to prevent the map from accumulating stale keys for inactive rooms.
     * Called by WebSocketEventListener when a participant disconnects.
     * @param fileId   the ID of the file the user has left
     * @param username the display name of the departing user
     */
    public void removeUser(Long fileId, String username) {
        Set<String> users = activeUsers.get(fileId);
        if (users != null) {
            users.remove(username);
            log.info("👋 User left: {} ← File {}", username, fileId);

            // Remove the entry entirely once the last user leaves to avoid
            // accumulating empty sets for rooms that are no longer active
            if (users.isEmpty()) {
                activeUsers.remove(fileId);
            }
        }
    }

    /**
     * Returns a snapshot of the active users for the given file.
     * Returns a defensive copy so callers cannot modify the internal set,
     * and returns an empty set rather than null for files with no active users.
     * @param fileId the ID of the file to query
     * @return a new HashSet containing the display names of all currently connected users
     */
    public Set<String> getActiveUsers(Long fileId) {
        return new HashSet<>(activeUsers.getOrDefault(fileId, Collections.emptySet()));
    }

    /**
     * Returns true if the given user is currently connected to the given file's room.
     * @param fileId   the ID of the file to check
     * @param username the display name to look up
     * @return true if the user is in the active set for the file, false otherwise
     */
    public boolean isUserActive(Long fileId, String username) {
        Set<String> users = activeUsers.get(fileId);
        return users != null && users.contains(username);
    }

    /**
     * Returns the number of users currently connected to the given file's room.
     * @param fileId the ID of the file to query
     * @return the count of active users, or 0 if no users are connected
     */
    public int getActiveUserCount(Long fileId) {
        Set<String> users = activeUsers.get(fileId);
        return users != null ? users.size() : 0;
    }

    /**
     * Clears all active user state across all rooms.
     * Intended for use in testing and application cleanup scenarios only —
     * calling this in production would cause all room presence state to be lost.
     */
    public void clearAll() {
        activeUsers.clear();
        log.info("🧹 Cleared all active users");
    }
}
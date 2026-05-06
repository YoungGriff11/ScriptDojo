package org.scriptdojo.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ActiveUsersService}, verifying the in-memory presence
 * tracking behaviour for collaborative editing rooms.
 * Test structure:
 * - addUser        — user appears after add, multiple users per file, deduplication,
 *                    independent tracking across files
 * - removeUser     — user absent after remove, safe removal of non-existent user,
 *                    file entry cleanup after last user leaves
 * - getActiveUsers — empty set for unknown file, correct set after adds
 * - getActiveUserCount — zero for unknown file, correct count after mixed adds and removes
 * - clearAll       — all file entries removed
 * A fresh ActiveUsersService instance is created before each test via @BeforeEach
 * to ensure complete state isolation — no shared state between test methods.
 * No Spring context is loaded; the service is instantiated directly.
 */
class ActiveUsersServiceTest {

    // Fresh instance per test — guarantees no state leaks between test methods
    private ActiveUsersService activeUsersService;

    @BeforeEach
    void setup() {
        activeUsersService = new ActiveUsersService();
    }

    // ─ addUser

    @Test
    @DisplayName("addUser - user appears in active users after being added")
    void addUser_userAppearsInActiveUsers() {
        activeUsersService.addUser(1L, "alice");
        assertTrue(activeUsersService.isUserActive(1L, "alice"));
    }

    @Test
    @DisplayName("addUser - multiple users can be active for same file")
    void addUser_multipleUsersForSameFile() {
        // Confirms users from the same room are tracked independently
        activeUsersService.addUser(1L, "alice");
        activeUsersService.addUser(1L, "bob");
        assertTrue(activeUsersService.isUserActive(1L, "alice"));
        assertTrue(activeUsersService.isUserActive(1L, "bob"));
    }

    @Test
    @DisplayName("addUser - same user added twice is not duplicated")
    void addUser_duplicateUserNotDuplicated() {
        // The underlying ConcurrentHashMap.newKeySet() is a Set — adding the same
        // username twice should result in a count of 1, not 2
        activeUsersService.addUser(1L, "alice");
        activeUsersService.addUser(1L, "alice");
        assertEquals(1, activeUsersService.getActiveUserCount(1L));
    }

    @Test
    @DisplayName("addUser - users in different files are tracked independently")
    void addUser_differentFilesTrackedIndependently() {
        // Confirms file-scoped tracking — a user in file 1 should not appear in file 2
        activeUsersService.addUser(1L, "alice");
        activeUsersService.addUser(2L, "bob");
        assertFalse(activeUsersService.isUserActive(1L, "bob"));
        assertFalse(activeUsersService.isUserActive(2L, "alice"));
    }

    // ─ removeUser

    @Test
    @DisplayName("removeUser - user no longer active after being removed")
    void removeUser_userNoLongerActive() {
        activeUsersService.addUser(1L, "alice");
        activeUsersService.removeUser(1L, "alice");
        assertFalse(activeUsersService.isUserActive(1L, "alice"));
    }

    @Test
    @DisplayName("removeUser - removing non-existent user does not throw")
    void removeUser_nonExistentUser_doesNotThrow() {
        // Simulates a disconnect event arriving for a session that never subscribed
        // to a room — the service must handle this gracefully without throwing
        assertDoesNotThrow(() -> activeUsersService.removeUser(1L, "ghost"));
    }

    @Test
    @DisplayName("removeUser - removing last user cleans up the file entry")
    void removeUser_lastUser_cleansUpFileEntry() {
        // Verifies that the file's map entry is removed when the last user leaves,
        // keeping the internal map free of empty sets for inactive rooms
        activeUsersService.addUser(1L, "alice");
        activeUsersService.removeUser(1L, "alice");
        assertEquals(0, activeUsersService.getActiveUserCount(1L));
    }

    // ─ getActiveUsers

    @Test
    @DisplayName("getActiveUsers - returns empty set for file with no users")
    void getActiveUsers_noUsers_returnsEmptySet() {
        // File ID 99 has never had any users — confirms an empty set is returned
        // rather than null, preventing NullPointerExceptions in callers
        Set<String> users = activeUsersService.getActiveUsers(99L);
        assertTrue(users.isEmpty());
    }

    @Test
    @DisplayName("getActiveUsers - returns all active users for a file")
    void getActiveUsers_returnsAllActiveUsers() {
        // Confirms the returned set contains exactly the users that were added
        activeUsersService.addUser(1L, "alice");
        activeUsersService.addUser(1L, "bob");
        Set<String> users = activeUsersService.getActiveUsers(1L);
        assertEquals(2, users.size());
        assertTrue(users.contains("alice"));
        assertTrue(users.contains("bob"));
    }

    // ─ getActiveUserCount

    @Test
    @DisplayName("getActiveUserCount - returns 0 for file with no users")
    void getActiveUserCount_noUsers_returnsZero() {
        // File ID 99 has never had any users — confirms 0 is returned rather than throwing
        assertEquals(0, activeUsersService.getActiveUserCount(99L));
    }

    @Test
    @DisplayName("getActiveUserCount - returns correct count after adds and removes")
    void getActiveUserCount_afterAddsAndRemoves_returnsCorrectCount() {
        // Adds three users then removes one — confirms the count reflects the net change
        activeUsersService.addUser(1L, "alice");
        activeUsersService.addUser(1L, "bob");
        activeUsersService.addUser(1L, "carol");
        activeUsersService.removeUser(1L, "bob");
        assertEquals(2, activeUsersService.getActiveUserCount(1L));
    }

    // ─ clearAl

    @Test
    @DisplayName("clearAll - removes all users from all files")
    void clearAll_removesAllUsers() {
        // Confirms clearAll resets presence state across all file entries simultaneously
        activeUsersService.addUser(1L, "alice");
        activeUsersService.addUser(2L, "bob");
        activeUsersService.clearAll();
        assertEquals(0, activeUsersService.getActiveUserCount(1L));
        assertEquals(0, activeUsersService.getActiveUserCount(2L));
    }
}
package org.scriptdojo.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ActiveUsersServiceTest {

    private ActiveUsersService activeUsersService;

    @BeforeEach
    void setup() {
        activeUsersService = new ActiveUsersService();
    }

    // ── addUser ───────────────────────────────────────────

    @Test
    @DisplayName("addUser - user appears in active users after being added")
    void addUser_userAppearsInActiveUsers() {
        activeUsersService.addUser(1L, "alice");
        assertTrue(activeUsersService.isUserActive(1L, "alice"));
    }

    @Test
    @DisplayName("addUser - multiple users can be active for same file")
    void addUser_multipleUsersForSameFile() {
        activeUsersService.addUser(1L, "alice");
        activeUsersService.addUser(1L, "bob");
        assertTrue(activeUsersService.isUserActive(1L, "alice"));
        assertTrue(activeUsersService.isUserActive(1L, "bob"));
    }

    @Test
    @DisplayName("addUser - same user added twice is not duplicated")
    void addUser_duplicateUserNotDuplicated() {
        activeUsersService.addUser(1L, "alice");
        activeUsersService.addUser(1L, "alice");
        assertEquals(1, activeUsersService.getActiveUserCount(1L));
    }

    @Test
    @DisplayName("addUser - users in different files are tracked independently")
    void addUser_differentFilesTrackedIndependently() {
        activeUsersService.addUser(1L, "alice");
        activeUsersService.addUser(2L, "bob");
        assertFalse(activeUsersService.isUserActive(1L, "bob"));
        assertFalse(activeUsersService.isUserActive(2L, "alice"));
    }

    // ── removeUser ────────────────────────────────────────

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
        assertDoesNotThrow(() -> activeUsersService.removeUser(1L, "ghost"));
    }

    @Test
    @DisplayName("removeUser - removing last user cleans up the file entry")
    void removeUser_lastUser_cleansUpFileEntry() {
        activeUsersService.addUser(1L, "alice");
        activeUsersService.removeUser(1L, "alice");
        assertEquals(0, activeUsersService.getActiveUserCount(1L));
    }

    // ── getActiveUsers ────────────────────────────────────

    @Test
    @DisplayName("getActiveUsers - returns empty set for file with no users")
    void getActiveUsers_noUsers_returnsEmptySet() {
        Set<String> users = activeUsersService.getActiveUsers(99L);
        assertTrue(users.isEmpty());
    }

    @Test
    @DisplayName("getActiveUsers - returns all active users for a file")
    void getActiveUsers_returnsAllActiveUsers() {
        activeUsersService.addUser(1L, "alice");
        activeUsersService.addUser(1L, "bob");
        Set<String> users = activeUsersService.getActiveUsers(1L);
        assertEquals(2, users.size());
        assertTrue(users.contains("alice"));
        assertTrue(users.contains("bob"));
    }

    // ── getActiveUserCount ────────────────────────────────

    @Test
    @DisplayName("getActiveUserCount - returns 0 for file with no users")
    void getActiveUserCount_noUsers_returnsZero() {
        assertEquals(0, activeUsersService.getActiveUserCount(99L));
    }

    @Test
    @DisplayName("getActiveUserCount - returns correct count after adds and removes")
    void getActiveUserCount_afterAddsAndRemoves_returnsCorrectCount() {
        activeUsersService.addUser(1L, "alice");
        activeUsersService.addUser(1L, "bob");
        activeUsersService.addUser(1L, "carol");
        activeUsersService.removeUser(1L, "bob");
        assertEquals(2, activeUsersService.getActiveUserCount(1L));
    }

    // ── clearAll ──────────────────────────────────────────

    @Test
    @DisplayName("clearAll - removes all users from all files")
    void clearAll_removesAllUsers() {
        activeUsersService.addUser(1L, "alice");
        activeUsersService.addUser(2L, "bob");
        activeUsersService.clearAll();
        assertEquals(0, activeUsersService.getActiveUserCount(1L));
        assertEquals(0, activeUsersService.getActiveUserCount(2L));
    }
}
package org.scriptdojo.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.scriptdojo.backend.entity.PermissionEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermissionServiceTest {

    @Autowired
    private PermissionService permissionService;

    private static final Long FILE_ID = 100L;
    private static final Long GRANTED_BY = 1L;
    private static final String GUEST_NAME = "test_guest";

    @BeforeEach
    void cleanup() {
        permissionService.deleteFilePermissions(FILE_ID);
    }

    // ── canGuestEdit ──────────────────────────────────────

    @Test
    @DisplayName("canGuestEdit - returns false when no permission exists")
    void canGuestEdit_noPermission_returnsFalse() {
        assertFalse(permissionService.canGuestEdit(GUEST_NAME, FILE_ID));
    }

    @Test
    @DisplayName("canGuestEdit - returns true after edit permission is granted")
    void canGuestEdit_afterGrantEdit_returnsTrue() {
        permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        assertTrue(permissionService.canGuestEdit(GUEST_NAME, FILE_ID));
    }

    @Test
    @DisplayName("canGuestEdit - returns false after edit permission is revoked")
    void canGuestEdit_afterRevokeEdit_returnsFalse() {
        permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        permissionService.revokeGuestEdit(GUEST_NAME, FILE_ID);
        assertFalse(permissionService.canGuestEdit(GUEST_NAME, FILE_ID));
    }

    // ── hasGuestAccess ────────────────────────────────────

    @Test
    @DisplayName("hasGuestAccess - returns false when no permission exists")
    void hasGuestAccess_noPermission_returnsFalse() {
        assertFalse(permissionService.hasGuestAccess(GUEST_NAME, FILE_ID));
    }

    @Test
    @DisplayName("hasGuestAccess - returns true after view permission is granted")
    void hasGuestAccess_afterGrantView_returnsTrue() {
        permissionService.grantGuestView(GUEST_NAME, FILE_ID, GRANTED_BY);
        assertTrue(permissionService.hasGuestAccess(GUEST_NAME, FILE_ID));
    }

    // ── grantGuestEdit ────────────────────────────────────

    @Test
    @DisplayName("grantGuestEdit - creates permission with EDIT role")
    void grantGuestEdit_createsEditPermission() {
        PermissionEntity permission = permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        assertTrue(permission.canEdit());
    }

    @Test
    @DisplayName("grantGuestEdit - sets correct fileId")
    void grantGuestEdit_setsCorrectFileId() {
        PermissionEntity permission = permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        assertEquals(FILE_ID, permission.getFileId());
    }

    @Test
    @DisplayName("grantGuestEdit - sets correct guestName")
    void grantGuestEdit_setsCorrectGuestName() {
        PermissionEntity permission = permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        assertEquals(GUEST_NAME, permission.getGuestName());
    }

    @Test
    @DisplayName("grantGuestEdit - granting twice does not create duplicate")
    void grantGuestEdit_twice_doesNotDuplicate() {
        permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        List<PermissionEntity> permissions = permissionService.getFilePermissions(FILE_ID);
        assertEquals(1, permissions.size());
    }

    // ── revokeGuestEdit ───────────────────────────────────

    @Test
    @DisplayName("revokeGuestEdit - revoking non-existent permission does not throw")
    void revokeGuestEdit_nonExistent_doesNotThrow() {
        assertDoesNotThrow(() -> permissionService.revokeGuestEdit("ghost_guest", FILE_ID));
    }

    @Test
    @DisplayName("revokeGuestEdit - permission still exists after revoke but canEdit is false")
    void revokeGuestEdit_permissionStillExistsButCannotEdit() {
        permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        permissionService.revokeGuestEdit(GUEST_NAME, FILE_ID);
        assertTrue(permissionService.hasGuestAccess(GUEST_NAME, FILE_ID));
        assertFalse(permissionService.canGuestEdit(GUEST_NAME, FILE_ID));
    }

    // ── removeGuestAccess ─────────────────────────────────

    @Test
    @DisplayName("removeGuestAccess - guest has no access after removal")
    void removeGuestAccess_guestHasNoAccess() {
        permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        permissionService.removeGuestAccess(GUEST_NAME, FILE_ID);
        assertFalse(permissionService.hasGuestAccess(GUEST_NAME, FILE_ID));
    }

    // ── getFilePermissions ────────────────────────────────

    @Test
    @DisplayName("getFilePermissions - returns empty list when no permissions exist")
    void getFilePermissions_noPermissions_returnsEmptyList() {
        List<PermissionEntity> permissions = permissionService.getFilePermissions(FILE_ID);
        assertTrue(permissions.isEmpty());
    }

    @Test
    @DisplayName("getFilePermissions - returns all permissions for a file")
    void getFilePermissions_returnsAllPermissions() {
        permissionService.grantGuestEdit("guest_a", FILE_ID, GRANTED_BY);
        permissionService.grantGuestEdit("guest_b", FILE_ID, GRANTED_BY);
        List<PermissionEntity> permissions = permissionService.getFilePermissions(FILE_ID);
        assertEquals(2, permissions.size());
    }

    // ── deleteFilePermissions ─────────────────────────────

    @Test
    @DisplayName("deleteFilePermissions - removes all permissions for a file")
    void deleteFilePermissions_removesAll() {
        permissionService.grantGuestEdit("guest_a", FILE_ID, GRANTED_BY);
        permissionService.grantGuestEdit("guest_b", FILE_ID, GRANTED_BY);
        permissionService.deleteFilePermissions(FILE_ID);
        assertTrue(permissionService.getFilePermissions(FILE_ID).isEmpty());
    }
}
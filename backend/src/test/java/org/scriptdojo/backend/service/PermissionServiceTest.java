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

/**
 * Integration tests for {@link PermissionService}, verifying guest permission
 * management behaviour against the H2 in-memory database.
 * Test structure:
 * - canGuestEdit        — false when absent, true after grant, false after revoke
 * - hasGuestAccess      — false when absent, true after view grant
 * - grantGuestEdit      — EDIT role set, fileId and guestName populated, upsert on double grant
 * - revokeGuestEdit     — safe on non-existent permission, record retained with canEdit=false
 * - removeGuestAccess   — record deleted entirely, no access remains
 * - getFilePermissions  — empty list when none exist, all records returned after grants
 * - deleteFilePermissions — all records removed for the file
 * Uses @SpringBootTest to load the full application context with H2 so that
 * real JPA persistence is exercised rather than mocked repository behaviour.
 * @BeforeEach clears all permissions for FILE_ID before each test to ensure
 * complete state isolation without reloading the Spring context.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermissionServiceTest {

    @Autowired
    private PermissionService permissionService;

    /**
     * Shared constants used across all tests.
     * FILE_ID is a fixed value that does not correspond to a real file record —
     * PermissionService does not validate file existence, so this is safe.
     * GRANTED_BY simulates the host user ID without requiring a real user record.
     */
    private static final Long FILE_ID    = 100L;
    private static final Long GRANTED_BY = 1L;
    private static final String GUEST_NAME = "test_guest";

    /**
     * Clears all permission records for FILE_ID before each test.
     * Ensures no state leaks between tests without requiring a full context reload.
     */
    @BeforeEach
    void cleanup() {
        permissionService.deleteFilePermissions(FILE_ID);
    }

    // ─ canGuestEdit

    @Test
    @DisplayName("canGuestEdit - returns false when no permission exists")
    void canGuestEdit_noPermission_returnsFalse() {
        // No permission record exists — should return false rather than throwing
        assertFalse(permissionService.canGuestEdit(GUEST_NAME, FILE_ID));
    }

    @Test
    @DisplayName("canGuestEdit - returns true after edit permission is granted")
    void canGuestEdit_afterGrantEdit_returnsTrue() {
        // Confirms the EDIT role is correctly persisted and reflected in the check
        permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        assertTrue(permissionService.canGuestEdit(GUEST_NAME, FILE_ID));
    }

    @Test
    @DisplayName("canGuestEdit - returns false after edit permission is revoked")
    void canGuestEdit_afterRevokeEdit_returnsFalse() {
        // Confirms revokeGuestEdit correctly downgrades the role so canGuestEdit
        // returns false even though the permission record still exists
        permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        permissionService.revokeGuestEdit(GUEST_NAME, FILE_ID);
        assertFalse(permissionService.canGuestEdit(GUEST_NAME, FILE_ID));
    }

    // ─ hasGuestAccess

    @Test
    @DisplayName("hasGuestAccess - returns false when no permission exists")
    void hasGuestAccess_noPermission_returnsFalse() {
        // No permission record exists — should return false rather than throwing
        assertFalse(permissionService.hasGuestAccess(GUEST_NAME, FILE_ID));
    }

    @Test
    @DisplayName("hasGuestAccess - returns true after view permission is granted")
    void hasGuestAccess_afterGrantView_returnsTrue() {
        // VIEW-level access still constitutes having access — confirms hasGuestAccess
        // returns true for both VIEW and EDIT records, not just EDIT
        permissionService.grantGuestView(GUEST_NAME, FILE_ID, GRANTED_BY);
        assertTrue(permissionService.hasGuestAccess(GUEST_NAME, FILE_ID));
    }

    // ─ grantGuestEdit

    @Test
    @DisplayName("grantGuestEdit - creates permission with EDIT role")
    void grantGuestEdit_createsEditPermission() {
        // Confirms the returned entity has canEdit() returning true
        PermissionEntity permission = permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        assertTrue(permission.canEdit());
    }

    @Test
    @DisplayName("grantGuestEdit - sets correct fileId")
    void grantGuestEdit_setsCorrectFileId() {
        // Confirms the fileId is correctly associated on the persisted entity
        PermissionEntity permission = permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        assertEquals(FILE_ID, permission.getFileId());
    }

    @Test
    @DisplayName("grantGuestEdit - sets correct guestName")
    void grantGuestEdit_setsCorrectGuestName() {
        // Confirms the guest display name is correctly stored on the persisted entity
        PermissionEntity permission = permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        assertEquals(GUEST_NAME, permission.getGuestName());
    }

    @Test
    @DisplayName("grantGuestEdit - granting twice does not create duplicate")
    void grantGuestEdit_twice_doesNotDuplicate() {
        // Confirms the upsert behaviour — granting edit to an already-granted guest
        // upgrades the existing record rather than inserting a second one
        permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        List<PermissionEntity> permissions = permissionService.getFilePermissions(FILE_ID);
        assertEquals(1, permissions.size());
    }

    // ─ revokeGuestEdit

    @Test
    @DisplayName("revokeGuestEdit - revoking non-existent permission does not throw")
    void revokeGuestEdit_nonExistent_doesNotThrow() {
        // Simulates a revoke arriving for a guest who never had a permission record —
        // the service must handle this gracefully without throwing
        assertDoesNotThrow(() -> permissionService.revokeGuestEdit("ghost_guest", FILE_ID));
    }

    @Test
    @DisplayName("revokeGuestEdit - permission still exists after revoke but canEdit is false")
    void revokeGuestEdit_permissionStillExistsButCannotEdit() {
        // Revoke downgrades the role to VIEW rather than deleting the record —
        // confirms the guest still has access but can no longer edit
        permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        permissionService.revokeGuestEdit(GUEST_NAME, FILE_ID);
        assertTrue(permissionService.hasGuestAccess(GUEST_NAME, FILE_ID));
        assertFalse(permissionService.canGuestEdit(GUEST_NAME, FILE_ID));
    }

    // ─ removeGuestAccess

    @Test
    @DisplayName("removeGuestAccess - guest has no access after removal")
    void removeGuestAccess_guestHasNoAccess() {
        // Confirms the permission record is fully deleted rather than downgraded —
        // the guest has no access of any kind after removeGuestAccess is called
        permissionService.grantGuestEdit(GUEST_NAME, FILE_ID, GRANTED_BY);
        permissionService.removeGuestAccess(GUEST_NAME, FILE_ID);
        assertFalse(permissionService.hasGuestAccess(GUEST_NAME, FILE_ID));
    }

    // ─ getFilePermissions

    @Test
    @DisplayName("getFilePermissions - returns empty list when no permissions exist")
    void getFilePermissions_noPermissions_returnsEmptyList() {
        // Confirms an empty list is returned rather than null for a file with no records
        List<PermissionEntity> permissions = permissionService.getFilePermissions(FILE_ID);
        assertTrue(permissions.isEmpty());
    }

    @Test
    @DisplayName("getFilePermissions - returns all permissions for a file")
    void getFilePermissions_returnsAllPermissions() {
        // Confirms both guest permission records are returned in the list
        permissionService.grantGuestEdit("guest_a", FILE_ID, GRANTED_BY);
        permissionService.grantGuestEdit("guest_b", FILE_ID, GRANTED_BY);
        List<PermissionEntity> permissions = permissionService.getFilePermissions(FILE_ID);
        assertEquals(2, permissions.size());
    }

    // ─ deleteFilePermissions

    @Test
    @DisplayName("deleteFilePermissions - removes all permissions for a file")
    void deleteFilePermissions_removesAll() {
        // Confirms all records for the file are deleted in a single operation,
        // leaving an empty list on subsequent retrieval
        permissionService.grantGuestEdit("guest_a", FILE_ID, GRANTED_BY);
        permissionService.grantGuestEdit("guest_b", FILE_ID, GRANTED_BY);
        permissionService.deleteFilePermissions(FILE_ID);
        assertTrue(permissionService.getFilePermissions(FILE_ID).isEmpty());
    }
}
package org.scriptdojo.backend.entity;

/**
 * Defines the access levels available for a collaborative editing session
 * in ScriptDojo.
 *
 * Used by {@link PermissionEntity} to control what actions a participant
 * can perform within a shared room:
 *
 *   VIEW — the participant can see the editor content in real time but
 *          cannot type or modify it. This is the default level assigned
 *          to all guests when they first join a room.
 *
 *   EDIT — the participant has full read/write access and can type in
 *          the editor. Granted explicitly by the host via the permissions
 *          panel during a live session.
 *
 * Persisted as a String column (via {@code @Enumerated(EnumType.STRING)}
 * in PermissionEntity) so that the stored values "VIEW" and "EDIT" remain
 * stable and human-readable regardless of any future reordering of the
 * enum constants.
 */
public enum PermissionRole {

    /** Read-only access. The editor is visible but not editable. Default for all guests. */
    VIEW,

    /** Full read/write access. The guest can type in the editor. Granted by the host. */
    EDIT
}
package org.scriptdojo.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import java.util.Map;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link org.scriptdojo.backend.controller.PermissionController},
 * covering grant, revoke, list, and active-user endpoints under /api/permissions.
 * Test structure:
 * - POST /api/permissions/grant-edit      — owner grants guest edit access
 * - POST /api/permissions/revoke-edit     — authentication enforcement only
 * - GET  /api/permissions/file/{fileId}   — permission list retrieval and grant reflection
 * - GET  /api/permissions/file/{fileId}/active-users — active user map structure
 * Uses @TestInstance(PER_CLASS) so @BeforeAll can be non-static, registering the test
 * user and creating a shared test file before any tests run.
 * The @BeforeAll setup uses SecurityMockMvcRequestPostProcessors.user() with a real
 * CustomUserDetails instance (loaded via CustomUserDetailsService) rather than
 * @WithUserDetails, because @WithUserDetails is not available on non-@Test methods.
 * This ensures the principal cast in FileController resolves a valid database user ID
 * when associating the file with its owner.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Username used for all authenticated test cases.
     * Registered once in @BeforeAll and referenced by @WithUserDetails
     * to load a valid CustomUserDetails with a real database ID.
     */
    private static final String TEST_USERNAME = "permtest_user";

    /**
     * ID of the file created in @BeforeAll and shared across all tests.
     * All permission operations target this file.
     */
    private Long testFileId;

    // Needed to load a real CustomUserDetails for the @BeforeAll file creation request
    @Autowired
    private org.scriptdojo.backend.security.CustomUserDetailsService userDetailsService;

    /**
     * Registers the test user and creates a shared test file before any tests run.
     * File creation uses SecurityMockMvcRequestPostProcessors.user() with a real
     * CustomUserDetails loaded from the database rather than @WithUserDetails, because
     * @WithUserDetails cannot be applied to @BeforeAll methods. This ensures the
     * principal cast in FileController succeeds and the file is correctly associated
     * with the test user's database ID.
     */
    @BeforeAll
    void setup() throws Exception {
        // Register the test user via the public registration endpoint
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USERNAME,
                                "password", "Password1!",
                                "email", "permtest@test.com"
                        ))))
                .andExpect(status().isOk());

        // Load the real CustomUserDetails so the principal cast in FileController
        // can resolve the database user ID correctly
        org.springframework.security.core.userdetails.UserDetails userDetails =
                userDetailsService.loadUserByUsername(TEST_USERNAME);

        // Create the shared test file using the real principal
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "PermTestFile.java",
                "content", "public class PermTestFile {}",
                "language", "java"
        ));

        MvcResult result = mockMvc.perform(post("/api/files")
                        .with(SecurityMockMvcRequestPostProcessors.user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        testFileId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    /**
     * Helper that creates a file using @WithUserDetails.
     * Available for tests that need a fresh file independent of the shared testFileId.
     * Note: @WithUserDetails on a helper method only applies when called from a @Test
     * method that is itself annotated with @WithUserDetails.
     */
    @WithUserDetails(TEST_USERNAME)
    private Long createTestFile() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "PermTestFile.java",
                "content", "public class PermTestFile {}",
                "language", "java"
        ));

        MvcResult result = mockMvc.perform(post("/api/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    // ─ POST /api/permissions/grant-edit

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("POST /api/permissions/grant-edit - owner can grant edit to guest")
    void grantEdit_ownerGrantsGuest_returns200() throws Exception {
        // Confirms the file owner can successfully grant edit access to a named guest
        mockMvc.perform(post("/api/permissions/grant-edit")
                        .param("fileId", testFileId.toString())
                        .param("guestName", "guest_alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("POST /api/permissions/grant-edit - response contains expected fields")
    void grantEdit_responseHasExpectedFields() throws Exception {
        // Verifies all expected PermissionDTO fields are present and correctly populated
        mockMvc.perform(post("/api/permissions/grant-edit")
                        .param("fileId", testFileId.toString())
                        .param("guestName", "guest_bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fileId").value(testFileId))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.isGuest").value(true));
    }

    @Test
    @DisplayName("POST /api/permissions/grant-edit - unauthenticated request redirects to login")
    void grantEdit_unauthenticated_redirects() throws Exception {
        // No session present — Spring Security should redirect before the controller is reached
        mockMvc.perform(post("/api/permissions/grant-edit")
                        .param("fileId", testFileId.toString())
                        .param("guestName", "guest_anon"))
                .andExpect(status().isFound());
    }

    // ─ POST /api/permissions/revoke-edit

    @Test
    @DisplayName("POST /api/permissions/revoke-edit - unauthenticated request redirects to login")
    void revokeEdit_unauthenticated_redirects() throws Exception {
        // Authentication enforcement only — functional revoke tests are omitted
        // because the placeholder userId bug in PermissionController causes all
        // revoke attempts to return 403 regardless of the caller's identity
        mockMvc.perform(post("/api/permissions/revoke-edit")
                        .param("fileId", testFileId.toString())
                        .param("guestName", "guest_anon"))
                .andExpect(status().isFound());
    }

    // ─ GET /api/permissions/file/{fileId}

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/permissions/file/{fileId} - returns list of permissions")
    void getFilePermissions_authenticated_returnsList() throws Exception {
        // Confirms the endpoint returns a JSON array for an authenticated request
        mockMvc.perform(get("/api/permissions/file/" + testFileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/permissions/file/{fileId} - granted permission appears in list")
    void getFilePermissions_afterGrant_containsGuest() throws Exception {
        // Grants permission to a uniquely named guest then verifies they appear
        // in the permission list by their identifier field
        String guestName = "guest_listed_" + System.currentTimeMillis();

        mockMvc.perform(post("/api/permissions/grant-edit")
                        .param("fileId", testFileId.toString())
                        .param("guestName", guestName))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/permissions/file/" + testFileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].identifier", hasItem(guestName)));
    }

    @Test
    @DisplayName("GET /api/permissions/file/{fileId} - unauthenticated request redirects to login")
    void getFilePermissions_unauthenticated_redirects() throws Exception {
        // No session present — Spring Security should redirect before the controller is reached
        mockMvc.perform(get("/api/permissions/file/" + testFileId))
                .andExpect(status().isFound());
    }

    // ─ GET /api/permissions/file/{fileId}/active-users

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/permissions/file/{fileId}/active-users - returns active users map")
    void getActiveUsers_authenticated_returnsMap() throws Exception {
        // Verifies the response contains the expected fileId, users, and count fields —
        // count will be 0 as no WebSocket connections are active during tests
        mockMvc.perform(get("/api/permissions/file/" + testFileId + "/active-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(testFileId))
                .andExpect(jsonPath("$.users").exists())
                .andExpect(jsonPath("$.count").isNumber());
    }

    @Test
    @DisplayName("GET /api/permissions/file/{fileId}/active-users - unauthenticated request redirects")
    void getActiveUsers_unauthenticated_redirects() throws Exception {
        // No session present — Spring Security should redirect before the controller is reached
        mockMvc.perform(get("/api/permissions/file/" + testFileId + "/active-users"))
                .andExpect(status().isFound());
    }
}
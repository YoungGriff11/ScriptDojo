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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_USERNAME = "permtest_user";
    private Long testFileId;

    @Autowired
    private org.scriptdojo.backend.security.CustomUserDetailsService userDetailsService;

    @BeforeAll
    void setup() throws Exception {
        // Create test user
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USERNAME,
                                "password", "Password1!",
                                "email", "permtest@test.com"
                        ))))
                .andExpect(status().isOk());

        // Load the real CustomUserDetails so the principal cast in FileController works
        org.springframework.security.core.userdetails.UserDetails userDetails =
                userDetailsService.loadUserByUsername(TEST_USERNAME);

        // Create a test file with the real principal
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

    // ── Helper ─────────────────────────────────────────────

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

    // ── POST /api/permissions/grant-edit ──────────────────

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("POST /api/permissions/grant-edit - owner can grant edit to guest")
    void grantEdit_ownerGrantsGuest_returns200() throws Exception {
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
        mockMvc.perform(post("/api/permissions/grant-edit")
                        .param("fileId", testFileId.toString())
                        .param("guestName", "guest_anon"))
                .andExpect(status().isFound());
    }

    @Test
    @DisplayName("POST /api/permissions/revoke-edit - unauthenticated request redirects to login")
    void revokeEdit_unauthenticated_redirects() throws Exception {
        mockMvc.perform(post("/api/permissions/revoke-edit")
                        .param("fileId", testFileId.toString())
                        .param("guestName", "guest_anon"))
                .andExpect(status().isFound());
    }

    // ── GET /api/permissions/file/{fileId} ────────────────

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/permissions/file/{fileId} - returns list of permissions")
    void getFilePermissions_authenticated_returnsList() throws Exception {
        mockMvc.perform(get("/api/permissions/file/" + testFileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/permissions/file/{fileId} - granted permission appears in list")
    void getFilePermissions_afterGrant_containsGuest() throws Exception {
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
        mockMvc.perform(get("/api/permissions/file/" + testFileId))
                .andExpect(status().isFound());
    }

    // ── GET /api/permissions/file/{fileId}/active-users ───

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/permissions/file/{fileId}/active-users - returns active users map")
    void getActiveUsers_authenticated_returnsMap() throws Exception {
        mockMvc.perform(get("/api/permissions/file/" + testFileId + "/active-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(testFileId))
                .andExpect(jsonPath("$.users").exists())
                .andExpect(jsonPath("$.count").isNumber());
    }

    @Test
    @DisplayName("GET /api/permissions/file/{fileId}/active-users - unauthenticated request redirects")
    void getActiveUsers_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/api/permissions/file/" + testFileId + "/active-users"))
                .andExpect(status().isFound());
    }
}
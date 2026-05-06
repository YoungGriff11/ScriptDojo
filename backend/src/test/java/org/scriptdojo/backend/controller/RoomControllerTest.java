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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link org.scriptdojo.backend.controller.RoomController},
 * covering room creation and the guest join endpoint.
 * Test structure:
 * - POST /api/room/create      — authenticated room creation, response shape, roomId format
 * - GET  /api/room/join/{roomId} — public join endpoint, response fields, Base64 content,
 *                                  guest name format, invalid room handling
 * Uses @TestInstance(PER_CLASS) so @BeforeAll can be non-static, allowing a shared
 * test file and room to be created once before all tests run.
 * All authenticated requests use SecurityMockMvcRequestPostProcessors.user() with a
 * real CustomUserDetails loaded via CustomUserDetailsService, rather than @WithUserDetails,
 * because the principal cast in RoomController and FileController requires a real database
 * user ID that @WithMockUser cannot provide.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Used to load a real CustomUserDetails for authenticated requests in setup and tests
    @Autowired
    private org.scriptdojo.backend.security.CustomUserDetailsService userDetailsService;

    /**
     * Username used for all authenticated test cases.
     * Registered and loaded in @BeforeAll to produce a real CustomUserDetails instance.
     */
    private static final String TEST_USERNAME = "roomtest_user";

    /**
     * ID of the file created in @BeforeAll and used as the target for room creation tests.
     */
    private Long testFileId;

    /**
     * Room ID of the room created in @BeforeAll and used as the target for join tests.
     */
    private String testRoomId;

    /**
     * Registers the test user, creates a shared test file, and creates a shared test room
     * before any tests run. Both the file and room are reused across all tests to avoid
     * redundant setup per test.
     * SecurityMockMvcRequestPostProcessors.user() is used throughout because @WithUserDetails
     * is not available on @BeforeAll methods, and the CustomUserDetails principal cast in
     * both FileController and RoomController requires a real database user ID.
     */
    @BeforeAll
    void setup() throws Exception {
        // Register the test user via the public registration endpoint
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USERNAME,
                                "password", "Password1!",
                                "email", "roomtest@test.com"
                        ))))
                .andExpect(status().isOk());

        // Load the real CustomUserDetails so the principal cast in controllers succeeds
        org.springframework.security.core.userdetails.UserDetails userDetails =
                userDetailsService.loadUserByUsername(TEST_USERNAME);

        // Create the shared test file
        MvcResult fileResult = mockMvc.perform(post("/api/files")
                        .with(SecurityMockMvcRequestPostProcessors.user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "RoomTestFile.java",
                                "content", "public class RoomTestFile {}",
                                "language", "java"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        testFileId = objectMapper.readTree(fileResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Create the shared test room using the test file's ID
        MvcResult roomResult = mockMvc.perform(post("/api/room/create")
                        .with(SecurityMockMvcRequestPostProcessors.user(userDetails))
                        .param("fileId", testFileId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        testRoomId = objectMapper.readTree(roomResult.getResponse().getContentAsString())
                .get("roomId").asText();
    }

    // ─ POST /api/room/create

    @Test
    @DisplayName("POST /api/room/create - authenticated owner creates room successfully")
    void createRoom_authenticatedOwner_returns200() throws Exception {
        // Confirms an authenticated file owner can create a room and receives a valid response
        org.springframework.security.core.userdetails.UserDetails userDetails =
                userDetailsService.loadUserByUsername(TEST_USERNAME);

        mockMvc.perform(post("/api/room/create")
                        .with(SecurityMockMvcRequestPostProcessors.user(userDetails))
                        .param("fileId", testFileId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").exists())
                .andExpect(jsonPath("$.url").exists());
    }

    @Test
    @DisplayName("POST /api/room/create - response contains roomId and share URL")
    void createRoom_responseHasRoomIdAndUrl() throws Exception {
        // Verifies the roomId is a string and the URL contains the /room/ path segment
        org.springframework.security.core.userdetails.UserDetails userDetails =
                userDetailsService.loadUserByUsername(TEST_USERNAME);

        mockMvc.perform(post("/api/room/create")
                        .with(SecurityMockMvcRequestPostProcessors.user(userDetails))
                        .param("fileId", testFileId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").isString())
                .andExpect(jsonPath("$.url", containsString("/room/")));
    }

    @Test
    @DisplayName("POST /api/room/create - roomId is 11 characters")
    void createRoom_roomIdIs11Characters() throws Exception {
        // Verifies the room ID generator produces exactly 11 alphanumeric characters
        // as specified in RoomController#generateRoomId
        org.springframework.security.core.userdetails.UserDetails userDetails =
                userDetailsService.loadUserByUsername(TEST_USERNAME);

        MvcResult result = mockMvc.perform(post("/api/room/create")
                        .with(SecurityMockMvcRequestPostProcessors.user(userDetails))
                        .param("fileId", testFileId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        String roomId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("roomId").asText();

        assert roomId.length() == 11;
    }

    @Test
    @DisplayName("POST /api/room/create - unauthenticated request redirects to login")
    void createRoom_unauthenticated_redirects() throws Exception {
        // No session present — Spring Security should redirect before the controller is reached
        mockMvc.perform(post("/api/room/create")
                        .param("fileId", testFileId.toString()))
                .andExpect(status().isFound());
    }

    // ─ GET /api/room/join/{roomId}

    @Test
    @DisplayName("GET /api/room/join/{roomId} - valid roomId returns room data")
    void joinRoom_validRoomId_returns200() throws Exception {
        // Confirms all expected fields are present in the join response
        mockMvc.perform(get("/api/room/join/" + testRoomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").exists())
                .andExpect(jsonPath("$.fileName").exists())
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.guestName").exists());
    }

    @Test
    @DisplayName("GET /api/room/join/{roomId} - fileName matches the file used to create room")
    void joinRoom_fileNameMatchesCreatedFile() throws Exception {
        // Verifies the join response returns the correct file associated with the room
        mockMvc.perform(get("/api/room/join/" + testRoomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("RoomTestFile.java"));
    }

    @Test
    @DisplayName("GET /api/room/join/{roomId} - content is base64 encoded")
    void joinRoom_contentIsBase64() throws Exception {
        // Verifies the file content is Base64-encoded in the response — the regex
        // matches only valid Base64 characters (alphanumeric, +, /, and = padding)
        MvcResult result = mockMvc.perform(get("/api/room/join/" + testRoomId))
                .andExpect(status().isOk())
                .andReturn();

        String content = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("content").asText();

        assert content.matches("^[A-Za-z0-9+/=]+$");
    }

    @Test
    @DisplayName("GET /api/room/join/{roomId} - guestName starts with Guest")
    void joinRoom_guestNameStartsWithGuest() throws Exception {
        // Verifies the generated guest name follows the "Guest{number}" format
        // defined in RoomController#generateGuestName
        mockMvc.perform(get("/api/room/join/" + testRoomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestName", startsWith("Guest")));
    }

    @Test
    @DisplayName("GET /api/room/join/{roomId} - invalid roomId throws exception")
    void joinRoom_invalidRoomId_throwsException() {
        // RuntimeException propagates as ServletException when no room is found —
        // assertThrows captures it rather than failing the test
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                mockMvc.perform(get("/api/room/join/nonexistent_room_id")));
    }

    @Test
    @DisplayName("GET /api/room/join/{roomId} - no authentication required")
    void joinRoom_noAuthRequired_returns200() throws Exception {
        // Confirms the join endpoint is publicly accessible without a session —
        // guests must be able to join without an account
        mockMvc.perform(get("/api/room/join/" + testRoomId))
                .andExpect(status().isOk());
    }
}
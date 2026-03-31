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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.scriptdojo.backend.security.CustomUserDetailsService userDetailsService;

    private static final String TEST_USERNAME = "roomtest_user";
    private Long testFileId;
    private String testRoomId;

    @BeforeAll
    void setup() throws Exception {
        // Register test user
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USERNAME,
                                "password", "Password1!",
                                "email", "roomtest@test.com"
                        ))))
                .andExpect(status().isOk());

        org.springframework.security.core.userdetails.UserDetails userDetails =
                userDetailsService.loadUserByUsername(TEST_USERNAME);

        // Create a test file
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

        // Create a test room
        MvcResult roomResult = mockMvc.perform(post("/api/room/create")
                        .with(SecurityMockMvcRequestPostProcessors.user(userDetails))
                        .param("fileId", testFileId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        testRoomId = objectMapper.readTree(roomResult.getResponse().getContentAsString())
                .get("roomId").asText();
    }

    // ── POST /api/room/create ─────────────────────────────

    @Test
    @DisplayName("POST /api/room/create - authenticated owner creates room successfully")
    void createRoom_authenticatedOwner_returns200() throws Exception {
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
        mockMvc.perform(post("/api/room/create")
                        .param("fileId", testFileId.toString()))
                .andExpect(status().isFound());
    }

    // ── GET /api/room/join/{roomId} ───────────────────────

    @Test
    @DisplayName("GET /api/room/join/{roomId} - valid roomId returns room data")
    void joinRoom_validRoomId_returns200() throws Exception {
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
        mockMvc.perform(get("/api/room/join/" + testRoomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("RoomTestFile.java"));
    }

    @Test
    @DisplayName("GET /api/room/join/{roomId} - content is base64 encoded")
    void joinRoom_contentIsBase64() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/room/join/" + testRoomId))
                .andExpect(status().isOk())
                .andReturn();

        String content = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("content").asText();

        // Base64 only contains alphanumeric, +, /, and = padding
        assert content.matches("^[A-Za-z0-9+/=]+$");
    }

    @Test
    @DisplayName("GET /api/room/join/{roomId} - guestName starts with Guest")
    void joinRoom_guestNameStartsWithGuest() throws Exception {
        mockMvc.perform(get("/api/room/join/" + testRoomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestName", startsWith("Guest")));
    }

    @Test
    @DisplayName("GET /api/room/join/{roomId} - invalid roomId throws exception")
    void joinRoom_invalidRoomId_throwsException() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                mockMvc.perform(get("/api/room/join/nonexistent_room_id")));
    }

    @Test
    @DisplayName("GET /api/room/join/{roomId} - no authentication required")
    void joinRoom_noAuthRequired_returns200() throws Exception {
        // This endpoint is public - guests don't need to log in
        mockMvc.perform(get("/api/room/join/" + testRoomId))
                .andExpect(status().isOk());
    }
}
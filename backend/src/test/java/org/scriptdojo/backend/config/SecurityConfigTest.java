package org.scriptdojo.backend.config;

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
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_USERNAME = "secconfig_user";

    @BeforeAll
    void setup() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USERNAME,
                                "password", "Password1!",
                                "email", "secconfig@test.com"
                        ))))
                .andExpect(status().isOk());
    }

    // ── Public endpoints ──────────────────────────────────

    @Test
    @DisplayName("GET /api/auth/register endpoint is publicly accessible")
    void authRegisterEndpoint_isPublic() throws Exception {
        // Already implicitly tested by setup, but explicitly confirm no auth needed
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "public_check_user",
                                "password", "Password1!",
                                "email", "publiccheck@test.com"
                        ))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/room/join/** is publicly accessible without authentication")
    void roomJoinEndpoint_isPublic() {
        // Throws because room doesn't exist, but NOT a 302 redirect — proves the endpoint is public
        assertThrows(Exception.class, () ->
                mockMvc.perform(get("/api/room/join/nonexistent123")));
    }

    @Test
    @DisplayName("POST /perform_login endpoint is publicly accessible")
    void performLoginEndpoint_isPublic() throws Exception {
        // Bad credentials returns failure redirect, not a security block
        mockMvc.perform(post("/perform_login")
                        .param("username", "wrong")
                        .param("password", "wrong"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    // ── Protected endpoints redirect to login ─────────────

    @Test
    @DisplayName("GET /api/files - unauthenticated request redirects to login")
    void apiFiles_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isFound());
    }

    @Test
    @DisplayName("POST /api/files - unauthenticated request redirects to login")
    void apiFilesPost_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isFound());
    }

    @Test
    @DisplayName("POST /api/room/create - unauthenticated request redirects to login")
    void apiRoomCreate_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/room/create")
                        .param("fileId", "1"))
                .andExpect(status().isFound());
    }

    @Test
    @DisplayName("POST /api/permissions/grant-edit - unauthenticated request redirects to login")
    void apiGrantEdit_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/permissions/grant-edit")
                        .param("fileId", "1")
                        .param("guestName", "guest"))
                .andExpect(status().isFound());
    }

    @Test
    @DisplayName("GET /api/user/me - unauthenticated request redirects to login")
    void apiUserMe_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isFound());
    }

    // ── Authenticated access works ────────────────────────

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/files - authenticated request returns 200")
    void apiFiles_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk());
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/user/me - authenticated request returns 200")
    void apiUserMe_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isOk());
    }

    // ── Password encoder ──────────────────────────────────

    @Test
    @DisplayName("POST /perform_login - correct credentials redirect to dashboard")
    void performLogin_correctCredentials_redirectsToDashboard() throws Exception {
        mockMvc.perform(post("/perform_login")
                        .param("username", TEST_USERNAME)
                        .param("password", "Password1!"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/dashboard"));
    }

    // ── Logout ────────────────────────────────────────────

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("POST /logout - authenticated user is redirected to login page")
    void logout_authenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/logout"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }
}
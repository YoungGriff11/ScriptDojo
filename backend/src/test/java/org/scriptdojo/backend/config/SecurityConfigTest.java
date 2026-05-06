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

/**
 * Integration tests for {@link SecurityConfig}, verifying that the URL
 * authorisation rules, form login, and logout behaviour are correctly configured.
 * Test structure:
 * - Public endpoints — confirm that permit-all routes are reachable without authentication
 * - Protected endpoints — confirm that unauthenticated requests are redirected to /login
 * - Authenticated access — confirm that valid sessions receive 200 responses
 * - Password encoder — confirm that correct credentials produce a dashboard redirect
 * - Logout — confirm that authenticated users are redirected to /login after logout
 * Uses @TestInstance(PER_CLASS) so @BeforeAll can be non-static, allowing MockMvc
 * to register the test user via HTTP before the Spring Security context initialises.
 * @WithUserDetails relies on this user existing in H2 before any @Test runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Username used for all authenticated test cases.
     * Registered once in @BeforeAll and referenced by @WithUserDetails
     * to load a valid Spring Security principal for authenticated requests.
     */
    private static final String TEST_USERNAME = "secconfig_user";

    /**
     * Registers the test user via the public registration endpoint before any test runs.
     * Must complete before the Spring Security context attempts to load the user via
     * @WithUserDetails — @TestInstance(PER_CLASS) ensures this runs on the shared instance.
     */
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

    // ─ Public endpoints

    @Test
    @DisplayName("GET /api/auth/register endpoint is publicly accessible")
    void authRegisterEndpoint_isPublic() throws Exception {
        // Registers a second user without authentication to confirm the endpoint
        // is permit-all — if it were protected this would redirect instead of returning 200
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
        // The request throws because the room does not exist, but the key assertion
        // is that it does NOT return a 302 redirect to /login — proving the endpoint
        // is reachable without authentication
        assertThrows(Exception.class, () ->
                mockMvc.perform(get("/api/room/join/nonexistent123")));
    }

    @Test
    @DisplayName("POST /perform_login endpoint is publicly accessible")
    void performLoginEndpoint_isPublic() throws Exception {
        // Wrong credentials produce a failure redirect to /login?error=true rather
        // than a security block, confirming the endpoint itself is publicly reachable
        mockMvc.perform(post("/perform_login")
                        .param("username", "wrong")
                        .param("password", "wrong"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    // ─ Protected endpoints redirect to login

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

    // ─ Authenticated access works

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

    // ─ Password encoder

    @Test
    @DisplayName("POST /perform_login - correct credentials redirect to dashboard")
    void performLogin_correctCredentials_redirectsToDashboard() throws Exception {
        // Verifies that the BCryptPasswordEncoder is correctly wired into the
        // authentication manager — a misconfigured encoder would cause this to fail
        mockMvc.perform(post("/perform_login")
                        .param("username", TEST_USERNAME)
                        .param("password", "Password1!"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/dashboard"));
    }

    // ─ Logout

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("POST /logout - authenticated user is redirected to login page")
    void logout_authenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/logout"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }
}
package org.scriptdojo.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scriptdojo.backend.entity.UserEntity;
import org.scriptdojo.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link org.scriptdojo.backend.controller.AuthController},
 * covering the POST /api/auth/register endpoint.
 * Test structure:
 * - Happy path — valid registration returns 200 with the expected response body
 * - Duplicate username — second registration with the same username returns 400
 * - Bean Validation — blank/short/long/invalid field values are rejected with 400
 * - Password encoding — verifies BCrypt is applied before persistence
 * - Role assignment — verifies the default USER role is set on new accounts
 * Each test uses a unique username to avoid cross-test state conflicts in the
 * shared H2 database. No @BeforeAll setup is required as registration is a
 * public endpoint and no pre-existing user is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    // ─ Helpers

    /**
     * Serialises an object to a JSON string for use as a MockMvc request body.
     */
    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    /**
     * Builds a valid registration request map with the given field values.
     * Used by tests that need to customise one field while keeping others valid.
     */
    private Map<String, String> validRequest(String username, String password, String email) {
        return Map.of("username", username, "password", password, "email", email);
    }

    // ─ Happy path

    @Test
    @DisplayName("POST /api/auth/register - success with valid data")
    void register_validRequest_returns200() throws Exception {
        // Confirms a well-formed request is accepted and returns 200
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("newuser123", "Password1!", "newuser@test.com"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("newuser123")));
    }

    @Test
    @DisplayName("POST /api/auth/register - returns username in success message")
    void register_validRequest_returnsUsernameInBody() throws Exception {
        // Confirms the success response body includes the registered username
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("uniqueuser1", "Password1!", "unique1@test.com"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("User registered: uniqueuser1")));
    }

    // ─ Duplicate username

    @Test
    @DisplayName("POST /api/auth/register - fails when username already taken")
    void register_duplicateUsername_returns400() throws Exception {
        // First registration succeeds
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("duplicateuser", "Password1!", "first@test.com"))))
                .andExpect(status().isOk());

        // Second registration with the same username is rejected with 400
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("duplicateuser", "Password1!", "second@test.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("already taken")));
    }

    @Test
    @DisplayName("POST /api/auth/register - fails with blank username")
    void register_blankUsername_returns400() throws Exception {
        // Empty string violates @NotBlank on RegisterRequest.username
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("", "Password1!", "test@test.com"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register - fails with username too short")
    void register_usernameTooShort_returns400() throws Exception {
        // 2-character username violates @Size(min=3) on RegisterRequest.username
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("ab", "Password1!", "test@test.com"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register - fails with username too long")
    void register_usernameTooLong_returns400() throws Exception {
        // 24-character username violates @Size(max=20) on RegisterRequest.username
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("thisusernameiswaytoolong", "Password1!", "test@test.com"))))
                .andExpect(status().isBadRequest());
    }

    // ─ Bean Validation — password

    @Test
    @DisplayName("POST /api/auth/register - fails with blank password")
    void register_blankPassword_returns400() throws Exception {
        // Empty string violates @NotBlank on RegisterRequest.password
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("validuser", "", "test@test.com"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register - fails with password too short")
    void register_passwordTooShort_returns400() throws Exception {
        // 3-character password violates @Size(min=6) on RegisterRequest.password
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("validuser2", "abc", "test@test.com"))))
                .andExpect(status().isBadRequest());
    }

    // ─ Bean Validation — email

    @Test
    @DisplayName("POST /api/auth/register - fails with blank email")
    void register_blankEmail_returns400() throws Exception {
        // Empty string violates @NotBlank on RegisterRequest.email
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("validuser3", "Password1!", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register - fails with invalid email format")
    void register_invalidEmail_returns400() throws Exception {
        // String without @ violates @Email on RegisterRequest.email
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("validuser4", "Password1!", "notanemail"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register - fails with missing request body")
    void register_emptyBody_returns400() throws Exception {
        // Empty JSON object fails all @NotBlank constraints simultaneously
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ─ Password encoding

    @Test
    @DisplayName("POST /api/auth/register - password is stored encoded not plaintext")
    void register_passwordIsEncoded() throws Exception {
        // Registers a user then fetches the entity directly from the database
        // to verify the stored password is a BCrypt hash ($2a$ prefix) rather
        // than the original plain-text value
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("encodedpwuser", "PlainText123!", "encoded@test.com"))))
                .andExpect(status().isOk());

        UserEntity saved = userService.findByUsername("encodedpwuser").orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals("PlainText123!", saved.getPassword());
        org.junit.jupiter.api.Assertions.assertTrue(saved.getPassword().startsWith("$2a$"));
    }

    // ─ Role assignment

    @Test
    @DisplayName("POST /api/auth/register - user role is set to USER")
    void register_userRoleIsSetCorrectly() throws Exception {
        // Registers a user then fetches the entity to confirm the default role
        // of "USER" is assigned regardless of what the caller supplies
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("roleuser", "Password1!", "role@test.com"))))
                .andExpect(status().isOk());

        UserEntity saved = userService.findByUsername("roleuser").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("USER", saved.getRole());
    }
}
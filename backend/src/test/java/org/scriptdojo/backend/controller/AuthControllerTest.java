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

    // ── Helpers ──────────────────────────────────────────

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private Map<String, String> validRequest(String username, String password, String email) {
        return Map.of("username", username, "password", password, "email", email);
    }

    // ── Tests ────────────────────────────────────────────

    // Tests that a valid registration request returns HTTP 200
    // by sending a POST with a valid username, password and email
    @Test
    @DisplayName("POST /api/auth/register - success with valid data")
    void register_validRequest_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("newuser123", "Password1!", "newuser@test.com"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("newuser123")));
    }

    // Tests that the success response body contains the registered username
    // by checking the response string includes "User registered: uniqueuser1"
    @Test
    @DisplayName("POST /api/auth/register - returns username in success message")
    void register_validRequest_returnsUsernameInBody() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("uniqueuser1", "Password1!", "unique1@test.com"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("User registered: uniqueuser1")));
    }

    // Tests that registering with an already taken username returns HTTP 400
    // by registering the same username twice and expecting a bad request on the second attempt
    @Test
    @DisplayName("POST /api/auth/register - fails when username already taken")
    void register_duplicateUsername_returns400() throws Exception {
        // Register first time
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("duplicateuser", "Password1!", "first@test.com"))))
                .andExpect(status().isOk());

        // Register again with same username — should be rejected
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("duplicateuser", "Password1!", "second@test.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("already taken")));
    }

    // Tests that a blank username is rejected with HTTP 400
    // by sending an empty string as the username field
    @Test
    @DisplayName("POST /api/auth/register - fails with blank username")
    void register_blankUsername_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("", "Password1!", "test@test.com"))))
                .andExpect(status().isBadRequest());
    }

    // Tests that a username shorter than 3 characters is rejected with HTTP 400
    // by sending a 2-character username which violates the @Size(min=3) constraint
    @Test
    @DisplayName("POST /api/auth/register - fails with username too short")
    void register_usernameTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("ab", "Password1!", "test@test.com"))))
                .andExpect(status().isBadRequest());
    }

    // Tests that a username longer than 20 characters is rejected with HTTP 400
    // by sending a 24-character username which violates the @Size(max=20) constraint
    @Test
    @DisplayName("POST /api/auth/register - fails with username too long")
    void register_usernameTooLong_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("thisusernameiswaytoolong", "Password1!", "test@test.com"))))
                .andExpect(status().isBadRequest());
    }

    // Tests that a blank password is rejected with HTTP 400
    // by sending an empty string as the password field
    @Test
    @DisplayName("POST /api/auth/register - fails with blank password")
    void register_blankPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("validuser", "", "test@test.com"))))
                .andExpect(status().isBadRequest());
    }

    // Tests that a password shorter than 6 characters is rejected with HTTP 400
    // by sending a 3-character password which violates the @Size(min=6) constraint
    @Test
    @DisplayName("POST /api/auth/register - fails with password too short")
    void register_passwordTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("validuser2", "abc", "test@test.com"))))
                .andExpect(status().isBadRequest());
    }

    // Tests that a blank email is rejected with HTTP 400
    // by sending an empty string as the email field
    @Test
    @DisplayName("POST /api/auth/register - fails with blank email")
    void register_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("validuser3", "Password1!", ""))))
                .andExpect(status().isBadRequest());
    }

    // Tests that an invalid email format is rejected with HTTP 400
    // by sending a string without @ symbol which violates the @Email constraint
    @Test
    @DisplayName("POST /api/auth/register - fails with invalid email format")
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("validuser4", "Password1!", "notanemail"))))
                .andExpect(status().isBadRequest());
    }

    // Tests that an empty JSON body is rejected with HTTP 400
    // by sending {} which has no fields and fails all @NotBlank constraints
    @Test
    @DisplayName("POST /api/auth/register - fails with missing request body")
    void register_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // Tests that the duplicate username error message includes the actual username
    // by checking the response body contains the attempted username "erroruser"
    @Test
    @DisplayName("POST /api/auth/register - error message contains username when taken")
    void register_duplicateUsername_errorMessageContainsUsername() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("erroruser", "Password1!", "error@test.com"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("erroruser", "Password1!", "error2@test.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("erroruser")));
    }

    // Tests that the password is BCrypt encoded in the database, not stored as plaintext
    // by registering a user then fetching them from the DB and checking the password hash prefix
    @Test
    @DisplayName("POST /api/auth/register - password is stored encoded not plaintext")
    void register_passwordIsEncoded() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("encodedpwuser", "PlainText123!", "encoded@test.com"))))
                .andExpect(status().isOk());

        UserEntity saved = userService.findByUsername("encodedpwuser").orElseThrow();
        // Password should be BCrypt encoded, not the original plaintext
        org.junit.jupiter.api.Assertions.assertNotEquals("PlainText123!", saved.getPassword());
        org.junit.jupiter.api.Assertions.assertTrue(saved.getPassword().startsWith("$2a$"));
    }

    // Tests that a newly registered user is assigned the role "USER"
    // by registering a user and fetching them from the DB to check the role field
    @Test
    @DisplayName("POST /api/auth/register - user role is set to USER")
    void register_userRoleIsSetCorrectly() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest("roleuser", "Password1!", "role@test.com"))))
                .andExpect(status().isOk());

        UserEntity saved = userService.findByUsername("roleuser").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("USER", saved.getRole());
    }
}
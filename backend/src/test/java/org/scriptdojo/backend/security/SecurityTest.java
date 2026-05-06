package org.scriptdojo.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Collection;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link CustomUserDetailsService} and {@link CustomUserDetails},
 * verifying that the Spring Security user loading pipeline correctly resolves
 * registered accounts and exposes the expected identity fields.
 * Test structure:
 * - CustomUserDetailsService — correct type returned, username populated, ID non-null,
 *                              UsernameNotFoundException thrown for unknown users
 * - CustomUserDetails        — email, authorities, password encoding, account status
 *                              flags, and underlying UserEntity access
 * Uses @TestInstance(PER_CLASS) so @BeforeAll can be non-static, registering the test
 * user via MockMvc before the Spring Security context attempts to load them.
 * All tests call loadUserByUsername() directly rather than going through the HTTP
 * login flow, keeping the assertions focused on the service and details class behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    /**
     * Credentials used for all tests. Defined as constants so that assertions
     * comparing loaded values against expected values remain readable and consistent.
     */
    private static final String TEST_USERNAME = "sectest_user";
    private static final String TEST_PASSWORD = "Password1!";
    private static final String TEST_EMAIL    = "sectest@test.com";

    /**
     * Registers the test user via the public registration endpoint before any tests run.
     * Required so loadUserByUsername() can resolve the account from the H2 database.
     */
    @BeforeAll
    void setup() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USERNAME,
                                "password", TEST_PASSWORD,
                                "email",    TEST_EMAIL
                        ))))
                .andExpect(status().isOk());
    }

    // ─ CustomUserDetailsService

    @Test
    @DisplayName("loadUserByUsername - returns CustomUserDetails for existing user")
    void loadUserByUsername_existingUser_returnsCustomUserDetails() {
        // Confirms the service returns a CustomUserDetails instance rather than a
        // generic Spring Security User object
        UserDetails details = userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertInstanceOf(CustomUserDetails.class, details);
    }

    @Test
    @DisplayName("loadUserByUsername - returned details has correct username")
    void loadUserByUsername_existingUser_hasCorrectUsername() {
        // Verifies the username field on the returned details matches the registered name
        UserDetails details = userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertEquals(TEST_USERNAME, details.getUsername());
    }

    @Test
    @DisplayName("loadUserByUsername - returned details has a non-null ID")
    void loadUserByUsername_existingUser_hasNonNullId() {
        // Confirms the database user ID is populated on the CustomUserDetails instance —
        // controllers depend on this for ownership checks and file associations
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertNotNull(details.getId());
    }

    @Test
    @DisplayName("loadUserByUsername - throws UsernameNotFoundException for unknown user")
    void loadUserByUsername_unknownUser_throwsException() {
        // Confirms Spring Security receives the correct exception type for an unknown
        // username, which triggers a login failure response rather than a 500 error
        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsService.loadUserByUsername("does_not_exist"));
    }

    // ─ CustomUserDetails

    @Test
    @DisplayName("CustomUserDetails - getEmail returns correct email")
    void customUserDetails_getEmail_returnsCorrectEmail() {
        // Verifies the email field is correctly propagated from the UserEntity
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertEquals(TEST_EMAIL, details.getEmail());
    }

    @Test
    @DisplayName("CustomUserDetails - getAuthorities contains USER role")
    void customUserDetails_getAuthorities_containsUserRole() {
        // Confirms exactly one authority is granted and it matches the default USER role
        // assigned to all self-registered accounts
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        Collection<? extends GrantedAuthority> authorities = details.getAuthorities();
        assertEquals(1, authorities.size());
        assertEquals("USER", authorities.iterator().next().getAuthority());
    }

    @Test
    @DisplayName("CustomUserDetails - password is BCrypt encoded")
    void customUserDetails_password_isBcryptEncoded() {
        // Verifies the stored password is a BCrypt hash ($2a$ prefix) rather than
        // the plain-text value — confirms UserService applies encoding before persistence
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertTrue(details.getPassword().startsWith("$2a$"));
    }

    @Test
    @DisplayName("CustomUserDetails - account is non-expired")
    void customUserDetails_isAccountNonExpired_returnsTrue() {
        // ScriptDojo does not implement account expiry — all accounts return true
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertTrue(details.isAccountNonExpired());
    }

    @Test
    @DisplayName("CustomUserDetails - account is non-locked")
    void customUserDetails_isAccountNonLocked_returnsTrue() {
        // ScriptDojo does not implement account locking — all accounts return true
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertTrue(details.isAccountNonLocked());
    }

    @Test
    @DisplayName("CustomUserDetails - credentials are non-expired")
    void customUserDetails_isCredentialsNonExpired_returnsTrue() {
        // ScriptDojo does not implement credential expiry — all accounts return true
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertTrue(details.isCredentialsNonExpired());
    }

    @Test
    @DisplayName("CustomUserDetails - account is enabled")
    void customUserDetails_isEnabled_returnsTrue() {
        // ScriptDojo does not implement account disabling — all accounts return true
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertTrue(details.isEnabled());
    }

    @Test
    @DisplayName("CustomUserDetails - getUser returns underlying UserEntity")
    void customUserDetails_getUser_returnsUserEntity() {
        // Verifies the underlying UserEntity is accessible and correctly associated
        // with the loaded account — used by call sites that need the full entity
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertNotNull(details.getUser());
        assertEquals(TEST_USERNAME, details.getUser().getUsername());
    }
}
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

    private static final String TEST_USERNAME = "sectest_user";
    private static final String TEST_PASSWORD = "Password1!";
    private static final String TEST_EMAIL    = "sectest@test.com";

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

    // ── CustomUserDetailsService ──────────────────────────

    @Test
    @DisplayName("loadUserByUsername - returns CustomUserDetails for existing user")
    void loadUserByUsername_existingUser_returnsCustomUserDetails() {
        UserDetails details = userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertInstanceOf(CustomUserDetails.class, details);
    }

    @Test
    @DisplayName("loadUserByUsername - returned details has correct username")
    void loadUserByUsername_existingUser_hasCorrectUsername() {
        UserDetails details = userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertEquals(TEST_USERNAME, details.getUsername());
    }

    @Test
    @DisplayName("loadUserByUsername - returned details has a non-null ID")
    void loadUserByUsername_existingUser_hasNonNullId() {
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertNotNull(details.getId());
    }

    @Test
    @DisplayName("loadUserByUsername - throws UsernameNotFoundException for unknown user")
    void loadUserByUsername_unknownUser_throwsException() {
        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsService.loadUserByUsername("does_not_exist"));
    }

    // ── CustomUserDetails ─────────────────────────────────

    @Test
    @DisplayName("CustomUserDetails - getEmail returns correct email")
    void customUserDetails_getEmail_returnsCorrectEmail() {
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertEquals(TEST_EMAIL, details.getEmail());
    }

    @Test
    @DisplayName("CustomUserDetails - getAuthorities contains USER role")
    void customUserDetails_getAuthorities_containsUserRole() {
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        Collection<? extends GrantedAuthority> authorities = details.getAuthorities();
        assertEquals(1, authorities.size());
        assertEquals("USER", authorities.iterator().next().getAuthority());
    }

    @Test
    @DisplayName("CustomUserDetails - password is BCrypt encoded")
    void customUserDetails_password_isBcryptEncoded() {
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertTrue(details.getPassword().startsWith("$2a$"));
    }

    @Test
    @DisplayName("CustomUserDetails - account is non-expired")
    void customUserDetails_isAccountNonExpired_returnsTrue() {
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertTrue(details.isAccountNonExpired());
    }

    @Test
    @DisplayName("CustomUserDetails - account is non-locked")
    void customUserDetails_isAccountNonLocked_returnsTrue() {
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertTrue(details.isAccountNonLocked());
    }

    @Test
    @DisplayName("CustomUserDetails - credentials are non-expired")
    void customUserDetails_isCredentialsNonExpired_returnsTrue() {
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertTrue(details.isCredentialsNonExpired());
    }

    @Test
    @DisplayName("CustomUserDetails - account is enabled")
    void customUserDetails_isEnabled_returnsTrue() {
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertTrue(details.isEnabled());
    }

    @Test
    @DisplayName("CustomUserDetails - getUser returns underlying UserEntity")
    void customUserDetails_getUser_returnsUserEntity() {
        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(TEST_USERNAME);
        assertNotNull(details.getUser());
        assertEquals(TEST_USERNAME, details.getUser().getUsername());
    }
}
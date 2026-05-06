package org.scriptdojo.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.scriptdojo.backend.entity.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link UserService}, verifying user persistence and
 * lookup behaviour against the H2 in-memory database.
 * Test structure:
 * - saveUser      — user retrievable after save, BCrypt encoding applied, encoded password
 *                   matches plaintext, username and email persisted correctly, ID assigned
 * - findByUsername — empty Optional for unknown username, correct user returned for known
 *                    username, email correctly populated on the returned entity
 * Uses @SpringBootTest to load the full application context with H2 so that real JPA
 * persistence and the BCryptPasswordEncoder bean are exercised rather than mocked.
 * Each test uses a unique username to avoid conflicts with other tests in the shared
 * H2 database — no @BeforeEach cleanup is needed as usernames never collide.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserServiceTest {

    @Autowired
    private UserService userService;

    // Injected to verify BCrypt hash correctness in the encoding tests
    @Autowired
    private PasswordEncoder passwordEncoder;

    // ─ saveUser

    @Test
    @DisplayName("saveUser - saved user can be found by username")
    void saveUser_savedUserCanBeFound() {
        // Confirms the user is durably persisted and retrievable via findByUsername
        UserEntity user = new UserEntity();
        user.setUsername("userservice_test1");
        user.setPassword("plaintext");
        user.setEmail("userservice1@test.com");
        user.setRole("USER");

        userService.saveUser(user);

        Optional<UserEntity> found = userService.findByUsername("userservice_test1");
        assertTrue(found.isPresent());
    }

    @Test
    @DisplayName("saveUser - password is BCrypt encoded on save")
    void saveUser_passwordIsBcryptEncoded() {
        // Confirms UserService applies BCrypt hashing before persistence —
        // the $2a$ prefix identifies a BCrypt hash
        UserEntity user = new UserEntity();
        user.setUsername("userservice_test2");
        user.setPassword("plainpassword");
        user.setEmail("userservice2@test.com");
        user.setRole("USER");

        UserEntity saved = userService.saveUser(user);

        assertTrue(saved.getPassword().startsWith("$2a$"));
    }

    @Test
    @DisplayName("saveUser - BCrypt encoded password matches original plaintext")
    void saveUser_encodedPasswordMatchesPlaintext() {
        // Confirms the hash is a valid BCrypt encoding of the original password —
        // this is what Spring Security verifies during login credential checking
        UserEntity user = new UserEntity();
        user.setUsername("userservice_test3");
        user.setPassword("mySecret123");
        user.setEmail("userservice3@test.com");
        user.setRole("USER");

        UserEntity saved = userService.saveUser(user);

        assertTrue(passwordEncoder.matches("mySecret123", saved.getPassword()));
    }

    @Test
    @DisplayName("saveUser - saved user has correct username")
    void saveUser_savedUserHasCorrectUsername() {
        // Confirms the username field is correctly persisted and returned
        UserEntity user = new UserEntity();
        user.setUsername("userservice_test4");
        user.setPassword("pass");
        user.setEmail("userservice4@test.com");
        user.setRole("USER");

        UserEntity saved = userService.saveUser(user);

        assertEquals("userservice_test4", saved.getUsername());
    }

    @Test
    @DisplayName("saveUser - saved user has correct email")
    void saveUser_savedUserHasCorrectEmail() {
        // Confirms the email field is correctly persisted and returned
        UserEntity user = new UserEntity();
        user.setUsername("userservice_test5");
        user.setPassword("pass");
        user.setEmail("userservice5@test.com");
        user.setRole("USER");

        UserEntity saved = userService.saveUser(user);

        assertEquals("userservice5@test.com", saved.getEmail());
    }

    @Test
    @DisplayName("saveUser - saved user is assigned an ID")
    void saveUser_savedUserHasId() {
        // Confirms the database assigns a non-null generated ID on first save
        UserEntity user = new UserEntity();
        user.setUsername("userservice_test6");
        user.setPassword("pass");
        user.setEmail("userservice6@test.com");
        user.setRole("USER");

        UserEntity saved = userService.saveUser(user);

        assertNotNull(saved.getId());
    }

    // ─ findByUsername

    @Test
    @DisplayName("findByUsername - returns empty Optional for unknown username")
    void findByUsername_unknownUser_returnsEmpty() {
        // Confirms an empty Optional is returned rather than null or an exception
        // for a username that has never been registered
        Optional<UserEntity> result = userService.findByUsername("does_not_exist");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByUsername - returns correct user for known username")
    void findByUsername_knownUser_returnsCorrectUser() {
        // Confirms the returned Optional contains the correct user entity
        UserEntity user = new UserEntity();
        user.setUsername("userservice_test7");
        user.setPassword("pass");
        user.setEmail("userservice7@test.com");
        user.setRole("USER");

        userService.saveUser(user);

        Optional<UserEntity> found = userService.findByUsername("userservice_test7");
        assertTrue(found.isPresent());
        assertEquals("userservice_test7", found.get().getUsername());
    }

    @Test
    @DisplayName("findByUsername - returned user has correct email")
    void findByUsername_returnsUserWithCorrectEmail() {
        // Confirms all fields are correctly hydrated on the returned entity,
        // not just the username used to look it up
        UserEntity user = new UserEntity();
        user.setUsername("userservice_test8");
        user.setPassword("pass");
        user.setEmail("userservice8@test.com");
        user.setRole("USER");

        userService.saveUser(user);

        Optional<UserEntity> found = userService.findByUsername("userservice_test8");
        assertTrue(found.isPresent());
        assertEquals("userservice8@test.com", found.get().getEmail());
    }
}
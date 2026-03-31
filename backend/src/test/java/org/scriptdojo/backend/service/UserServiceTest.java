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

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ── saveUser ──────────────────────────────────────────

    @Test
    @DisplayName("saveUser - saved user can be found by username")
    void saveUser_savedUserCanBeFound() {
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
        UserEntity user = new UserEntity();
        user.setUsername("userservice_test6");
        user.setPassword("pass");
        user.setEmail("userservice6@test.com");
        user.setRole("USER");

        UserEntity saved = userService.saveUser(user);

        assertNotNull(saved.getId());
    }

    // ── findByUsername ────────────────────────────────────

    @Test
    @DisplayName("findByUsername - returns empty Optional for unknown username")
    void findByUsername_unknownUser_returnsEmpty() {
        Optional<UserEntity> result = userService.findByUsername("does_not_exist");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByUsername - returns correct user for known username")
    void findByUsername_knownUser_returnsCorrectUser() {
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
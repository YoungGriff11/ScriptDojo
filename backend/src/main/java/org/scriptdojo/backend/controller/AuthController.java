package org.scriptdojo.backend.controller;

import org.scriptdojo.backend.service.dto.RegisterRequest;
import org.scriptdojo.backend.entity.UserEntity;
import org.scriptdojo.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:8080")
public class AuthController {
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        if (userService.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.badRequest()
                    .body("Error: Username '" + request.username() + "' is already taken.");
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPassword(request.password());
        user.setEmail(request.email());
        user.setRole("USER");

        userService.saveUser(user);

        return ResponseEntity.ok("User registered: " + request.username());
    }
}

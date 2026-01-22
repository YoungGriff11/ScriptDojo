package org.scriptdojo.backend.controller;

import lombok.RequiredArgsConstructor;
import org.scriptdojo.backend.entity.UserEntity;
import org.scriptdojo.backend.service.UserService;
import org.scriptdojo.backend.service.dto.UserInfoDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserInfoDTO> getCurrentUser() {
        UserEntity user = userService.getCurrentUser();
        return ResponseEntity.ok(new UserInfoDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail()
        ));
    }
}
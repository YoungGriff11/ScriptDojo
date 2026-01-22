package org.scriptdojo.backend.security;

import org.scriptdojo.backend.entity.UserEntity;
import org.scriptdojo.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // ────────────────────────────────────────────────────────────────
        // ADD THE DEBUG LOGGING RIGHT HERE ↓↓↓
        CustomUserDetails details = new CustomUserDetails(user);

        System.out.println("DEBUG: CustomUserDetailsService loaded user: " + username +
                " | Returning class: " + details.getClass().getName() +
                " | User ID: " + details.getId());

        return details;
        // ────────────────────────────────────────────────────────────────
    }
}
package org.scriptdojo.backend.security;

import org.scriptdojo.backend.entity.UserEntity;
import org.scriptdojo.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security {@link UserDetailsService} implementation that loads user
 * accounts from the database during authentication.
 * Called automatically by the Spring Security authentication pipeline when a
 * login attempt is made. Retrieves the {@link UserEntity} by username and wraps
 * it in a {@link CustomUserDetails} instance, which is then stored in the
 * security context for the duration of the session.
 * Wired into the authentication manager in
 * {@link org.scriptdojo.backend.config.SecurityConfig#authenticationManager}.
 * Note: the System.out.println debug statement should be removed or replaced
 * with a proper SLF4J log.debug() call before production deployment.
 */
@Service
@RequiredArgsConstructor // Lombok: generates constructor injecting userRepository
public class CustomUserDetailsService implements UserDetailsService {

    // Used to look up the user account by username during authentication
    private final UserRepository userRepository;

    /**
     * Loads a {@link UserDetails} instance for the given username.
     * Called by Spring Security during form login to retrieve the account
     * against which the submitted credentials will be verified.
     * @param username the username submitted in the login form
     * @return a {@link CustomUserDetails} wrapping the matching {@link UserEntity}
     * @throws UsernameNotFoundException if no account exists for the given username,
     *                                   causing Spring Security to return a login failure
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        CustomUserDetails details = new CustomUserDetails(user);

        System.out.println("DEBUG: CustomUserDetailsService loaded user: " + username +
                " | Returning class: " + details.getClass().getName() +
                " | User ID: " + details.getId());

        return details;
    }
}
package org.scriptdojo.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public SecurityConfig(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // === PUBLIC ENDPOINTS (anyone can access) ===
                        .requestMatchers(
                                "/",
                                "/welcome.html", "/login.html", "/signup.html",
                                "/css/**", "/js/**", "/images/**",
                                "/room/**",                  // CRITICAL: share links
                                "/room-guest.html",          // CRITICAL: guest editor page
                                "/ws/**",                    // WebSocket endpoint
                                "/api/room/create",          // generate share link
                                "/api/room/*/grant-edit"     // future permission endpoint
                        ).permitAll()

                        // PROTECTED ENDPOINTS (require login)
                        .requestMatchers("/dashboard.html", "/editor.html", "/api/files/**").authenticated()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/perform_login")
                        .defaultSuccessUrl("/dashboard.html", true)    // ← now welcome page, not dashboard
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/perform_logout")
                        .logoutSuccessUrl("/welcome.html")
                        .permitAll()
                );

        return http.build();
    }
}
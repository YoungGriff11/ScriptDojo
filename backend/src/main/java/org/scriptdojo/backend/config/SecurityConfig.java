package org.scriptdojo.backend.config;

import org.scriptdojo.backend.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Central Spring Security configuration for ScriptDojo.
 *
 * Responsibilities:
 * - CORS policy: defines which origins, methods, and headers are permitted
 * - URL access rules: distinguishes public endpoints from authenticated-only ones
 * - Form login: configures the login/logout flow used by the React frontend
 * - Password encoding: registers BCrypt as the application-wide password hasher
 * - Authentication manager: wires the custom user details service into Spring Security
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor // Lombok: generates a constructor injecting customUserDetailsService
public class SecurityConfig {

    // Loads user accounts from the database for authentication checks
    private final CustomUserDetailsService customUserDetailsService;

    /**
     * Defines the HTTP security filter chain — the main security ruleset applied to every request.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ── CORS ──────────────────────────────────────────────────────────────────
                // Configures Cross-Origin Resource Sharing so the React frontend (running on
                // a different port/domain) can make authenticated requests to the API.
                .cors(cors -> cors.configurationSource(request -> {
                    var config = new org.springframework.web.cors.CorsConfiguration();

                    // Allowed origins: Vite dev server, Docker/local production, and the
                    // live domain. Requests from any other origin will be rejected by the browser.
                    config.setAllowedOrigins(java.util.List.of(
                            "http://localhost:5173",   // Vite dev server
                            "http://localhost:8080",   // Docker / production
                            "https://scriptdojo.ie"   // Production domain (when live)
                    ));

                    config.setAllowedMethods(java.util.List.of("GET","POST","PUT","DELETE","OPTIONS"));
                    config.setAllowedHeaders(java.util.List.of("*")); // Accept any request header
                    config.setAllowCredentials(true); // Required to forward session cookies cross-origin
                    return config;
                }))

                // ── CSRF ──────────────────────────────────────────────────────────────────
                // CSRF protection is disabled because the React SPA manages its own session
                // cookie and does not rely on server-rendered forms.
                .csrf(csrf -> csrf.disable())

                // ── URL AUTHORISATION RULES ───────────────────────────────────────────────
                // Requests are evaluated top-to-bottom; the first matching rule wins.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                // React static assets served directly by Spring Boot
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/favicon.ico",
                                "/vite.svg",

                                // React client-side routes — all map to index.html via SpaController
                                // so the React Router can handle them in the browser
                                "/login",
                                "/signup",
                                "/dashboard",
                                "/editor",
                                "/room/**",

                                // Public REST API endpoints (registration, login, room joining)
                                "/api/auth/**",
                                "/api/room/join/**",

                                // WebSocket handshake endpoint — must be public so guests can connect
                                "/ws/**",

                                // Legacy HTML pages kept for backwards compatibility
                                "/login.html",
                                "/signup.html",
                                "/room-guest",
                                "/static/**"
                        ).permitAll()                  // All of the above are accessible without authentication
                        .anyRequest().authenticated()  // Everything else requires a valid session
                )

                // ── FORM LOGIN ────────────────────────────────────────────────────────────
                // Configures Spring Security's built-in form-based login mechanism.
                // The React frontend POSTs credentials to /perform_login and reads the redirect.
                .formLogin(form -> form
                        .loginPage("/login")                        // React route that renders the login UI
                        .loginProcessingUrl("/perform_login")       // Endpoint that processes the credentials
                        .defaultSuccessUrl("/dashboard", true)      // Redirect here on successful login
                        .failureUrl("/login?error=true")            // Redirect here on failed login
                        .permitAll()
                )

                // ── LOGOUT ───────────────────────────────────────────────────────────────
                // Spring Security invalidates the session and redirects to /login on logout.
                .logout(logout -> logout
                        .permitAll()
                        .logoutSuccessUrl("/login")
                );

        return http.build();
    }

    /**
     * Registers BCryptPasswordEncoder as the application-wide password hashing strategy.
     * BCrypt is used both when storing new passwords and when verifying login attempts.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Builds and exposes the AuthenticationManager bean used by the login flow.
     *
     * Wires together:
     * - CustomUserDetailsService: fetches the User entity from the database by username
     * - BCryptPasswordEncoder: verifies the submitted password against the stored hash
     *
     * The AuthenticationConfiguration parameter is declared to satisfy Spring's dependency
     * graph even though the manager is built manually via AuthenticationManagerBuilder.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            HttpSecurity http,
            AuthenticationConfiguration authenticationConfiguration) throws Exception {

        AuthenticationManagerBuilder authBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder
                .userDetailsService(customUserDetailsService)
                .passwordEncoder(passwordEncoder());

        return authBuilder.build();
    }
}
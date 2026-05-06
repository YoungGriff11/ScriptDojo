package org.scriptdojo.backend.security;

import org.scriptdojo.backend.entity.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom implementation of Spring Security's {@link UserDetails} interface
 * that wraps a {@link UserEntity} and exposes it to the Spring Security context.
 * Produced by {@link CustomUserDetailsService} during authentication and stored
 * in the security context for the duration of the user's session.
 * Extends the standard UserDetails contract with additional accessors (getId,
 * getEmail, getUser) so that controllers can retrieve the full user identity
 * from the Authentication principal without a separate database lookup.
 */
public class CustomUserDetails implements UserDetails {

    // The underlying user entity loaded from the database during authentication
    private final UserEntity user;

    public CustomUserDetails(UserEntity user) {
        this.user = user;
    }

    /**
     * Returns the database ID of the authenticated user.
     * Used by controllers that need the user's ID for ownership checks or
     * associations (e.g. file creation, permission granting) without querying
     * the database again after authentication.
     */
    public Long getId() {
        return user.getId();
    }

    /**
     * Returns the single granted authority derived from the user's role string
     * (e.g. "USER"). Spring Security uses this to evaluate any role-based
     * access rules defined in SecurityConfig.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(user.getRole()));
    }

    /**
     * Returns the BCrypt-hashed password used by Spring Security to verify
     * the credentials supplied during login.
     */
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /** Returns the username used to identify this account during authentication. */
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * The following four methods return true unconditionally as ScriptDojo does
     * not currently implement account expiry, locking, or credential expiry.
     * All registered accounts are considered permanently active and enabled.
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /** Returns the email address of the authenticated user. */
    public String getEmail() {
        return user.getEmail();
    }

    /**
     * Returns the underlying {@link UserEntity}.
     * Available for call sites that require the full entity rather than the
     * individual fields exposed by the other accessors.
     */
    public UserEntity getUser() {
        return user;
    }
}
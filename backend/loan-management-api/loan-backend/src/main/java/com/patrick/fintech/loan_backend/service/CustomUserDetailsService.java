package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(
            UserRepository userRepository) {

        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(
            String email)
            throws UsernameNotFoundException {

        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException(
                    "Email is required");
        }

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        User user =
                userRepository
                        .findByEmail(normalizedEmail)
                        .orElseThrow(
                                () -> new UsernameNotFoundException(
                                        "User not found: "
                                                + normalizedEmail));

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new DisabledException(
                    "User account is not active");
        }

        String roleName =
                normalizeRole(
                        user.getRole() != null
                                ? user.getRole().getName()
                                : null);

        if (roleName == null) {
            throw new DisabledException(
                    "User has no valid security role assigned");
        }

        /*
         * Spring Security's hasRole('ADMIN') and hasAnyRole(...)
         * expect ROLE_ADMIN-style authorities by default.
         *
         * Always expose the normalized role using the ROLE_ prefix.
         */
        String authority =
                "ROLE_" + roleName;

        return new org.springframework.security.core.userdetails.User(
                normalizedEmail,
                user.getPassword(),

                // enabled
                true,

                // accountNonExpired
                true,

                // credentialsNonExpired
                true,

                // accountNonLocked
                !user.isLocked(),

                List.of(
                        new SimpleGrantedAuthority(authority)
                )
        );
    }

    /**
     * Converts all supported database/API role representations into
     * the canonical role name expected by Spring Security.
     *
     * Examples:
     *
     * ADMIN                  -> ADMIN
     * admin                  -> ADMIN
     * ROLE_ADMIN             -> ADMIN
     * role_admin             -> ADMIN
     * ROLE-ADMIN             -> ADMIN
     * " role admin "         -> ADMIN
     */
    private String normalizeRole(String raw) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized =
                raw.trim()
                        .toUpperCase(Locale.ROOT)
                        .replace('-', '_')
                        .replace(' ', '_');

        /*
         * Database records should normally contain ADMIN rather than
         * ROLE_ADMIN, but accept both formats safely.
         */
        while (normalized.startsWith("ROLE_")) {
            normalized =
                    normalized.substring(5);
        }

        /*
         * Security role names are intentionally restricted to
         * uppercase letters, numbers and underscores.
         *
         * This prevents malformed database values from becoming
         * unexpected Spring Security authorities.
         */
        if (
                normalized.isBlank()
                    || !normalized.matches(
                            "[A-Z][A-Z0-9_]*")) {

            return null;
        }

        return normalized;
    }
}
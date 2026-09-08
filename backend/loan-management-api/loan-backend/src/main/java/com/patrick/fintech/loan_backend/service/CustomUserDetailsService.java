package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.RoleName;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("User email is required");
        }

        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found: " + email
                        )
                );

        String roleName = normalizeRole(
                user.getRole() != null
                        ? user.getRole().getName()
                        : null
        );

        if (roleName == null) {
            throw new UsernameNotFoundException(
                    "User does not have a valid security role"
            );
        }

        /*
         * The database role is the authoritative role.
         */
        Set<String> authorities = new LinkedHashSet<>();

        authorities.add("ROLE_" + roleName);

        /*
         * ADMIN is the highest application role.
         *
         * Give ADMIN all application authorities so that an endpoint
         * protected with:
         *
         * hasAnyRole('ACCOUNTANT', ...)
         *
         * or:
         *
         * hasAnyRole('LOAN_OFFICER', ...)
         *
         * does not incorrectly reject a genuine administrator.
         */
        if ("ADMIN".equals(roleName)) {

            for (RoleName role : RoleName.values()) {
                authorities.add("ROLE_" + role.name());
            }

            /*
             * Backward compatibility with endpoints/databases using
             * INSTITUTION_ADMIN.
             */
            authorities.add("ROLE_INSTITUTION_ADMIN");
        }

        List<SimpleGrantedAuthority> grantedAuthorities =
                new ArrayList<>();

        for (String authority : authorities) {
            grantedAuthorities.add(
                    new SimpleGrantedAuthority(authority)
            );
        }

        /*
         * IMPORTANT:
         *
         * The account status is still enforced here.
         * ADMIN does not bypass a disabled/inactive account.
         */
        boolean enabled =
                user.getStatus() == User.UserStatus.ACTIVE;

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                enabled,
                true,
                true,
                true,
                grantedAuthorities
        );
    }

    private String normalizeRole(String rawRole) {

        if (rawRole == null || rawRole.isBlank()) {
            return null;
        }

        String normalized =
                rawRole.trim()
                        .toUpperCase(Locale.ROOT)
                        .replace('-', '_')
                        .replace(' ', '_');

        /*
         * Match all roles defined by the current RoleName enum.
         */
        for (RoleName role : RoleName.values()) {

            if (role.name().equals(normalized)) {
                return role.name();
            }
        }

        /*
         * Compatibility with installations where this role exists
         * in the database but is not currently represented in RoleName.
         */
        if ("INSTITUTION_ADMIN".equals(normalized)) {
            return "INSTITUTION_ADMIN";
        }

        return null;
    }
}
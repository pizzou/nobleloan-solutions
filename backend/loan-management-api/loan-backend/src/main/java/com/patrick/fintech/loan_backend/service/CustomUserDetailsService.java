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

/**
 * Loads the authoritative application role from the database and converts it
 * into Spring Security authorities.
 *
 * ADMIN and BUSINESS_OWNER are high-authority roles, but they are deliberately
 * kept distinct for confidential reporting scope. ADMIN receives the normal
 * staff authorities needed to operate the application, while BUSINESS_OWNER
 * is the only role that receives ROLE_BUSINESS_OWNER. This distinction is
 * security-critical because ReportingScopeService uses ROLE_BUSINESS_OWNER
 * as the authoritative signal for access to BUSINESS_OWNER_ONLY loans.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + email));

        String roleName = normalizeRole(
                user.getRole() != null ? user.getRole().getName() : null);

        if (roleName == null) {
            throw new UsernameNotFoundException(
                    "User has no valid security role assigned");
        }

        Set<String> authorities = new LinkedHashSet<>();
        authorities.add("ROLE_" + roleName);

        // ADMIN is the top-level application administrator.  Grant every
        // defined staff authority so existing endpoint-specific method
        // security cannot accidentally deny an administrator.
        if ("ADMIN".equals(roleName)
                || "INSTITUTION_ADMIN".equals(roleName)
                || "BUSINESS_OWNER".equals(roleName)) {
            for (RoleName role : RoleName.values()) {
                // BUSINESS_OWNER is a confidential reporting authority, not a
                // generic staff authority. ADMIN must not inherit it, otherwise
                // ReportingScopeService would correctly interpret the ADMIN
                // authentication as BUSINESS_OWNER and expose confidential
                // loans in accounting, dashboard and regulatory reports.
                if ("BUSINESS_OWNER".equals(role.name())
                        && !"BUSINESS_OWNER".equals(roleName)) {
                    continue;
                }
                authorities.add("ROLE_" + role.name());
            }

            // Kept for compatibility with the existing UserController rule:
            // hasAnyRole('ADMIN','INSTITUTION_ADMIN').
            authorities.add("ROLE_INSTITUTION_ADMIN");
        }

        List<SimpleGrantedAuthority> grantedAuthorities = new ArrayList<>();
        for (String authority : authorities) {
            grantedAuthorities.add(new SimpleGrantedAuthority(authority));
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.getStatus() == User.UserStatus.ACTIVE,
                true,
                true,
                !user.isLocked(),
                grantedAuthorities
        );
    }

    private String normalizeRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        for (RoleName role : RoleName.values()) {
            if (role.name().equals(normalized)) {
                return role.name();
            }
        }

        
        if ("INSTITUTION_ADMIN".equals(normalized)) {
            return normalized;
        }

        return null;
    }
}

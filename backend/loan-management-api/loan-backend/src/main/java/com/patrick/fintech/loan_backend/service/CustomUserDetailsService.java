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

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + email));

        return fromUser(user);
    }

    /**
     * Converts an already-loaded application User into Spring Security
     * UserDetails without performing another database query.
     *
     * This method is intentionally public because JwtAuthFilter already
     * loads the authoritative User entity in order to validate:
     *
     * - account status
     * - token version
     * - tenant/security state
     *
     * Reusing that entity avoids a second database lookup on every request.
     */
    public UserDetails fromUser(User user) {

        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }

        String roleName = normalizeRole(
                user.getRole() != null
                        ? user.getRole().getName()
                        : null
        );

        if (roleName == null) {
            throw new UsernameNotFoundException(
                    "User has no valid security role assigned"
            );
        }

        Set<String> authorities = new LinkedHashSet<>();

        authorities.add("ROLE_" + roleName);

        /*
         * ADMIN, INSTITUTION_ADMIN and BUSINESS_OWNER retain the
         * existing high-authority behavior.
         *
         * BUSINESS_OWNER remains deliberately separate because
         * reporting/security scope depends on ROLE_BUSINESS_OWNER.
         */
        if ("ADMIN".equals(roleName)
                || "INSTITUTION_ADMIN".equals(roleName)
                || "BUSINESS_OWNER".equals(roleName)) {

            for (RoleName role : RoleName.values()) {

                /*
                 * ADMIN must NOT inherit BUSINESS_OWNER.
                 *
                 * ReportingScopeService uses ROLE_BUSINESS_OWNER
                 * as the authoritative confidential-reporting signal.
                 */
                if ("BUSINESS_OWNER".equals(role.name())
                        && !"BUSINESS_OWNER".equals(roleName)) {
                    continue;
                }

                authorities.add("ROLE_" + role.name());
            }

            /*
             * Existing controller compatibility.
             */
            authorities.add("ROLE_INSTITUTION_ADMIN");
        }

        List<SimpleGrantedAuthority> grantedAuthorities =
                new ArrayList<>(authorities.size());

        for (String authority : authorities) {
            grantedAuthorities.add(
                    new SimpleGrantedAuthority(authority)
            );
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

        /*
         * Preserve compatibility with installations where
         * INSTITUTION_ADMIN exists as a persisted role even if
         * it is not currently represented in RoleName.
         */
        if ("INSTITUTION_ADMIN".equals(normalized)) {
            return normalized;
        }

        return null;
    }
}
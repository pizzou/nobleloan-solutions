
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

        /*
         * Always grant the user's actual role.
         */
        authorities.add("ROLE_" + roleName);

        
        if ("BUSINESS_OWNER".equals(roleName)) {

           
            for (RoleName role : RoleName.values()) {
                authorities.add("ROLE_" + role.name());
            }

            
            authorities.add("ROLE_INSTITUTION_ADMIN");

        } else if ("ADMIN".equals(roleName)
                || "INSTITUTION_ADMIN".equals(roleName)) {

           
            for (RoleName role : RoleName.values()) {

                if ("BUSINESS_OWNER".equals(role.name())) {
                    continue;
                }

                authorities.add("ROLE_" + role.name());
            }

            
            authorities.add("ROLE_INSTITUTION_ADMIN");
        }

        List<SimpleGrantedAuthority> grantedAuthorities = new ArrayList<>();

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

        
        if ("INSTITUTION_ADMIN".equals(normalized)) {
            return normalized;
        }

        return null;
    }
}


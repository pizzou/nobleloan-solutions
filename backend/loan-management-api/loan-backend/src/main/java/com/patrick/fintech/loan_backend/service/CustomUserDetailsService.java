package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.RoleName;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.DisabledException;
import java.util.List;
import java.util.Locale;


@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + email));

        String roleName = normalizeRole(
                user.getRole() != null ? user.getRole().getName() : null);

        if (roleName == null) {
            throw new DisabledException(
                    "User has no valid security role assigned");
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.getStatus() == User.UserStatus.ACTIVE,
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + roleName))
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

        return null;
    }
}

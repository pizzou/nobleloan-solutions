package com.patrick.fintech.loan_backend.dto;

import com.patrick.fintech.loan_backend.model.User;
import lombok.Data;
import java.time.LocalDateTime;

/** Safe user response. Passwords, MFA secrets, OTP hashes and security internals are never exposed. */
@Data
public class UserResponse {
    private Long id;
    private Long organizationId;
    private Long branchId;
    private Long roleId;
    private String roleName;
    private String name;
    private String email;
    private String phone;
    private String avatarUrl;
    private String jobTitle;
    private User.UserStatus status;
    private boolean twoFactorEnabled;
    private boolean mustChangePassword;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

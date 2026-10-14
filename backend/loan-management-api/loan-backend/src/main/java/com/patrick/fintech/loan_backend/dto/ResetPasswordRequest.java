package com.patrick.fintech.loan_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank
    @Size(max = 128, message = "Reset token is invalid")
    private String token;

    @NotBlank
    @Size(min = 10, max = 128, message = "Password must be between 10 and 128 characters")
    private String newPassword;
}

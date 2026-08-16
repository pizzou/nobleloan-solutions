package com.patrick.fintech.loan_backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** Safe loan comment representation. */
@Data
public class LoanCommentResponse {
    private Long id;
    private Long loanId;
    private Long authorId;
    private String authorName;
    private String message;
    private boolean visibleToApplicant;
    private LocalDateTime createdAt;
}

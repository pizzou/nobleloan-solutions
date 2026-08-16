package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ApiBoundaryArchitectureTest {
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?:ApiResponse|ResponseEntity|Page|List)<[^\\n]*(?:com\\.patrick\\.fintech\\.loan_backend\\.model\\.|\\b(?:Loan|Borrower|Payment|User|Organization|Role|Branch|BorrowerFile|CollectionCase|CollectionAction|ChartOfAccount|JournalEntry|AuditLog|BankAccount|KycCheck|Expense|LoanApproval|LoanProduct|Notification|ContactMessage|CurrencyRate|ESignatureRequest|Guarantor|InternalDocument|ImportBatch|WebhookEndpoint|Collateral)\\b)",
            Pattern.MULTILINE);

    @Test
    void controllersMustNotExposeJpaEntitiesInResponseGenerics() throws Exception {
        Path root = Paths.get("src/main/java/com/patrick/fintech/loan_backend/controller");
        assertTrue(Files.isDirectory(root), "Controller source directory not found");
        List<String> violations = new ArrayList<>();
        try (var files = Files.list(root)) {
            files.filter(p -> p.toString().endsWith("Controller.java")).forEach(p -> {
                try {
                    String source = Files.readString(p);
                    var m = FORBIDDEN.matcher(source);
                    if (m.find())
                        violations.add(p.getFileName() + ": " + m.group());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertTrue(violations.isEmpty(), "JPA entity response boundary violations:\n" + String.join("\n", violations));
    }
}

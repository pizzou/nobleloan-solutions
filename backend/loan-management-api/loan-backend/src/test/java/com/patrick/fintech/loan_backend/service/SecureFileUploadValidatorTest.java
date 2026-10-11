package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class SecureFileUploadValidatorTest {
    private final SecureFileUploadValidator validator = new SecureFileUploadValidator();

    @Test
    void acceptsSupportedPdfWhenContentMatchesDeclaredMimeType() {
        byte[] pdfHeader = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile(
                "file", "identity.pdf", "application/pdf", pdfHeader);

        assertDoesNotThrow(() -> validator.validateDocument(file, 8L * 1024 * 1024));
    }

    @Test
    void rejectsMimeTypeSpoofing() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "identity.pdf", "application/pdf",
                "this is not a PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> validator.validateDocument(file, 8L * 1024 * 1024));
    }

    @Test
    void rejectsUnsupportedTypesAndOversizedFiles() {
        MockMultipartFile html = new MockMultipartFile(
                "file", "payload.html", "text/html", "<script>alert(1)</script>".getBytes());
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateDocument(html, 8L * 1024 * 1024));

        MockMultipartFile oversized = new MockMultipartFile(
                "file", "identity.pdf", "application/pdf",
                "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateDocument(oversized, 4));
    }

    @Test
    void rejectsPathTraversalFilenames() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../identity.pdf", "application/pdf",
                "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        assertThrows(IllegalArgumentException.class,
                () -> validator.validateDocument(file, 8L * 1024 * 1024));
    }
}

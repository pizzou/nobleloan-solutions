package com.patrick.fintech.loan_backend.security;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/** Centralized upload validation. Never trust filename or client MIME type alone. */
public final class SecureFileValidator {
    private SecureFileValidator() {}

    public static final long DEFAULT_MAX_BYTES = 25L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("csv", "xlsx");

    public static void validate(MultipartFile file, long maxBytes) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Uploaded file is required");
        if (file.getSize() > maxBytes) throw new IllegalArgumentException("Uploaded file exceeds the maximum allowed size");
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Uploaded filename is required");
        String normalized = name.replace('\\', '/');
        if (normalized.contains("../") || normalized.contains("/")) throw new IllegalArgumentException("Invalid filename");
        String ext = extension(normalized);
        if (!ALLOWED_EXTENSIONS.contains(ext)) throw new IllegalArgumentException("Only CSV and XLSX files are accepted");

        String contentType = file.getContentType();
        if ("csv".equals(ext)) {
            if (contentType != null && !contentType.isBlank() &&
                !contentType.equalsIgnoreCase(MediaType.TEXT_PLAIN_VALUE) &&
                !contentType.equalsIgnoreCase("text/csv") &&
                !contentType.equalsIgnoreCase("application/csv") &&
                !contentType.equalsIgnoreCase("application/vnd.ms-excel")) {
                throw new IllegalArgumentException("Invalid CSV content type");
            }
        } else {
            try (InputStream in = file.getInputStream()) {
                byte[] h = in.readNBytes(4);
                if (h.length < 4 || h[0] != 0x50 || h[1] != 0x4B) {
                    throw new IllegalArgumentException("Invalid XLSX file signature");
                }
            }
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

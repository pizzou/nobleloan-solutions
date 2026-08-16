package com.patrick.fintech.loan_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Security boundary for legacy CSV/XLSX uploads.
 *
 * The original filename and MIME type are untrusted input.
 */
@Service
@Slf4j
public class ImportFileSecurityService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("csv", "xlsx");

    private static final int CSV_SAMPLE_BYTES = 4096;

    private static final long DEFAULT_MAX_BYTES = 50L * 1024L * 1024L;

    @Value("${app.import.max-file-bytes:52428800}")
    private long maxFileBytes;

    public void validate(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "No import file was uploaded");
        }

        if (file.getSize() <= 0) {
            throw new IllegalArgumentException(
                    "Import file is empty");
        }

        if (file.getSize() > maxFileBytes) {
            throw new IllegalArgumentException(
                    "Import file exceeds the maximum allowed size of "
                            + maxFileBytes
                            + " bytes");
        }

        String filename = safeFilename(file.getOriginalFilename());

        String extension = extensionOf(filename);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Only CSV and XLSX files are supported");
        }

        try {

            if ("xlsx".equals(extension)) {
                validateXlsxSignature(file);
            } else {
                validateCsvSample(file);
            }

        } catch (IOException ex) {

            log.warn(
                    "Unable to inspect import file: filename={}",
                    filename,
                    ex);

            throw new IllegalArgumentException(
                    "The uploaded file could not be inspected",
                    ex);
        }
    }

    public String safeFilename(String originalFilename) {

        String filename = originalFilename == null
                ? "import.xlsx"
                : originalFilename;

        filename = filename.replace('\\', '/');

        int slash = filename.lastIndexOf('/');

        if (slash >= 0) {
            filename = filename.substring(slash + 1);
        }

        filename = filename.trim();

        if (filename.isBlank()
                || ".".equals(filename)
                || "..".equals(filename)) {

            throw new IllegalArgumentException(
                    "Invalid import filename");
        }

        if (filename.length() > 255) {
            throw new IllegalArgumentException(
                    "Import filename is too long");
        }

        if (filename.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException(
                    "Import filename contains an invalid character");
        }

        return filename;
    }

    private void validateXlsxSignature(
            MultipartFile file) throws IOException {

        byte[] signature = new byte[8];

        try (InputStream input = file.getInputStream()) {

            int read = input.read(signature);

            if (read < 4
                    || signature[0] != 'P'
                    || signature[1] != 'K'
                    || signature[2] != 3
                    || signature[3] != 4) {

                throw new IllegalArgumentException(
                        "The XLSX file signature is invalid");
            }
        }
    }

    private void validateCsvSample(
            MultipartFile file) throws IOException {

        byte[] buffer = new byte[CSV_SAMPLE_BYTES];

        int read;

        try (InputStream input = file.getInputStream()) {

            read = input.read(buffer);
        }

        if (read <= 0) {
            throw new IllegalArgumentException(
                    "CSV file is empty");
        }

        for (int i = 0; i < read; i++) {

            if (buffer[i] == 0) {

                throw new IllegalArgumentException(
                        "The uploaded CSV contains binary data");
            }
        }

        /*
         * Decode a small sample only.
         *
         * We deliberately do not reject the upload solely because of
         * character replacement because CSV files may legitimately use
         * different encodings.
         */
        String sample = new String(
                buffer,
                0,
                read,
                StandardCharsets.UTF_8);

        if (sample.indexOf('\uFFFD') >= 0) {

            log.debug(
                    "CSV contains replacement characters; continuing import");
        }
    }

    private String extensionOf(String filename) {

        int dot = filename.lastIndexOf('.');

        if (dot < 0
                || dot == filename.length() - 1) {

            return "";
        }

        return filename
                .substring(dot + 1)
                .toLowerCase(Locale.ROOT);
    }
}
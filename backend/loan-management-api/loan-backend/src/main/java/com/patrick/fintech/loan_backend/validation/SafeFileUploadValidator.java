package com.patrick.fintech.loan_backend.validation;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class SafeFileUploadValidator {

    private static final Set<String> ALLOWED = Set.of(
            "pdf",
            "csv",
            "xlsx",
            "xls",
            "jpg",
            "jpeg",
            "png",
            "webp");

    private SafeFileUploadValidator() {
        // Utility class.
    }

    public static String validate(
            MultipartFile file,
            long maxBytes) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "A file is required.");
        }

        if (maxBytes <= 0) {
            throw new IllegalArgumentException(
                    "Maximum file size must be greater than zero.");
        }

        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "File exceeds the maximum allowed size.");
        }

        String original = file.getOriginalFilename();

        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException(
                    "Filename is required.");
        }

        String name = original.replace('\\', '/');

        /*
         * Reject path traversal and absolute paths.
         */
        if (name.contains("../")
                || name.contains("..")
                || name.startsWith("/")
                || name.startsWith("\\")) {

            throw new IllegalArgumentException(
                    "Invalid filename.");
        }

        /*
         * Only keep the final filename component.
         */
        String safe = name.substring(
                name.lastIndexOf('/') + 1);

        if (safe.isBlank()
                || ".".equals(safe)
                || "..".equals(safe)) {

            throw new IllegalArgumentException(
                    "Invalid filename.");
        }

        String ext = extension(safe);

        if (!ALLOWED.contains(ext)) {
            throw new IllegalArgumentException(
                    "File type is not allowed.");
        }

        return safe;
    }

    public static void validateMagicBytes(
            MultipartFile file,
            String extension) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "A file is required.");
        }

        if (extension == null || extension.isBlank()) {
            throw new IllegalArgumentException(
                    "File extension is required.");
        }

        String ext = extension
                .toLowerCase(Locale.ROOT)
                .trim();

        if (!ALLOWED.contains(ext)) {
            throw new IllegalArgumentException(
                    "File type is not allowed.");
        }

        try (InputStream in = file.getInputStream()) {

            byte[] b = in.readNBytes(16);

            if (b.length == 0) {
                throw new IllegalArgumentException(
                        "File contains no readable content.");
            }

            switch (ext) {

                case "jpg":
                case "jpeg":

                    if (!starts(
                            b,
                            (byte) 0xFF,
                            (byte) 0xD8,
                            (byte) 0xFF)) {
                        throw new IllegalArgumentException(
                                "Invalid JPEG content.");
                    }

                    break;

                case "png":

                    if (!starts(
                            b,
                            (byte) 0x89,
                            (byte) 0x50,
                            (byte) 0x4E,
                            (byte) 0x47,
                            (byte) 0x0D,
                            (byte) 0x0A,
                            (byte) 0x1A,
                            (byte) 0x0A)) {
                        throw new IllegalArgumentException(
                                "Invalid PNG content.");
                    }

                    break;

                case "pdf":

                    if (!starts(
                            b,
                            (byte) '%',
                            (byte) 'P',
                            (byte) 'D',
                            (byte) 'F',
                            (byte) '-')) {
                        throw new IllegalArgumentException(
                                "Invalid PDF content.");
                    }

                    break;

                case "webp":

                    if (b.length < 12
                            || !starts(
                                    b,
                                    (byte) 'R',
                                    (byte) 'I',
                                    (byte) 'F',
                                    (byte) 'F')
                            || !startsAt(
                                    b,
                                    8,
                                    (byte) 'W',
                                    (byte) 'E',
                                    (byte) 'B',
                                    (byte) 'P')) {

                        throw new IllegalArgumentException(
                                "Invalid WEBP content.");
                    }

                    break;

                case "xlsx":

                    if (!starts(
                            b,
                            (byte) 0x50,
                            (byte) 0x4B,
                            (byte) 0x03,
                            (byte) 0x04)
                            && !starts(
                                    b,
                                    (byte) 0x50,
                                    (byte) 0x4B,
                                    (byte) 0x05,
                                    (byte) 0x06)
                            && !starts(
                                    b,
                                    (byte) 0x50,
                                    (byte) 0x4B,
                                    (byte) 0x07,
                                    (byte) 0x08)) {

                        throw new IllegalArgumentException(
                                "Invalid XLSX content.");
                    }

                    break;

                case "xls":

                    if (!starts(
                            b,
                            (byte) 0xD0,
                            (byte) 0xCF,
                            (byte) 0x11,
                            (byte) 0xE0,
                            (byte) 0xA1,
                            (byte) 0xB1,
                            (byte) 0x1A,
                            (byte) 0xE1)) {

                        throw new IllegalArgumentException(
                                "Invalid XLS content.");
                    }

                    break;

                case "csv":

                    break;

                default:

                    throw new IllegalArgumentException(
                            "Unsupported file type.");
            }
        }
    }

    /**
     * Checks whether the byte array starts with the expected sequence.
     *
     * @param actual   actual file bytes
     * @param expected expected signature
     * @return true if the signature matches
     */
    private static boolean starts(
            byte[] actual,
            byte... expected) {

        if (actual == null || expected == null) {
            return false;
        }

        if (actual.length < expected.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {

            if (actual[i] != expected[i]) {
                return false;
            }
        }

        return true;
    }

    private static boolean startsAt(
            byte[] actual,
            int offset,
            byte... expected) {

        if (actual == null || expected == null) {
            return false;
        }

        if (offset < 0) {
            return false;
        }

        if (actual.length < offset + expected.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {

            if (actual[offset + i] != expected[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Extracts a lowercase file extension.
     */
    private static String extension(
            String name) {

        if (name == null || name.isBlank()) {
            return "";
        }

        int dot = name.lastIndexOf('.');

        if (dot < 1
                || dot == name.length() - 1) {

            return "";
        }

        return name.substring(dot + 1)
                .toLowerCase(Locale.ROOT);
    }
}
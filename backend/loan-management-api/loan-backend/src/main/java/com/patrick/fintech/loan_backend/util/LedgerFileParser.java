package com.patrick.fintech.loan_backend.util;

import org.apache.poi.ss.usermodel.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Production parser for CSV/XLS/XLSX legacy loan ledgers.
 *
 * The business import rules remain in LegacyLoanImportRowService. This class
 * only converts common spreadsheet layouts into the canonical import fields.
 */
public final class LedgerFileParser {

    private static final int MAX_SHEETS_TO_SCAN = 50;
    private static final int MAX_HEADER_ROWS_TO_SCAN = 15;

    private LedgerFileParser() {
    }

    public static List<Map<String, String>> parse(String filename, InputStream in) throws IOException {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return parseCsv(in);
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseExcel(in);
        }
        throw new IllegalArgumentException("Unsupported file type — please upload a .csv or .xlsx file.");
    }

    /** Package-safe CSV splitter used by the streaming importer. */
    public static List<String> splitForStreaming(String line) {
        return splitCsvLine(line);
    }

    public static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        String value = cleanCell(header);
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s\\-]+", "_");
    }

    // ---------- CSV ----------

    private static List<Map<String, String>> parseCsv(InputStream in) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = readLogicalLine(reader);
            if (headerLine == null) {
                return rows;
            }
            List<String> headers = splitCsvLine(headerLine).stream()
                    .map(LedgerFileParser::normalizeHeader)
                    .toList();

            String line;
            while ((line = readLogicalLine(reader)) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = splitCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < cells.size() ? cleanCell(cells.get(i)) : "");
                }
                if (!isMeaningfullyBlank(row)) {
                    rows.add(row);
                }
            }
        }
        return canonicalizeRows(rows);
    }

    private static String readLogicalLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        StringBuilder buf = new StringBuilder(line);
        while (!isQuoteBalanced(buf)) {
            String next = reader.readLine();
            if (next == null) {
                break;
            }
            buf.append('\n').append(next);
        }
        return buf.toString();
    }

    private static boolean isQuoteBalanced(CharSequence s) {
        boolean escaped = false;
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                    i++;
                    continue;
                }
                inQuotes = !inQuotes;
            }
        }
        return !inQuotes;
    }

    private static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        out.add(cur.toString());
        return out;
    }

    // ---------- XLSX/XLS ----------

    private static List<Map<String, String>> parseExcel(InputStream in) throws IOException {
        try (Workbook wb = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT, true);
            FormulaEvaluator evaluator = null;

            SheetCandidate best = null;
            int sheetsScanned = Math.min(wb.getNumberOfSheets(), MAX_SHEETS_TO_SCAN);

            for (int s = 0; s < sheetsScanned; s++) {
                Sheet sheet = wb.getSheetAt(s);
                SheetCandidate candidate = inspectSheet(sheet, formatter, evaluator);
                if (candidate != null) {
                    // An identified NLS positional ledger is more authoritative than
                    // a generic "portfolio" worksheet because it contains borrower identity
                    // fields (national ID, phone, gender) plus the loan columns required
                    // by the legacy import flow.
                    if (candidate.nlsLayout && (best == null || !best.nlsLayout || candidate.score > best.score)) {
                        best = candidate;
                    } else if (!candidate.nlsLayout
                            && (best == null || (!best.nlsLayout && candidate.score > best.score))) {
                        best = candidate;
                    }
                }
            }

            if (best == null) {
                throw new IOException(
                        "No supported ledger table was found in the workbook. " +
                                "Expected a standard import header row or the supported NLS loan portfolio layout.");
            }

            return readCandidate(best, formatter, evaluator);
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException(
                    "The uploaded Excel ledger could not be read. " +
                            "The workbook may contain unsupported Excel features or may be corrupted. " +
                            "Formula evaluation is intentionally disabled during import discovery.",
                    e);
        }
    }

    private static SheetCandidate inspectSheet(
            Sheet sheet,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        // Prefer an actual canonical/portfolio header row.
        int maxRow = Math.min(sheet.getLastRowNum(), MAX_HEADER_ROWS_TO_SCAN - 1);
        for (int rowIndex = 0; rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || row.getLastCellNum() <= 0) {
                continue;
            }
            List<String> headers = readRow(row, formatter, evaluator);
            int score = headerScore(headers);
            if (score >= 5) {
                return SheetCandidate.header(sheet, rowIndex, headers, score);
            }
        }

        // The supplied NLS portfolio workbook has a headerless data sheet.
        // Detect the characteristic positional layout without changing the row service
        // rules.
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= Math.min(sheet.getLastRowNum(), 50); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (looksLikeNlsPortfolioRow(row, formatter, evaluator)) {
                int populated = Math.max(0, row.getLastCellNum());
                // NLS layout is an explicit supported import layout. Give it a
                // deterministic priority over generic portfolio sheets.
                return SheetCandidate.nls(sheet, rowIndex, populated, 10_000 + populated);
            }
        }

        return null;
    }

    private static int headerScore(List<String> headers) {
        Set<String> normalized = new HashSet<>(headers);
        int score = 0;
        score += containsAny(normalized, "national_id", "id_number", "id", "national_identity") ? 3 : 0;
        score += containsAny(normalized, "first_name", "firstname", "names", "name") ? 2 : 0;
        score += containsAny(normalized, "phone", "telephone", "mobile") ? 1 : 0;
        score += containsAny(normalized, "amount", "amount_disbursed", "principal") ? 2 : 0;
        score += containsAny(normalized, "duration_months", "period_of_the_loan", "loan_period") ? 2 : 0;
        score += containsAny(normalized, "start_date", "disbursement_date", "date_disbursed") ? 2 : 0;
        return score;
    }

    private static boolean containsAny(Set<String> values, String... candidates) {
        for (String candidate : candidates) {
            if (values.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static List<Map<String, String>> readCandidate(
            SheetCandidate candidate,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        if (candidate.nlsLayout) {
            return readNlsRows(candidate.sheet, candidate.startRow, formatter, evaluator);
        }

        List<String> headers = canonicalizeHeaders(candidate.headers);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int rowIndex = candidate.startRow + 1; rowIndex <= candidate.sheet.getLastRowNum(); rowIndex++) {
            Row row = candidate.sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                values.put(headers.get(i), cellValue(cell, formatter, evaluator));
            }
            if (!isMeaningfullyBlank(values)) {
                rows.add(values);
            }
        }
        return canonicalizeRows(rows);
    }

    private static List<Map<String, String>> readNlsRows(
            Sheet sheet,
            int startRow,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        List<Map<String, String>> rows = new ArrayList<>();
        for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || !looksLikeNlsPortfolioRow(row, formatter, evaluator)) {
                continue;
            }
            rows.add(mapNlsPortfolioRow(row, formatter, evaluator));
        }
        return rows;
    }

    private static boolean looksLikeNlsPortfolioRow(
            Row row,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        if (row == null || row.getLastCellNum() < 30) {
            return false;
        }

        String name = cellValue(row.getCell(1), formatter, evaluator);
        String nationalId = cellValue(row.getCell(2), formatter, evaluator);
        String phone = cellValue(row.getCell(3), formatter, evaluator);
        String gender = cellValue(row.getCell(4), formatter, evaluator);
        String loanType = cellValue(row.getCell(9), formatter, evaluator);
        String amount = cellValue(row.getCell(20), formatter, evaluator);
        String duration = cellValue(row.getCell(29), formatter, evaluator);

        return !name.isBlank()
                && nationalId.replaceAll("\\s+", "").length() >= 10
                && !phone.isBlank()
                && ("M".equalsIgnoreCase(gender) || "F".equalsIgnoreCase(gender)
                        || "MALE".equalsIgnoreCase(gender) || "FEMALE".equalsIgnoreCase(gender))
                && !loanType.isBlank()
                && isDecimalLike(amount)
                && isIntegerLike(duration);
    }

    private static Map<String, String> mapNlsPortfolioRow(
            Row row,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        String fullName = cleanCell(cellValue(row.getCell(1), formatter, evaluator));
        String[] names = splitName(fullName);

        Map<String, String> out = new LinkedHashMap<>();
        out.put("national_id", cleanNationalId(cellValue(row.getCell(2), formatter, evaluator)));
        out.put("first_name", names[0]);
        out.put("last_name", names[1]);
        out.put("phone", cleanCell(cellValue(row.getCell(3), formatter, evaluator)));
        out.put("gender", cleanCell(cellValue(row.getCell(4), formatter, evaluator)));
        out.put("marital_status", cleanCell(cellValue(row.getCell(7), formatter, evaluator)));
        out.put("loan_type", cleanLoanType(cellValue(row.getCell(9), formatter, evaluator)));
        out.put("amount", cleanCell(cellValue(row.getCell(20), formatter, evaluator)));
        out.put("interest_rate", "5.00");
        out.put("interest_rate_type", "MONTHLY");
        out.put("duration_months", cleanCell(cellValue(row.getCell(29), formatter, evaluator)));
        out.put("start_date", normalizeDateString(cellValue(row.getCell(21), formatter, evaluator)));
        out.put("status", "ACTIVE");
        out.put("currency", "RWF");
        out.put("loan_reference", cleanCell(cellValue(row.getCell(0), formatter, evaluator)));
        out.put("notes", "Imported from NLS loan portfolio workbook");
        return out;
    }

    private static List<String> readRow(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            result.add(normalizeHeader(cellValue(row.getCell(i), formatter, evaluator)));
        }
        return result;
    }

    private static List<String> canonicalizeHeaders(List<String> headers) {
        List<String> result = new ArrayList<>(headers.size());
        for (String header : headers) {
            result.add(canonicalHeader(header));
        }
        return result;
    }

    private static String canonicalHeader(String header) {
        return switch (normalizeHeader(header)) {
            case "id_number", "national_identity", "nationalid" -> "national_id";
            case "names", "name", "borrower_name" -> "names";
            case "telephone", "mobile", "mobile_number" -> "phone";
            case "amount_disbursed", "principal", "loan_amount" -> "amount";
            case "period_of_the_loan", "loan_period", "period_months" -> "duration_months";
            case "disbursement_date", "date_disbursed" -> "start_date";
            case "rate", "monthly_interest_rate" -> "interest_rate";
            default -> normalizeHeader(header);
        };
    }

    private static List<Map<String, String>> canonicalizeRows(List<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }

        List<Map<String, String>> result = new ArrayList<>(rows.size());
        for (Map<String, String> source : rows) {
            Map<String, String> row = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : source.entrySet()) {
                row.put(canonicalHeader(entry.getKey()), cleanCell(entry.getValue()));
            }
            normalizeNamesAlias(row);
            normalizeHistoricalPortfolioAliases(row);
            if (!isMeaningfullyBlank(row)) {
                result.add(row);
            }
        }
        return result;
    }

    private static void normalizeNamesAlias(Map<String, String> row) {
        String names = row.get("names");
        if ((row.get("first_name") == null || row.get("first_name").isBlank()) && names != null && !names.isBlank()) {
            String[] split = splitName(names);
            row.put("first_name", split[0]);
            row.put("last_name", split[1]);
        }
    }

    private static void normalizeHistoricalPortfolioAliases(Map<String, String> row) {
        // Support the titled PORTFOLIO 2025 worksheet without changing row-service
        // logic.
        if (row.containsKey("amount") && !row.containsKey("interest_rate")) {
            row.put("interest_rate", "5.00");
        }
        if (row.containsKey("amount") && !row.containsKey("interest_rate_type")) {
            row.put("interest_rate_type", "MONTHLY");
        }
        if (row.containsKey("amount") && !row.containsKey("loan_type")) {
            row.put("loan_type", "PERSONAL");
        }
        if (row.containsKey("amount") && !row.containsKey("status")) {
            row.put("status", "ACTIVE");
        }
        if (row.containsKey("amount") && !row.containsKey("gender")) {
            row.put("gender", "UNKNOWN");
        }
        if (row.containsKey("amount") && !row.containsKey("marital_status")) {
            row.put("marital_status", "UNKNOWN");
        }
        if (row.containsKey("amount") && !row.containsKey("currency")) {
            row.put("currency", "RWF");
        }
    }

    private static String cleanCell(String value) {
        if (value == null) {
            return "";
        }
        String result = value
                .replace("\uFEFF", "")
                .replace("\u00A0", " ")
                .trim();

        while (result.length() >= 2
                && ((result.charAt(0) == '\'' && result.charAt(result.length() - 1) == '\'')
                        || (result.charAt(0) == '"' && result.charAt(result.length() - 1) == '"')
                        || (result.charAt(0) == '“' && result.charAt(result.length() - 1) == '”'))) {
            result = result.substring(1, result.length() - 1).trim();
        }

        if (result.startsWith("'") || result.startsWith("’") || result.startsWith("‘") || result.startsWith("`")) {
            result = result.substring(1).trim();
        }
        return result;
    }

    private static String cleanNationalId(String value) {
        return cleanCell(value).replaceAll("\\s+", "");
    }

    private static String cleanLoanType(String value) {
        return cleanCell(value).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static String[] splitName(String fullName) {
        String value = cleanCell(fullName);
        if (value.isBlank()) {
            return new String[] { "Unknown", "Unknown" };
        }
        String[] tokens = value.split("\\s+");
        if (tokens.length == 1) {
            return new String[] { tokens[0], "Unknown" };
        }
        return new String[] {
                tokens[0],
                String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length))
        };
    }

    private static String normalizeDateString(String value) {
        return cleanCell(value);
    }

    private static String cellValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }

        // Never evaluate Excel formulas during import discovery. Apache POI's
        // formula evaluator cannot parse some valid Excel structured-reference
        // formulas such as Table123[[#This Row],[Column12]], which otherwise
        // aborts the entire upload.
        if (cell.getCellType() == CellType.FORMULA) {
            CellType cachedType = cell.getCachedFormulaResultType();

            switch (cachedType) {
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        LocalDate date = cell.getDateCellValue()
                                .toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate();
                        return date.toString();
                    }
                    return cleanCell(formatter.formatRawCellContents(
                            cell.getNumericCellValue(),
                            cell.getCellStyle().getDataFormat(),
                            cell.getCellStyle().getDataFormatString()));
                }
                case STRING -> {
                    return cleanCell(cell.getStringCellValue());
                }
                case BOOLEAN -> {
                    return Boolean.toString(cell.getBooleanCellValue());
                }
                case ERROR -> {
                    return "";
                }
                default -> {
                    return "";
                }
            }
        }

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDate date = cell.getDateCellValue()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            return date.toString();
        }

        // No evaluator: this formats ordinary cells without attempting to parse
        // unsupported Excel formulas.
        return cleanCell(formatter.formatCellValue(cell));
    }

    private static boolean isMeaningfullyBlank(Map<String, String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String value : row.values()) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDecimalLike(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            new java.math.BigDecimal(cleanCell(value).replace(",", ""));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isIntegerLike(String value) {
        if (!isDecimalLike(value)) {
            return false;
        }
        try {
            return new java.math.BigDecimal(cleanCell(value)).stripTrailingZeros().scale() <= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static final class SheetCandidate {
        private final Sheet sheet;
        private final int startRow;
        private final List<String> headers;
        private final boolean nlsLayout;
        private final int score;

        private SheetCandidate(Sheet sheet, int startRow, List<String> headers, boolean nlsLayout, int score) {
            this.sheet = sheet;
            this.startRow = startRow;
            this.headers = headers;
            this.nlsLayout = nlsLayout;
            this.score = score;
        }

        private static SheetCandidate header(Sheet sheet, int row, List<String> headers, int score) {
            return new SheetCandidate(sheet, row, headers, false, score);
        }

        private static SheetCandidate nls(Sheet sheet, int row, int width, int score) {
            return new SheetCandidate(sheet, row, List.of(), true, score);
        }
    }
}
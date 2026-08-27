package com.patrick.fintech.loan_backend.util;

import org.apache.poi.ss.usermodel.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;

/**
 * Bank-grade legacy ledger parser.
 *
 * Responsibilities are deliberately limited to file interpretation:
 * - CSV / XLS / XLSX support for synchronous preview
 * - standard header-based ledgers
 * - Noble Loan monthly portfolio ledgers
 * - Noble Loan credit/portfolio positional ledgers
 * - Excel formula-safe value extraction
 * - normalization of headers, dates and spreadsheet text
 * - duplicate detection across worksheets
 *
 * Financial business rules remain in LegacyLoanImportRowService.
 */
public final class LedgerFileParser {

    private static final int MAX_SHEETS_TO_SCAN = 100;
    private static final int MAX_HEADER_ROWS_TO_SCAN = 20;
    private static final int MONTHLY_MIN_COLUMNS = 30;
    private static final int CREDIT_MIN_COLUMNS = 35;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M/d/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M/d/uu").withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("d/M/uu").withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART));

    private LedgerFileParser() {
    }

    public static List<Map<String, String>> parse(String filename, InputStream in) throws IOException {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename is required.");
        }
        if (in == null) {
            throw new IllegalArgumentException("Input stream is required.");
        }

        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return parseCsv(in);
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseExcel(in);
        }
        throw new IllegalArgumentException("Unsupported file type — please upload a .csv, .xls or .xlsx file.");
    }

    /** Package-safe CSV splitter used by the streaming importer. */
    public static List<String> splitForStreaming(String line) {
        return splitCsvLine(line);
    }

    /** Shared cell normalization used by preview and commit paths. */
    public static String normalizeCellValue(String value) {
        return cleanCell(value);
    }

    public static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        String value = cleanCell(header);
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\-]+", "_")
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    // ---------------------------------------------------------------------
    // CSV
    // ---------------------------------------------------------------------

    private static List<Map<String, String>> parseCsv(InputStream in) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8), 64 * 1024)) {

            String headerLine = readLogicalLine(reader);
            if (headerLine == null) {
                return rows;
            }

            List<String> headers = splitCsvLine(headerLine).stream()
                    .map(LedgerFileParser::normalizeHeader)
                    .map(LedgerFileParser::canonicalHeader)
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

    public static String readLogicalLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }

        StringBuilder buffer = new StringBuilder(line);
        while (!isQuoteBalanced(buffer)) {
            String next = reader.readLine();
            if (next == null) {
                break;
            }
            buffer.append('\n').append(next);
        }
        return buffer.toString();
    }

    private static boolean isQuoteBalanced(CharSequence value) {
        boolean inQuotes = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '"') {
                continue;
            }
            if (i + 1 < value.length() && value.charAt(i + 1) == '"') {
                i++;
                continue;
            }
            inQuotes = !inQuotes;
        }
        return !inQuotes;
    }

    public static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        out.add(current.toString());
        return out;
    }

    // ---------------------------------------------------------------------
    // Excel
    // ---------------------------------------------------------------------

    private static List<Map<String, String>> parseExcel(InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT, true);
            // Evaluate ordinary Excel formulas so calculated historical balances are
            // imported from the formula result rather than blindly trusting a stale
            // cached value. cellValue() still falls back to the cached result when
            // POI cannot evaluate a workbook-specific/structured-reference formula.
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            List<Map<String, String>> result = new ArrayList<>();
            Set<String> seenKeys = new HashSet<>();

            List<SheetLayout> detectedLayouts = new ArrayList<>();
            int sheets = Math.min(workbook.getNumberOfSheets(), MAX_SHEETS_TO_SCAN);
            boolean hasMonthlyLedger = false;
            boolean hasStandardLedger = false;

            for (int sheetIndex = 0; sheetIndex < sheets; sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                SheetLayout layout = detectLayout(sheet, formatter, evaluator);
                if (layout == null) {
                    continue;
                }
                detectedLayouts.add(new SheetLayout(layout.type, layout.startRow, layout.headers, sheet));
                hasMonthlyLedger |= layout.type == LayoutType.MONTHLY_PORTFOLIO;
                hasStandardLedger |= layout.type == LayoutType.STANDARD;
            }

            for (SheetLayout layout : detectedLayouts) {
                // Credit/portfolio worksheets are useful as a fallback when a
                // workbook contains no actual loan-ledger sheet. When the same
                // workbook already contains the historical loan ledger, treating
                // the credit snapshot as another loan source creates duplicate or
                // future-dated loans.
                if (layout.type == LayoutType.CREDIT_PORTFOLIO
                        && (hasMonthlyLedger || hasStandardLedger)) {
                    continue;
                }

                List<Map<String, String>> sheetRows = switch (layout.type) {
                    case MONTHLY_PORTFOLIO -> readMonthlyRows(layout.sheet, layout.startRow, formatter, evaluator);
                    case CREDIT_PORTFOLIO -> readCreditRows(layout.sheet, layout.startRow, formatter, evaluator);
                    case STANDARD ->
                        readStandardRows(layout.sheet, layout.startRow, layout.headers, formatter, evaluator);
                };

                for (Map<String, String> row : sheetRows) {
                    if (row == null || isMeaningfullyBlank(row)) {
                        continue;
                    }
                    Map<String, String> canonical = canonicalizeRow(row);
                    String key = duplicateKey(canonical);
                    if (key != null && !seenKeys.add(key)) {
                        continue;
                    }
                    result.add(canonical);
                }
            }

            if (result.isEmpty()) {
                throw new IOException(
                        "No supported ledger records were found in the workbook. "
                                + "Expected a standard import table or the Noble Loan portfolio layout.");
            }

            return result;
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException(
                    "The uploaded Excel ledger could not be read. "
                            + "Formula results are evaluated when possible and safely fall back to cached values when POI cannot evaluate a formula.",
                    e);
        }
    }

    private static SheetLayout detectLayout(
            Sheet sheet,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        int maxProbeRow = Math.min(sheet.getLastRowNum(), 60);

        // The monthly Noble Loan ledger must be detected BEFORE generic headers.
        // Its human-readable headers are split across two rows and therefore do not
        // contain a complete canonical header set on a single row.
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= maxProbeRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (looksLikeMonthlyPortfolioRow(row, formatter, evaluator)) {
                return SheetLayout.monthly(rowIndex);
            }
        }

        // The supplied credit/portfolio workbook has borrower demographics and
        // financing data in fixed positional columns.
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= maxProbeRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (looksLikeCreditPortfolioRow(row, formatter, evaluator)) {
                return SheetLayout.credit(rowIndex);
            }
        }

        // Finally support normal header-driven CSV/XLS/XLSX tables.
        int headerLimit = Math.min(sheet.getLastRowNum(), MAX_HEADER_ROWS_TO_SCAN - 1);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= headerLimit; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || row.getLastCellNum() <= 0) {
                continue;
            }
            List<String> headers = readRow(row, formatter, evaluator);
            if (headerScore(headers) >= 5) {
                return SheetLayout.standard(rowIndex, canonicalizeHeaders(headers));
            }
        }

        return null;
    }

    private static boolean looksLikeMonthlyPortfolioRow(
            Row row,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        if (row == null || row.getLastCellNum() < MONTHLY_MIN_COLUMNS) {
            return false;
        }

        String name = cleanCell(cellValue(row.getCell(1), formatter, evaluator));
        String nationalId = cleanNationalId(cellValue(row.getCell(2), formatter, evaluator));
        String phone = cleanCell(cellValue(row.getCell(3), formatter, evaluator));
        String amount = cleanCell(cellValue(row.getCell(4), formatter, evaluator));
        String duration = cleanCell(cellValue(row.getCell(7), formatter, evaluator));
        String startDate = cleanCell(cellValue(row.getCell(14), formatter, evaluator));

        return !name.isBlank()
                && !"TOTAL".equalsIgnoreCase(name)
                && nationalId.length() >= 8
                && !phone.isBlank()
                && isPositiveDecimalLike(amount)
                && isIntegerInRange(duration, 1, 6)
                && parseDate(startDate) != null;
    }

    private static Map<String, String> mapMonthlyPortfolioRow(
            Row row,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        String fullName = cleanCell(cellValue(row.getCell(1), formatter, evaluator));
        String[] names = splitName(fullName);

        String amount = cleanCell(cellValue(row.getCell(4), formatter, evaluator));
        String applicationFee = cleanCell(cellValue(row.getCell(5), formatter, evaluator));
        String sourceRate = cleanCell(cellValue(row.getCell(6), formatter, evaluator));
        String duration = cleanCell(cellValue(row.getCell(7), formatter, evaluator));
        String startDate = normalizeDateString(cellValue(row.getCell(14), formatter, evaluator));
        String nextDueDate = normalizeDateString(cellValue(row.getCell(15), formatter, evaluator));

        BigDecimal principalPaid = decimalOrZero(cellValue(row.getCell(26), formatter, evaluator));
        BigDecimal interestPaid = decimalOrZero(cellValue(row.getCell(25), formatter, evaluator));
        BigDecimal managementPaid = decimalOrZero(cellValue(row.getCell(23), formatter, evaluator));
        BigDecimal managementOutstanding = decimalOrZero(cellValue(row.getCell(21), formatter, evaluator));

        // IMPORTANT: columns V/W/X/Y in the historical workbook are
        // "TOTAL MANAGEMENT FEE BALANCE", "TOTAL PROCESSING FEE BALANCE",
        // "PAID MANAGEMENT FEE" and "PAID PROCESSING FEE". They are NOT
        // the Noble Loan application-fee paid/outstanding fields.
        // The actual one-time application fee is column F (APPLICATION FEES).
        // Treat that historical charge as paid at disbursement because the
        // workbook does not contain a separate application-fee payment ledger.
        // This prevents old recurring processing-fee balances from being
        // incorrectly reconciled against the one-time application fee.
        BigDecimal applicationFeeAmount = decimalOrZero(applicationFee);
        BigDecimal applicationOutstanding = BigDecimal.ZERO;
        BigDecimal applicationPaid = applicationFeeAmount;

        BigDecimal interestOutstanding = decimalOrZero(cellValue(row.getCell(28), formatter, evaluator));
        BigDecimal principalOutstanding = decimalOrZero(cellValue(row.getCell(29), formatter, evaluator));
        BigDecimal penalties = decimalOrZero(cellValue(row.getCell(27), formatter, evaluator));

        BigDecimal totalInterest = money(interestPaid.add(interestOutstanding));
        BigDecimal totalManagement = money(managementPaid.add(managementOutstanding));
        BigDecimal totalRepayable = money(
                decimalOrZero(amount)
                        .add(totalInterest)
                        .add(totalManagement));
        BigDecimal totalPaid = money(
                principalPaid
                        .add(interestPaid)
                        .add(managementPaid));

        String status = deriveMonthlyStatus(
                row,
                principalOutstanding,
                interestOutstanding,
                managementOutstanding,
                penalties,
                formatter,
                evaluator);

        Map<String, String> out = new LinkedHashMap<>();
        out.put("national_id", cleanNationalId(cellValue(row.getCell(2), formatter, evaluator)));
        out.put("first_name", names[0]);
        out.put("last_name", names[1]);
        out.put("phone", cleanCell(cellValue(row.getCell(3), formatter, evaluator)));
        out.put("gender", "UNKNOWN");
        out.put("marital_status", "UNKNOWN");
        out.put("loan_type", "PERSONAL");
        out.put("amount", amount);
        out.put("interest_rate", normalizeImportedRate(sourceRate));
        out.put("interest_rate_type", "MONTHLY");
        out.put("management_fee_rate", "5.00");
        out.put("duration_months", duration);
        out.put("start_date", startDate);
        out.put("next_due_date", nextDueDate);
        out.put("status", status);
        out.put("currency", "RWF");
        out.put("loan_reference", cleanCell(cellValue(row.getCell(0), formatter, evaluator)));
        out.put("total_paid", totalPaid.toPlainString());
        out.put("outstanding_balance", money(principalOutstanding).toPlainString());
        out.put("total_repayable", totalRepayable.toPlainString());
        out.put("principal_paid", principalPaid.toPlainString());
        // Source principal balance is retained for reconciliation/audit only.
        // Legacy formulas can be stale; LegacyLoanImportRowService derives the
        // authoritative current principal balance from amount - principal_paid.
        out.put("principal_balance", principalOutstanding.toPlainString());
        out.put("interest_paid", interestPaid.toPlainString());
        out.put("interest_outstanding", interestOutstanding.toPlainString());
        out.put("management_fee_paid", managementPaid.toPlainString());
        out.put("total_management_fee_balance", managementOutstanding.toPlainString());
        out.put("application_fee", money(decimalOrZero(applicationFee)).toPlainString());
        out.put("application_fee_paid", applicationPaid.toPlainString());
        out.put("application_fee_outstanding", applicationOutstanding.toPlainString());
        out.put("penalties_assessed", penalties.toPlainString());
        out.put("penalties_paid", "0.00");
        out.put("notes", "Imported from Noble Loan historical portfolio workbook");
        return out;
    }

    private static String deriveMonthlyStatus(
            Row row,
            BigDecimal principalOutstanding,
            BigDecimal interestOutstanding,
            BigDecimal managementOutstanding,
            BigDecimal penalties,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        // A restructure marker exists in the historical due-date columns.
        for (int i = 15; i <= 20; i++) {
            String value = cleanCell(cellValue(row.getCell(i), formatter, evaluator));
            if (value.toUpperCase(Locale.ROOT).contains("RESTRUCTURE")) {
                return "RESTRUCTURED";
            }
        }

        if (principalOutstanding.compareTo(BigDecimal.ZERO) == 0
                && interestOutstanding.compareTo(BigDecimal.ZERO) == 0
                && managementOutstanding.compareTo(BigDecimal.ZERO) == 0
                && penalties.compareTo(BigDecimal.ZERO) == 0) {
            return "PAID";
        }

        return "ACTIVE";
    }

    private static boolean looksLikeCreditPortfolioRow(
            Row row,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        if (row == null || row.getLastCellNum() < CREDIT_MIN_COLUMNS) {
            return false;
        }

        String name = cleanCell(cellValue(row.getCell(1), formatter, evaluator));
        String nationalId = cleanNationalId(cellValue(row.getCell(2), formatter, evaluator));
        String phone = cleanCell(cellValue(row.getCell(3), formatter, evaluator));
        String gender = cleanCell(cellValue(row.getCell(4), formatter, evaluator));
        String loanType = cleanCell(cellValue(row.getCell(9), formatter, evaluator));
        String amount = cleanCell(cellValue(row.getCell(20), formatter, evaluator));
        String duration = cleanCell(cellValue(row.getCell(29), formatter, evaluator));
        String startDate = cleanCell(cellValue(row.getCell(21), formatter, evaluator));

        return !name.isBlank()
                && nationalId.length() >= 8
                && !phone.isBlank()
                && isGender(gender)
                && !loanType.isBlank()
                && isPositiveDecimalLike(amount)
                && isIntegerInRange(duration, 1, 6)
                && parseDate(startDate) != null;
    }

    private static Map<String, String> mapCreditPortfolioRow(
            Row row,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        String fullName = cleanCell(cellValue(row.getCell(1), formatter, evaluator));
        String[] names = splitName(fullName);
        String amount = cleanCell(cellValue(row.getCell(20), formatter, evaluator));
        String startDate = normalizeDateString(cellValue(row.getCell(21), formatter, evaluator));
        String duration = cleanCell(cellValue(row.getCell(29), formatter, evaluator));

        Map<String, String> out = new LinkedHashMap<>();
        out.put("national_id", cleanNationalId(cellValue(row.getCell(2), formatter, evaluator)));
        out.put("first_name", names[0]);
        out.put("last_name", names[1]);
        out.put("phone", cleanCell(cellValue(row.getCell(3), formatter, evaluator)));
        out.put("gender", cleanCell(cellValue(row.getCell(4), formatter, evaluator)));
        out.put("marital_status", cleanCell(cellValue(row.getCell(7), formatter, evaluator)));
        out.put("loan_type", cleanLoanType(cellValue(row.getCell(9), formatter, evaluator)));
        out.put("amount", amount);
        out.put("interest_rate", "5.00");
        out.put("interest_rate_type", "MONTHLY");
        out.put("management_fee_rate", "5.00");
        out.put("duration_months", duration);
        out.put("start_date", startDate);
        out.put("status", "ACTIVE");
        out.put("currency", "RWF");
        out.put("loan_reference", cleanCell(cellValue(row.getCell(0), formatter, evaluator)));
        out.put("notes",
                "Imported from Noble Loan credit/portfolio workbook layout; historical financial components were not available in this worksheet.");
        return out;
    }

    private static List<Map<String, String>> readMonthlyRows(
            Sheet sheet,
            int startRow,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            if (looksLikeMonthlyPortfolioRow(row, formatter, evaluator)) {
                rows.add(mapMonthlyPortfolioRow(row, formatter, evaluator));
            }
        }
        return rows;
    }

    private static List<Map<String, String>> readCreditRows(
            Sheet sheet,
            int startRow,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            if (looksLikeCreditPortfolioRow(row, formatter, evaluator)) {
                rows.add(mapCreditPortfolioRow(row, formatter, evaluator));
            }
        }
        return rows;
    }

    private static List<Map<String, String>> readStandardRows(
            Sheet sheet,
            int headerRow,
            List<String> headers,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        List<Map<String, String>> rows = new ArrayList<>();
        for (int rowIndex = headerRow + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                values.put(
                        headers.get(i),
                        cellValue(row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL), formatter, evaluator));
            }

            if (!isMeaningfullyBlank(values)) {
                rows.add(values);
            }
        }
        return rows;
    }

    // ---------------------------------------------------------------------
    // Canonicalization / duplicate protection
    // ---------------------------------------------------------------------

    private static int headerScore(List<String> headers) {
        Set<String> normalized = new HashSet<>(headers);
        int score = 0;
        score += containsAny(normalized, "national_id", "id_number", "id", "national_identity") ? 3 : 0;
        score += containsAny(normalized, "first_name", "firstname", "names", "name") ? 2 : 0;
        score += containsAny(normalized, "phone", "telephone", "mobile") ? 1 : 0;
        score += containsAny(normalized, "amount", "amount_disbursed", "principal", "loan_amount") ? 2 : 0;
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
            case "application_fee", "applicationfee" -> "application_fee";
            case "application_fee_paid", "applicationfee_paid" -> "application_fee_paid";
            case "application_fee_outstanding", "applicationfee_outstanding" -> "application_fee_outstanding";
            default -> normalizeHeader(header);
        };
    }

    private static List<Map<String, String>> canonicalizeRows(List<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        List<Map<String, String>> result = new ArrayList<>(rows.size());
        for (Map<String, String> source : rows) {
            Map<String, String> canonical = canonicalizeRow(source);
            if (!isMeaningfullyBlank(canonical)) {
                result.add(canonical);
            }
        }
        return result;
    }

    private static Map<String, String> canonicalizeRow(Map<String, String> source) {
        Map<String, String> row = new LinkedHashMap<>();
        if (source == null) {
            return row;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            row.put(canonicalHeader(entry.getKey()), cleanCell(entry.getValue()));
        }
        normalizeNamesAlias(row);
        normalizeHistoricalPortfolioAliases(row);
        normalizeImportedRateField(row, "interest_rate");
        normalizeImportedRateField(row, "management_fee_rate");
        normalizeImportedRateField(row, "application_fee_rate");
        if (row.containsKey("start_date")) {
            row.put("start_date", normalizeDateString(row.get("start_date")));
        }
        if (row.containsKey("next_due_date")) {
            row.put("next_due_date", normalizeDateString(row.get("next_due_date")));
        }
        return row;
    }

    private static void normalizeNamesAlias(Map<String, String> row) {
        String names = row.get("names");
        if ((row.get("first_name") == null || row.get("first_name").isBlank())
                && names != null && !names.isBlank()) {
            String[] split = splitName(names);
            row.put("first_name", split[0]);
            row.put("last_name", split[1]);
        }
    }

    private static void normalizeHistoricalPortfolioAliases(Map<String, String> row) {
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

    private static String duplicateKey(Map<String, String> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }

        String reference = cleanCell(row.get("loan_reference"));
        if (!reference.isBlank() && !"null".equalsIgnoreCase(reference)) {
            return "REF|" + reference.toUpperCase(Locale.ROOT);
        }

        String nationalId = cleanNationalId(row.get("national_id"));
        String startDate = normalizeDateString(row.get("start_date"));
        String amount = cleanCell(row.get("amount")).replace(",", "");
        if (!nationalId.isBlank() && !startDate.isBlank() && !amount.isBlank()) {
            return "COMPOSITE|" + nationalId + "|" + startDate + "|" + amount;
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // Cell / date helpers
    // ---------------------------------------------------------------------

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
        String normalized = cleanCell(value).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (normalized.isBlank()) {
            return "PERSONAL";
        }
        if (normalized.contains("BUSINESS") || normalized.contains("SME")) {
            return "BUSINESS";
        }
        if (normalized.contains("AGRI")) {
            return "AGRICULTURAL";
        }
        if (normalized.contains("SALARY")) {
            return "SALARY_ADVANCE";
        }
        if (normalized.contains("AUTO") || normalized.contains("VEHICLE") || normalized.contains("CAR")) {
            return "AUTO";
        }
        if (normalized.contains("MORTGAGE") || normalized.contains("HOME")) {
            return "MORTGAGE";
        }
        return normalized;
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
        return new String[] { tokens[0], String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)) };
    }

    private static String normalizeDateString(String value) {
        String cleaned = cleanCell(value);
        if (cleaned.isBlank()) {
            return "";
        }

        LocalDate parsed = parseDate(cleaned);
        return parsed == null ? cleaned : parsed.toString();
    }

    private static LocalDate parseDate(String value) {
        String cleaned = cleanCell(value);
        if (cleaned.isBlank()) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(cleaned, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static void normalizeImportedRateField(Map<String, String> row, String key) {
        if (row.containsKey(key)) {
            row.put(key, normalizeImportedRate(row.get(key)));
        }
    }

    private static String normalizeImportedRate(String value) {
        String cleaned = cleanCell(value);
        if (cleaned.isBlank()) {
            return "5.00";
        }

        String lower = cleaned.toLowerCase(Locale.ROOT);
        boolean explicitPercent = lower.contains("%")
                || lower.endsWith("percent")
                || lower.endsWith("pct");

        String numeric = cleaned
                .replace("%", "")
                .replace(",", "")
                .replaceAll("(?i)percent\\s*$", "")
                .replaceAll("(?i)pct\\s*$", "")
                .trim();

        try {
            BigDecimal rate = new BigDecimal(numeric);
            if (rate.compareTo(BigDecimal.ZERO) < 0) {
                // Keep invalid data visible to the row-level validator instead
                // of silently turning it into the 5% default.
                return cleaned;
            }
            if (!explicitPercent && rate.compareTo(BigDecimal.ONE) <= 0) {
                rate = rate.multiply(BigDecimal.valueOf(100));
            }
            return rate.setScale(6, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        } catch (NumberFormatException e) {
            // Preserve invalid source text so LegacyLoanImportRowService can
            // return a precise per-row validation error rather than corrupting
            // the historical contract by silently defaulting it to 5%.
            return cleaned;
        }
    }

    private static boolean isGender(String value) {
        return "M".equalsIgnoreCase(value)
                || "F".equalsIgnoreCase(value)
                || "MALE".equalsIgnoreCase(value)
                || "FEMALE".equalsIgnoreCase(value);
    }

    private static boolean isPositiveDecimalLike(String value) {
        try {
            return new BigDecimal(cleanCell(value).replace(",", "")).compareTo(BigDecimal.ZERO) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isIntegerInRange(String value, int min, int max) {
        try {
            BigDecimal number = new BigDecimal(cleanCell(value).replace(",", ""));
            return number.stripTrailingZeros().scale() <= 0
                    && number.intValueExact() >= min
                    && number.intValueExact() <= max;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static BigDecimal decimalOrZero(String value) {
        try {
            String cleaned = cleanCell(value).replace(",", "");
            if (cleaned.isBlank()) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(cleaned).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP)
                : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String cellValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }

        if (cell.getCellType() == CellType.FORMULA) {
            // Prefer a live formula result. This matters for legacy portfolio
            // workbooks where principal/interest balances are formula-driven and
            // the cached result can be stale after a borrower payment.
            if (evaluator != null) {
                try {
                    return cleanCell(formatter.formatCellValue(cell, evaluator));
                } catch (RuntimeException ignored) {
                    // Fall through to the cached formula result. Some Excel
                    // structured-reference formulas are not supported by POI.
                }
            }

            CellType cachedType = cell.getCachedFormulaResultType();
            return switch (cachedType) {
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        LocalDate date = cell.getDateCellValue()
                                .toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate();
                        yield date.toString();
                    }
                    yield cleanCell(formatter.formatRawCellContents(
                            cell.getNumericCellValue(),
                            cell.getCellStyle().getDataFormat(),
                            cell.getCellStyle().getDataFormatString()));
                }
                case STRING -> cleanCell(cell.getStringCellValue());
                case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                default -> "";
            };
        }

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDate date = cell.getDateCellValue()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            return date.toString();
        }

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

    private static final class SheetLayout {
        private final LayoutType type;
        private final int startRow;
        private final List<String> headers;
        private final Sheet sheet;

        private SheetLayout(LayoutType type, int startRow, List<String> headers) {
            this(type, startRow, headers, null);
        }

        private SheetLayout(LayoutType type, int startRow, List<String> headers, Sheet sheet) {
            this.type = type;
            this.startRow = startRow;
            this.headers = headers;
            this.sheet = sheet;
        }

        static SheetLayout monthly(int row) {
            return new SheetLayout(LayoutType.MONTHLY_PORTFOLIO, row, List.of());
        }

        static SheetLayout credit(int row) {
            return new SheetLayout(LayoutType.CREDIT_PORTFOLIO, row, List.of());
        }

        static SheetLayout standard(int row, List<String> headers) {
            return new SheetLayout(LayoutType.STANDARD, row, headers);
        }
    }

    private enum LayoutType {
        MONTHLY_PORTFOLIO,
        CREDIT_PORTFOLIO,
        STANDARD
    }
}
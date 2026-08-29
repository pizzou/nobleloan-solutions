package com.patrick.fintech.loan_backend.util;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;

/**
 * Streaming XLSX/CSV reader used by the asynchronous production import path.
 *
 * Important properties:
 * - never creates an XSSFWorkbook
 * - supports all relevant Noble Loan workbook layouts
 * - scans every worksheet instead of stopping at the first sheet
 * - de-duplicates repeated historical worksheets
 * - preserves historical financial components when the source workbook provides
 * them
 * - emits one canonical row at a time to LegacyLoanImportRowService
 */
public final class StreamingLedgerFileParser {

    private static final int HEADER_SCAN_ROWS = 20;
    private static final int LAYOUT_SCAN_ROWS = 60;
    private static final int MAX_COLUMNS = 256;
    private static final int MONTHLY_MIN_COLUMNS = 30;
    private static final int CREDIT_MIN_COLUMNS = 35;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M/d/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M/d/uu").withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("d/M/uu").withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("dd-MM-uu").withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("d-M-uu").withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("dd-MMM-uu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("d-MMM-uu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("dd MMM uu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("d MMM uu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatter.ofPattern("MMM d, uu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART));

    private StreamingLedgerFileParser() {
    }

    public interface RowConsumer {
        void accept(long rowNumber, Map<String, String> row) throws Exception;
    }

    /**
     * Streams all supported records and returns the number of unique records
     * emitted.
     */
    public static long stream(
            String filename,
            InputStream input,
            long maxRows,
            RowConsumer consumer) throws IOException {

        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename is required.");
        }
        if (input == null) {
            throw new IllegalArgumentException("Input stream is required.");
        }
        if (maxRows <= 0) {
            throw new IllegalArgumentException("Maximum import rows must be greater than zero.");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("Row consumer is required.");
        }

        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return streamCsv(input, maxRows, consumer);
        }
        if (lower.endsWith(".xlsx")) {
            return streamXlsx(filename, input, maxRows, consumer);
        }
        if (lower.endsWith(".xls")) {
            throw new IllegalArgumentException(
                    "The asynchronous production importer supports XLSX and CSV. Convert legacy XLS files to XLSX first.");
        }
        throw new IllegalArgumentException("Only CSV and XLSX imports are supported for streaming.");
    }

    /**
     * Counts unique importable rows without invoking the row business service.
     * Used as a first pass so the UI can receive a truthful progress percentage.
     */
    public static long countRows(
            String filename,
            InputStream input,
            long maxRows) throws IOException {
        return stream(filename, input, maxRows, (rowNumber, row) -> {
            // Intentionally empty.
        });
    }

    // ---------------------------------------------------------------------
    // CSV
    // ---------------------------------------------------------------------

    private static long streamCsv(
            InputStream input,
            long maxRows,
            RowConsumer consumer) throws IOException {

        Set<String> seen = new HashSet<>();
        long emitted = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8), 64 * 1024)) {

            String headerLine = LedgerFileParser.readLogicalLine(reader);
            if (headerLine == null) {
                return 0;
            }

            List<String> headers = LedgerFileParser.splitForStreaming(headerLine).stream()
                    .map(LedgerFileParser::normalizeHeader)
                    .map(StreamingLedgerFileParser::canonicalHeader)
                    .toList();

            String line;
            long physicalRow = 1;
            while ((line = LedgerFileParser.readLogicalLine(reader)) != null) {
                physicalRow++;
                if (line.isBlank()) {
                    continue;
                }

                List<String> cells = LedgerFileParser.splitForStreaming(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < cells.size() ? normalizeStreamValue(cells.get(i)) : "");
                }

                row = canonicalizeRow(row);
                if (isBlank(row)) {
                    continue;
                }

                if (!seen.add(duplicateKey(row))) {
                    continue;
                }

                if (++emitted > maxRows) {
                    throw new IllegalArgumentException(
                            "Import exceeds the maximum row limit of " + maxRows + ".");
                }

                try {
                    consumer.accept(physicalRow, row);
                } catch (Exception e) {
                    throw new IOException("Import row application failed at row " + physicalRow + ".", e);
                }
            }
        }

        return emitted;
    }

    // ---------------------------------------------------------------------
    // XLSX
    // ---------------------------------------------------------------------

    private static long streamXlsx(
            String filename,
            InputStream input,
            long maxRows,
            RowConsumer consumer) throws IOException {

        try (OPCPackage packageHandle = OPCPackage.open(input)) {
            XSSFReader reader = new XSSFReader(packageHandle);
            StylesTable styles = reader.getStylesTable();
            ReadOnlySharedStringsTable sharedStrings = new ReadOnlySharedStringsTable(packageHandle);

            Set<String> seen = new HashSet<>();
            List<PendingRecord> pendingCreditRows = new ArrayList<>();
            boolean[] authoritativeLedgerSeen = { false };
            long[] emitted = { 0L };

            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream sheetInput = sheets.next()) {
                    StreamingHandler handler = new StreamingHandler(
                            maxRows,
                            consumer,
                            seen,
                            emitted,
                            pendingCreditRows,
                            authoritativeLedgerSeen);

                    XMLReader xmlReader = XMLReaderFactory.createXMLReader();
                    xmlReader.setContentHandler(
                            new XSSFSheetXMLHandler(
                                    styles,
                                    null,
                                    sharedStrings,
                                    handler,
                                    new org.apache.poi.ss.usermodel.DataFormatter(Locale.ROOT, true),
                                    false));

                    xmlReader.parse(new InputSource(sheetInput));
                }
            }

            // Credit/portfolio worksheets are a fallback source only. If the
            // workbook has no real loan-ledger sheet, emit their rows now.
            if (!authoritativeLedgerSeen[0]) {
                for (PendingRecord pending : pendingCreditRows) {
                    emitPending(
                            pending,
                            maxRows,
                            seen,
                            emitted,
                            consumer);
                }
            }

            return emitted[0];
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Streaming XLSX parsing failed.", e);
        }
    }

    private static void emitPending(
            PendingRecord pending,
            long maxRows,
            Set<String> seen,
            long[] emitted,
            RowConsumer consumer) throws Exception {

        String key = duplicateKey(pending.row);
        if (!seen.add(key)) {
            return;
        }
        if (emitted[0] >= maxRows) {
            throw new IllegalArgumentException(
                    "Import exceeds the maximum row limit of " + maxRows + ".");
        }
        emitted[0]++;
        consumer.accept(pending.rowNumber, pending.row);
    }

    private static final class PendingRecord {
        private final long rowNumber;
        private final Map<String, String> row;

        private PendingRecord(long rowNumber, Map<String, String> row) {
            this.rowNumber = rowNumber;
            this.row = row;
        }
    }

    private static final class StreamingHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final long maxRows;
        private final RowConsumer consumer;
        private final Set<String> seen;
        private final long[] emitted;
        private final List<PendingRecord> pendingCreditRows;
        private final boolean[] authoritativeLedgerSeen;

        private final List<Map<Integer, String>> probeRows = new ArrayList<>();
        private Map<Integer, String> current;
        private long physicalRow;
        private Layout layout = Layout.UNKNOWN;
        private List<String> headers = List.of();
        private int headerRow = -1;
        private boolean probeComplete;

        private StreamingHandler(
                long maxRows,
                RowConsumer consumer,
                Set<String> seen,
                long[] emitted,
                List<PendingRecord> pendingCreditRows,
                boolean[] authoritativeLedgerSeen) {
            this.maxRows = maxRows;
            this.consumer = consumer;
            this.seen = seen;
            this.emitted = emitted;
            this.pendingCreditRows = pendingCreditRows;
            this.authoritativeLedgerSeen = authoritativeLedgerSeen;
        }

        @Override
        public void startRow(int rowNum) {
            physicalRow = rowNum + 1L;
            current = new LinkedHashMap<>();
        }

        @Override
        public void endRow(int rowNum) {
            if (current == null || current.isEmpty()) {
                return;
            }

            if (!probeComplete) {
                probeRows.add(new LinkedHashMap<>(current));
                determineLayout(false);

                if (layout != Layout.UNKNOWN || probeRows.size() >= LAYOUT_SCAN_ROWS) {
                    if (layout == Layout.UNKNOWN) {
                        determineLayout(true);
                    }
                    probeComplete = true;

                    // Re-process probe rows through the selected layout. This is bounded
                    // to 60 rows and therefore remains safe for large workbooks.
                    for (int i = 0; i < probeRows.size(); i++) {
                        Map<Integer, String> probe = probeRows.get(i);
                        long rowNumber = i + 1L;
                        emitMapped(rowNumber, probe);
                    }
                    probeRows.clear();
                }
                return;
            }

            emitMapped(physicalRow, current);
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int column = columnIndex(cellReference);
            if (column < 0 || column >= MAX_COLUMNS) {
                return;
            }
            current.put(column, normalizeStreamValue(formattedValue));
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // Not relevant to ledger rows.
        }

        private void determineLayout(boolean finalAttempt) {
            if (layout != Layout.UNKNOWN) {
                return;
            }

            // Prefer positional Noble Loan layouts over generic headers.
            for (int i = 0; i < probeRows.size(); i++) {
                Map<Integer, String> row = probeRows.get(i);
                if (looksLikeMonthly(row)) {
                    layout = Layout.MONTHLY;
                    authoritativeLedgerSeen[0] = true;
                    return;
                }
            }

            for (int i = 0; i < probeRows.size(); i++) {
                Map<Integer, String> row = probeRows.get(i);
                if (looksLikeCredit(row)) {
                    layout = Layout.CREDIT;
                    return;
                }
            }

            for (int i = 0; i < probeRows.size(); i++) {
                Map<Integer, String> row = probeRows.get(i);
                List<String> candidateHeaders = normalizeHeaders(row);
                if (headerScore(candidateHeaders) >= 5) {
                    layout = Layout.STANDARD;
                    authoritativeLedgerSeen[0] = true;
                    headerRow = i + 1;
                    headers = candidateHeaders;
                    return;
                }
            }

            if (finalAttempt) {
                layout = Layout.UNKNOWN;
            }
        }

        private void emitMapped(long rowNumber, Map<Integer, String> indexed) {
            Map<String, String> mapped;

            switch (layout) {
                case MONTHLY -> {
                    if (!looksLikeMonthly(indexed)) {
                        return;
                    }
                    mapped = mapMonthly(indexed);
                }
                case CREDIT -> {
                    if (!looksLikeCredit(indexed)) {
                        return;
                    }
                    mapped = mapCredit(indexed);
                }
                case STANDARD -> {
                    if (rowNumber <= headerRow) {
                        return;
                    }
                    mapped = mapStandard(indexed);
                }
                default -> {
                    return;
                }
            }

            mapped = canonicalizeRow(mapped);
            if (isBlank(mapped)) {
                return;
            }

            if (layout == Layout.CREDIT) {
                pendingCreditRows.add(new PendingRecord(rowNumber, mapped));
                return;
            }

            String key = duplicateKey(mapped);
            if (!seen.add(key)) {
                return;
            }

            if (emitted[0] >= maxRows) {
                throw new IllegalArgumentException(
                        "Import exceeds the maximum row limit of " + maxRows + ".");
            }

            emitted[0]++;
            try {
                consumer.accept(rowNumber, mapped);
            } catch (Exception e) {
                throw new ImportRowRuntimeException(rowNumber, e);
            }
        }

        private Map<String, String> mapStandard(Map<Integer, String> row) {
            Map<String, String> out = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                out.put(headers.get(i), normalizeStreamValue(row.getOrDefault(i, "")));
            }
            return out;
        }

        private boolean looksLikeMonthly(Map<Integer, String> row) {
            if (maxIndex(row) + 1 < MONTHLY_MIN_COLUMNS) {
                return false;
            }
            String name = clean(row.get(1));
            String nationalId = clean(row.get(2)).replaceAll("\\s+", "");
            String phone = clean(row.get(3));
            String amount = clean(row.get(4));
            String duration = clean(row.get(7));
            String startDate = clean(row.get(14));
            return !name.isBlank()
                    && !"TOTAL".equalsIgnoreCase(name)
                    && nationalId.length() >= 8
                    && !phone.isBlank()
                    && positiveDecimal(amount)
                    && integerInRange(duration, 1, 6)
                    && parseDate(startDate) != null;
        }

        private Map<String, String> mapMonthly(Map<Integer, String> row) {
            String[] names = splitName(row.get(1));
            BigDecimal amount = decimal(row.get(4));
            BigDecimal applicationFee = decimal(row.get(5));
            BigDecimal principalPaid = decimal(row.get(26));
            BigDecimal interestPaid = decimal(row.get(25));
            BigDecimal managementPaid = decimal(row.get(23));
            BigDecimal managementOutstanding = decimal(row.get(21));
            // Historical columns 22/24 are old processing-fee balance and
            // paid amounts. They are not Noble Loan's one-time application fee
            // fields. The actual historical application fee is column 5.
            BigDecimal applicationOutstanding = BigDecimal.ZERO;
            BigDecimal applicationPaid = money(applicationFee);
            BigDecimal interestOutstanding = decimal(row.get(28));
            BigDecimal principalOutstanding = decimal(row.get(29));
            BigDecimal penalties = decimal(row.get(27));

            BigDecimal totalInterest = money(interestPaid.add(interestOutstanding));
            BigDecimal totalManagement = money(managementPaid.add(managementOutstanding));
            BigDecimal totalRepayable = money(amount.add(totalInterest).add(totalManagement));
            BigDecimal totalPaid = money(principalPaid.add(interestPaid).add(managementPaid));

            Map<String, String> out = new LinkedHashMap<>();
            out.put("national_id", clean(row.get(2)).replaceAll("\\s+", ""));
            out.put("first_name", names[0]);
            out.put("last_name", names[1]);
            out.put("phone", clean(row.get(3)));
            out.put("gender", "UNKNOWN");
            out.put("marital_status", "UNKNOWN");
            out.put("loan_type", "PERSONAL");
            out.put("amount", amount.toPlainString());
            out.put("interest_rate", normalizeRate(row.get(6)));
            out.put("interest_rate_type", "MONTHLY");
            out.put("management_fee_rate", "5.00");
            out.put("duration_months", clean(row.get(7)));
            out.put("start_date", normalizeDate(row.get(14)));
            out.put("next_due_date", normalizeDate(row.get(15)));
            out.put("status", monthlyStatus(row));
            out.put("currency", "RWF");
            out.put("loan_reference", clean(row.get(0)));
            out.put("total_paid", totalPaid.toPlainString());
            out.put("outstanding_balance", money(principalOutstanding).toPlainString());
            out.put("total_repayable", totalRepayable.toPlainString());
            out.put("principal_paid", principalPaid.toPlainString());
            out.put("interest_paid", interestPaid.toPlainString());
            out.put("interest_outstanding", interestOutstanding.toPlainString());
            out.put("management_fee_paid", managementPaid.toPlainString());
            out.put("total_management_fee_balance", managementOutstanding.toPlainString());
            out.put("application_fee", money(applicationFee).toPlainString());
            out.put("application_fee_paid", applicationPaid.toPlainString());
            out.put("application_fee_outstanding", applicationOutstanding.toPlainString());
            out.put("penalties_assessed", penalties.toPlainString());
            out.put("penalties_paid", "0.00");
            out.put("notes", "Imported from Noble Loan historical portfolio workbook");
            return out;
        }

        private boolean looksLikeCredit(Map<Integer, String> row) {
            if (maxIndex(row) + 1 < CREDIT_MIN_COLUMNS) {
                return false;
            }
            String name = clean(row.get(1));
            String nationalId = clean(row.get(2)).replaceAll("\\s+", "");
            String phone = clean(row.get(3));
            String gender = clean(row.get(4));
            String loanType = clean(row.get(9));
            String amount = clean(row.get(20));
            String duration = clean(row.get(29));
            String startDate = clean(row.get(21));
            return !name.isBlank()
                    && nationalId.length() >= 8
                    && !phone.isBlank()
                    && genderMatches(gender)
                    && !loanType.isBlank()
                    && positiveDecimal(amount)
                    && integerInRange(duration, 1, 6)
                    && parseDate(startDate) != null;
        }

        private Map<String, String> mapCredit(Map<Integer, String> row) {
            String[] names = splitName(row.get(1));
            Map<String, String> out = new LinkedHashMap<>();
            out.put("national_id", clean(row.get(2)).replaceAll("\\s+", ""));
            out.put("first_name", names[0]);
            out.put("last_name", names[1]);
            out.put("phone", clean(row.get(3)));
            out.put("gender", clean(row.get(4)));
            out.put("marital_status", clean(row.get(7)));
            out.put("loan_type", normalizeLoanType(row.get(9)));
            out.put("amount", clean(row.get(20)));
            out.put("interest_rate", "5.00");
            out.put("interest_rate_type", "MONTHLY");
            out.put("duration_months", clean(row.get(29)));
            out.put("start_date", normalizeDate(row.get(21)));
            out.put("status", "ACTIVE");
            out.put("currency", "RWF");
            out.put("loan_reference", clean(row.get(0)));
            out.put("notes",
                    "Imported from Noble Loan credit/portfolio workbook layout; historical financial components were not available in this worksheet.");
            return out;
        }

        private String monthlyStatus(Map<Integer, String> row) {
            for (int i = 15; i <= 20; i++) {
                if (clean(row.get(i)).toUpperCase(Locale.ROOT).contains("RESTRUCTURE")) {
                    return "RESTRUCTURED";
                }
            }
            if (decimal(row.get(29)).compareTo(BigDecimal.ZERO) == 0
                    && decimal(row.get(28)).compareTo(BigDecimal.ZERO) == 0
                    && decimal(row.get(21)).compareTo(BigDecimal.ZERO) == 0
                    && decimal(row.get(27)).compareTo(BigDecimal.ZERO) == 0) {
                return "PAID";
            }
            return "ACTIVE";
        }

        private List<String> normalizeHeaders(Map<Integer, String> row) {
            int max = maxIndex(row);
            List<String> result = new ArrayList<>();
            for (int i = 0; i <= max; i++) {
                result.add(canonicalHeader(row.getOrDefault(i, "")));
            }
            return result;
        }
    }

    // ---------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------

    private enum Layout {
        UNKNOWN,
        MONTHLY,
        CREDIT,
        STANDARD
    }

    private static final class ImportRowRuntimeException extends RuntimeException {
        private final long rowNumber;

        private ImportRowRuntimeException(long rowNumber, Exception cause) {
            super("Import row application failed at row " + rowNumber + ".", cause);
            this.rowNumber = rowNumber;
        }
    }

    private static int maxIndex(Map<Integer, String> row) {
        return row.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
    }

    private static int columnIndex(String reference) {
        if (reference == null || reference.isBlank()) {
            return -1;
        }
        int result = 0;
        int index = 0;
        while (index < reference.length() && Character.isLetter(reference.charAt(index))) {
            result = result * 26 + (Character.toUpperCase(reference.charAt(index)) - 'A' + 1);
            index++;
        }
        return result - 1;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String result = value.replace("\uFEFF", "").replace("\u00A0", " ").trim();
        while (result.length() >= 2
                && ((result.startsWith("'") && result.endsWith("'"))
                        || (result.startsWith("\"") && result.endsWith("\"")))) {
            result = result.substring(1, result.length() - 1).trim();
        }
        if (result.startsWith("'") || result.startsWith("’") || result.startsWith("‘") || result.startsWith("`")) {
            result = result.substring(1).trim();
        }
        return result;
    }

    private static String normalizeStreamValue(String value) {
        return clean(value);
    }

    private static String[] splitName(String value) {
        String normalized = clean(value);
        if (normalized.isBlank()) {
            return new String[] { "Unknown", "Unknown" };
        }
        String[] parts = normalized.split("\\s+");
        if (parts.length == 1) {
            return new String[] { parts[0], "Unknown" };
        }
        return new String[] { parts[0], String.join(" ", Arrays.copyOfRange(parts, 1, parts.length)) };
    }

    private static String canonicalHeader(String header) {
        String normalized = LedgerFileParser.normalizeHeader(header);
        return switch (normalized) {
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
            default -> normalized;
        };
    }

    private static int headerScore(List<String> headers) {
        Set<String> values = new HashSet<>(headers);
        int score = 0;
        score += hasAny(values, "national_id", "id_number", "id", "national_identity") ? 3 : 0;
        score += hasAny(values, "first_name", "firstname", "names", "name") ? 2 : 0;
        score += hasAny(values, "phone", "telephone", "mobile") ? 1 : 0;
        score += hasAny(values, "amount", "amount_disbursed", "principal", "loan_amount") ? 2 : 0;
        score += hasAny(values, "duration_months", "period_of_the_loan", "loan_period") ? 2 : 0;
        score += hasAny(values, "start_date", "disbursement_date", "date_disbursed") ? 2 : 0;
        return score;
    }

    private static boolean hasAny(Set<String> values, String... candidates) {
        for (String candidate : candidates) {
            if (values.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> canonicalizeRow(Map<String, String> source) {
        Map<String, String> row = new LinkedHashMap<>();
        if (source == null) {
            return row;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            row.put(canonicalHeader(entry.getKey()), clean(entry.getValue()));
        }

        if ((row.get("first_name") == null || row.get("first_name").isBlank())
                && row.get("names") != null && !row.get("names").isBlank()) {
            String[] names = splitName(row.get("names"));
            row.put("first_name", names[0]);
            row.put("last_name", names[1]);
        }

        if (row.containsKey("amount")) {
            row.putIfAbsent("interest_rate", "5.00");
            row.putIfAbsent("interest_rate_type", "MONTHLY");
            row.putIfAbsent("loan_type", "PERSONAL");
            row.putIfAbsent("status", "ACTIVE");
            row.putIfAbsent("gender", "UNKNOWN");
            row.putIfAbsent("marital_status", "UNKNOWN");
            row.putIfAbsent("currency", "RWF");
        }

        if (row.containsKey("start_date")) {
            row.put("start_date", normalizeDate(row.get("start_date")));
        }
        if (row.containsKey("next_due_date")) {
            row.put("next_due_date", normalizeDate(row.get("next_due_date")));
        }

        return row;
    }

    private static String duplicateKey(Map<String, String> row) {
        String reference = clean(row.get("loan_reference"));
        if (!reference.isBlank()) {
            return "REF|" + reference.toUpperCase(Locale.ROOT);
        }
        String nationalId = clean(row.get("national_id")).replaceAll("\\s+", "");
        String date = normalizeDate(row.get("start_date"));
        String amount = clean(row.get("amount")).replace(",", "");
        if (!nationalId.isBlank() && !date.isBlank() && !amount.isBlank()) {
            return "COMPOSITE|" + nationalId + "|" + date + "|" + amount;
        }
        return "ROW|" + Integer.toHexString(row.toString().hashCode());
    }

    private static boolean isBlank(Map<String, String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        return row.values().stream().allMatch(v -> v == null || v.isBlank());
    }

    private static BigDecimal decimal(String value) {
        try {
            String normalized = clean(value).replace(",", "");
            if (normalized.isBlank()) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean positiveDecimal(String value) {
        try {
            return decimal(value).compareTo(BigDecimal.ZERO) > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean integerInRange(String value, int min, int max) {
        try {
            BigDecimal number = new BigDecimal(clean(value).replace(",", ""));
            if (number.stripTrailingZeros().scale() > 0) {
                return false;
            }
            int integer = number.intValueExact();
            return integer >= min && integer <= max;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean genderMatches(String value) {
        return "M".equalsIgnoreCase(value)
                || "F".equalsIgnoreCase(value)
                || "MALE".equalsIgnoreCase(value)
                || "FEMALE".equalsIgnoreCase(value);
    }

    private static String normalizeRate(String value) {
        String normalized = clean(value);
        if (normalized.isBlank()) {
            return "5.00";
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        boolean explicitPercent = lower.contains("%")
                || lower.endsWith("percent")
                || lower.endsWith("pct");

        String numeric = normalized
                .replace("%", "")
                .replaceAll("(?i)percent\\s*$", "")
                .replaceAll("(?i)pct\\s*$", "")
                .replace(",", "")
                .trim();

        try {
            BigDecimal rate = new BigDecimal(numeric);
            if (rate.compareTo(BigDecimal.ZERO) < 0) {
                return normalized;
            }
            if (!explicitPercent && rate.compareTo(BigDecimal.ONE) <= 0) {
                rate = rate.multiply(BigDecimal.valueOf(100));
            }
            return rate.setScale(6, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        } catch (NumberFormatException e) {
            // Preserve the bad source value so the row validator reports it;
            // never silently change invalid contractual rates to 5%.
            return normalized;
        }
    }

    private static String normalizeLoanType(String value) {
        String normalized = clean(value).toUpperCase(Locale.ROOT).replace(' ', '_');
        if (normalized.contains("BUSINESS") || normalized.contains("SME"))
            return "BUSINESS";
        if (normalized.contains("AGRI"))
            return "AGRICULTURAL";
        if (normalized.contains("SALARY"))
            return "SALARY_ADVANCE";
        if (normalized.contains("AUTO") || normalized.contains("VEHICLE") || normalized.contains("CAR"))
            return "AUTO";
        if (normalized.contains("MORTGAGE") || normalized.contains("HOME"))
            return "MORTGAGE";
        return normalized.isBlank() ? "PERSONAL" : normalized;
    }

    private static String normalizeDate(String value) {
        String normalized = clean(value);
        if (normalized.isBlank()) {
            return "";
        }

        if (normalized.toUpperCase(Locale.ROOT).contains("RESTRUCTURE")) {
            return "";
        }

        LocalDate parsed = parseDate(normalized);
        return parsed == null ? normalized : parsed.toString();
    }

    private static LocalDate parseDate(String value) {
        String normalized = clean(value);
        if (normalized.isBlank()) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        try {
            BigDecimal serial = new BigDecimal(normalized);
            if (serial.compareTo(BigDecimal.ONE) >= 0
                    && serial.compareTo(BigDecimal.valueOf(2958465)) <= 0
                    && serial.stripTrailingZeros().scale() <= 0) {
                return LocalDate.of(1899, 12, 30).plusDays(serial.longValueExact());
            }
        } catch (NumberFormatException | ArithmeticException ignored) {
        }

        return null;
    }
}
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class StreamingLedgerFileParser {

    private static final int HEADER_SCAN_ROWS = 15;
    private static final int MAX_COLUMNS = 256;

    private StreamingLedgerFileParser() {
    }

    /**
     * Receives one logical data row.
     */
    @FunctionalInterface
    public interface RowConsumer {

        void accept(long rowNumber, Map<String, String> row) throws Exception;
    }

    public static long stream(
            String filename,
            InputStream input,
            long maxRows,
            RowConsumer consumer) throws IOException {

        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }

        if (input == null) {
            throw new IllegalArgumentException("Input stream is required");
        }

        if (consumer == null) {
            throw new IllegalArgumentException("Row consumer is required");
        }

        if (maxRows <= 0) {
            throw new IllegalArgumentException(
                    "Maximum row limit must be greater than zero");
        }

        String lower = filename.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".csv")) {
            return streamCsv(input, maxRows, consumer);
        }

        if (lower.endsWith(".xlsx")) {
            return streamXlsx(input, maxRows, consumer);
        }

        throw new IllegalArgumentException(
                "Only CSV and XLSX imports are supported for streaming. "
                        + "Convert XLS to XLSX first.");
    }

    private static long streamCsv(
            InputStream input,
            long maxRows,
            RowConsumer consumer) throws IOException {

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        input,
                        StandardCharsets.UTF_8),
                64 * 1024)) {

            String headerLine = readLogical(reader);

            if (headerLine == null || headerLine.isBlank()) {
                return 0;
            }

            List<String> headers = LedgerFileParser
                    .splitForStreaming(headerLine)
                    .stream()
                    .map(LedgerFileParser::normalizeHeader)
                    .toList();

            long dataRows = 0;
            String line;

            while ((line = readLogical(reader)) != null) {

                if (line.isBlank()) {
                    continue;
                }

                dataRows++;

                if (dataRows > maxRows) {
                    throw new IllegalArgumentException(
                            "Import exceeds the maximum row limit of "
                                    + maxRows);
                }

                List<String> cells = LedgerFileParser.splitForStreaming(line);

                Map<String, String> row = new LinkedHashMap<>();

                for (int i = 0; i < headers.size(); i++) {

                    String value = i < cells.size()
                            ? cells.get(i).trim()
                            : "";

                    row.put(headers.get(i), value);
                }

                if (row.values().stream().anyMatch(
                        value -> !value.isBlank())) {

                    try {
                        consumer.accept(dataRows + 1, row);
                    } catch (Exception exception) {
                        throw new IOException(
                                "Import row processing failed at row "
                                        + (dataRows + 1),
                                exception);
                    }
                }
            }

            return dataRows;
        }
    }

    /**
     * Streams XLSX using Apache POI's SAX/event model.
     *
     * <p>
     * No XSSFWorkbook is created.
     * </p>
     */
    private static long streamXlsx(
            InputStream input,
            long maxRows,
            RowConsumer consumer) throws IOException {

        try (OPCPackage packageFile = OPCPackage.open(input)) {

            XSSFReader reader = new XSSFReader(packageFile);

            StylesTable styles = reader.getStylesTable();

            ReadOnlySharedStringsTable sharedStrings = new ReadOnlySharedStringsTable(packageFile);

            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();

            while (sheets.hasNext()) {

                try (InputStream sheetInput = sheets.next()) {

                    StreamingHandler handler = new StreamingHandler(
                            maxRows,
                            consumer);

                    XMLReader xmlReader = XMLReaderFactory.createXMLReader();

                    XSSFSheetXMLHandler sheetHandler = new XSSFSheetXMLHandler(
                            styles,
                            null,
                            sharedStrings,
                            handler,
                            new org.apache.poi.ss.usermodel.DataFormatter(
                                    Locale.ROOT),
                            false);

                    xmlReader.setContentHandler(sheetHandler);

                    xmlReader.parse(
                            new InputSource(sheetInput));

                    if (handler.getRowsEmitted() > 0) {
                        return handler.getRowsEmitted();
                    }
                }
            }

            throw new IOException(
                    "No supported header row was found in the workbook.");

        } catch (IOException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new IOException(
                    "Streaming XLSX parsing failed",
                    exception);
        }
    }

    private static final class StreamingHandler
            implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final long maxRows;
        private final RowConsumer consumer;

        private final List<Map<Integer, String>> firstRows = new ArrayList<>();

        private Map<Integer, String> currentRow;

        private long physicalRow;

        private long rowsEmitted;

        private List<String> headers;

        private boolean headerFound;

        private long detectedHeaderRow = -1;

        private StreamingHandler(
                long maxRows,
                RowConsumer consumer) {
            this.maxRows = maxRows;
            this.consumer = consumer;
        }

        /**
         * Called by Apache POI when a worksheet row begins.
         */
        @Override
        public void startRow(int rowNumber) {

            physicalRow = (long) rowNumber + 1;

            currentRow = new LinkedHashMap<>();
        }

        /**
         * Called by Apache POI when a worksheet row ends.
         */
        @Override
        public void endRow(int rowNumber) {

            if (currentRow == null) {
                return;
            }

            /*
             * Discover the header from the first N rows.
             */
            if (!headerFound) {

                if (firstRows.size() < HEADER_SCAN_ROWS) {
                    firstRows.add(currentRow);
                }

                int score = headerScore(currentRow);

                if (score >= 5) {

                    headers = normalizeHeaders(currentRow);

                    headerFound = true;

                    detectedHeaderRow = physicalRow;
                } else if (firstRows.size() >= HEADER_SCAN_ROWS) {

                    /*
                     * No strong header found. Use the first non-empty row
                     * as a controlled fallback.
                     */
                    Map<Integer, String> fallback = firstRows.stream()
                            .filter(row -> !row.isEmpty())
                            .findFirst()
                            .orElse(null);

                    if (fallback != null) {

                        headers = normalizeHeaders(fallback);

                        headerFound = true;

                        detectedHeaderRow = findPhysicalHeaderRow(fallback);
                    }
                }

                return;
            }

            /*
             * Ignore the header itself and all rows before it.
             */
            if (physicalRow <= detectedHeaderRow) {
                return;
            }

            if (currentRow.isEmpty()) {
                return;
            }

            if (rowsEmitted >= maxRows) {
                throw new IllegalArgumentException(
                        "Import exceeds the maximum row limit of "
                                + maxRows);
            }

            Map<String, String> row = new LinkedHashMap<>();

            for (int index = 0; index < headers.size(); index++) {

                row.put(
                        headers.get(index),
                        currentRow.getOrDefault(index, ""));
            }

            /*
             * Do not create/emit completely empty rows.
             */
            boolean hasData = row.values()
                    .stream()
                    .anyMatch(value -> value != null
                            && !value.isBlank());

            if (!hasData) {
                return;
            }

            try {

                consumer.accept(
                        physicalRow,
                        row);

                rowsEmitted++;

            } catch (Exception exception) {

                /*
                 * The SAX API does not allow checked exceptions from
                 * SheetContentsHandler, so wrap the application exception.
                 * The outer parser converts it to IOException.
                 */
                throw new RowProcessingRuntimeException(
                        physicalRow,
                        exception);
            }
        }

        /**
         * IMPORTANT:
         * Must be public because it is declared public by
         * XSSFSheetXMLHandler.SheetContentsHandler.
         */
        @Override
        public void cell(
                String cellReference,
                String formattedValue,
                XSSFComment comment) {

            if (currentRow == null) {
                return;
            }

            int columnIndex = columnIndex(cellReference);

            if (columnIndex < 0 ||
                    columnIndex >= MAX_COLUMNS) {
                return;
            }

            currentRow.put(
                    columnIndex,
                    formattedValue == null
                            ? ""
                            : formattedValue.trim());
        }

        /**
         * IMPORTANT:
         * Must be public because it is declared public by
         * XSSFSheetXMLHandler.SheetContentsHandler.
         */
        @Override
        public void headerFooter(
                String text,
                boolean isHeader,
                String tagName) {
            /*
             * Ledger imports do not currently process worksheet
             * header/footer content.
             */
        }

        long getRowsEmitted() {
            return rowsEmitted;
        }

        /**
         * Converts Excel column references:
         *
         * A -> 0
         * B -> 1
         * Z -> 25
         * AA -> 26
         */
        private int columnIndex(String reference) {

            if (reference == null ||
                    reference.isBlank()) {
                return -1;
            }

            int result = 0;
            int position = 0;

            while (position < reference.length()
                    && Character.isLetter(
                            reference.charAt(position))) {

                result = result * 26
                        + (Character.toUpperCase(
                                reference.charAt(position)) - 'A' + 1);

                position++;
            }

            return result - 1;
        }

        private List<String> normalizeHeaders(
                Map<Integer, String> row) {

            int maxColumn = row.keySet()
                    .stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(-1);

            if (maxColumn < 0) {
                return List.of();
            }

            List<String> result = new ArrayList<>(maxColumn + 1);

            Set<String> used = new HashSet<>();

            for (int index = 0; index <= maxColumn; index++) {

                String header = LedgerFileParser.normalizeHeader(
                        row.getOrDefault(
                                index,
                                ""));

                /*
                 * Protect against duplicate column names.
                 */
                if (header.isBlank()) {
                    header = "column_" + (index + 1);
                }

                String uniqueHeader = header;

                int suffix = 2;

                while (!used.add(uniqueHeader)) {

                    uniqueHeader = header + "_" + suffix++;

                }

                result.add(uniqueHeader);
            }

            return result;
        }

        private int headerScore(
                Map<Integer, String> row) {

            Set<String> values = new HashSet<>();

            for (String value : row.values()) {

                if (value != null &&
                        !value.isBlank()) {

                    values.add(
                            LedgerFileParser.normalizeHeader(
                                    value));
                }
            }

            int score = 0;

            if (has(
                    values,
                    "national_id",
                    "id_number",
                    "id",
                    "national_identity")) {
                score += 3;
            }

            if (has(
                    values,
                    "first_name",
                    "firstname",
                    "names",
                    "name")) {
                score += 2;
            }

            if (has(
                    values,
                    "phone",
                    "telephone",
                    "mobile")) {
                score++;
            }

            if (has(
                    values,
                    "amount",
                    "amount_disbursed",
                    "principal")) {
                score += 2;
            }

            if (has(
                    values,
                    "duration_months",
                    "period_of_the_loan",
                    "loan_period")) {
                score += 2;
            }

            if (has(
                    values,
                    "start_date",
                    "disbursement_date",
                    "date_disbursed")) {
                score += 2;
            }

            return score;
        }

        private boolean has(
                Set<String> values,
                String... candidates) {

            for (String candidate : candidates) {

                if (values.contains(candidate)) {
                    return true;
                }
            }

            return false;
        }

        private int findPhysicalHeaderRow(
                Map<Integer, String> target) {

            for (int index = 0; index < firstRows.size(); index++) {

                if (firstRows.get(index) == target) {
                    return index + 1;
                }
            }

            return 1;
        }
    }

    /**
     * Runtime wrapper used because Apache POI's callback interface
     * does not permit checked exceptions.
     */
    private static final class RowProcessingRuntimeException
            extends RuntimeException {

        private final long rowNumber;

        private RowProcessingRuntimeException(
                long rowNumber,
                Exception cause) {

            super(
                    "Import row processing failed at row "
                            + rowNumber,
                    cause);

            this.rowNumber = rowNumber;
        }

        @SuppressWarnings("unused")
        public long getRowNumber() {
            return rowNumber;
        }
    }

    /**
     * Reads a CSV logical record, including quoted multiline fields.
     */
    private static String readLogical(
            BufferedReader reader) throws IOException {

        String line = reader.readLine();

        if (line == null) {
            return null;
        }

        StringBuilder result = new StringBuilder(line);

        while (!balanced(result)) {

            String next = reader.readLine();

            if (next == null) {
                break;
            }

            result
                    .append('\n')
                    .append(next);
        }

        return result.toString();
    }

    /**
     * Determines whether CSV quotes are balanced.
     */
    private static boolean balanced(
            CharSequence value) {

        boolean quoted = false;

        for (int index = 0; index < value.length(); index++) {

            char character = value.charAt(index);

            if (character == '"') {

                /*
                 * Escaped quote:
                 * ""
                 */
                if (index + 1 < value.length()
                        && value.charAt(index + 1) == '"') {

                    index++;

                    continue;
                }

                quoted = !quoted;
            }
        }

        return !quoted;
    }
}
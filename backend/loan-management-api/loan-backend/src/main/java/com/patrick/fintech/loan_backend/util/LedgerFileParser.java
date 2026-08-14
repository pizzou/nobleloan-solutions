package com.patrick.fintech.loan_backend.util;

import org.apache.poi.ss.usermodel.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LedgerFileParser {

    private LedgerFileParser() {
    }

    public static List<Map<String, String>> parse(String filename, InputStream in) throws IOException {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv"))
            return parseCsv(in);
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls"))
            return parseExcel(in);
        throw new IllegalArgumentException("Unsupported file type — please upload a .csv or .xlsx file.");
    }

    public static String normalizeHeader(String header) {
        if (header == null)
            return "";
        return header.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\-]+", "_");
    }

    // ---------- CSV ----------

    private static List<Map<String, String>> parseCsv(InputStream in) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = readLogicalLine(reader);
            if (headerLine == null)
                return rows;
            List<String> headers = splitCsvLine(headerLine).stream()
                    .map(LedgerFileParser::normalizeHeader).toList();

            String line;
            while ((line = readLogicalLine(reader)) != null) {
                if (line.isBlank())
                    continue;
                List<String> cells = splitCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < cells.size() ? cells.get(i).trim() : "");
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private static String readLogicalLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null)
            return null;
        StringBuilder buf = new StringBuilder(line);
        while (!isQuoteBalanced(buf)) {
            String next = reader.readLine();
            if (next == null)
                break; // malformed trailing quote — stop rather than loop forever
            buf.append('\n').append(next);
        }
        return buf.toString();
    }

    private static boolean isQuoteBalanced(CharSequence s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '"')
                count++;
        }
        return count % 2 == 0;
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
                } else if (c == '"')
                    inQuotes = false;
                else
                    cur.append(c);
            } else {
                if (c == '"')
                    inQuotes = true;
                else if (c == ',') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else
                    cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    // ---------- XLSX/XLS ----------

    private static List<Map<String, String>> parseExcel(InputStream in) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();

        Workbook wb;
        try {
            wb = WorkbookFactory.create(in);
        } catch (RuntimeException e) {

            throw new IOException("The uploaded Excel file could not be opened. " +
                    "It may be password-protected, corrupted, or not a valid .xlsx/.xls file.", e);
        }

        try (wb) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            Iterator<Row> it = sheet.iterator();
            if (!it.hasNext())
                return rows;

            Row headerRow = it.next();
            List<String> headers = readHeaderRow(headerRow, formatter, evaluator);

            while (it.hasNext()) {
                Row r = it.next();
                boolean allBlank = true;
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = r.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String val = cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
                    if (!val.isBlank())
                        allBlank = false;
                    row.put(headers.get(i), val);
                }
                if (!allBlank)
                    rows.add(row);
            }
        }
        return rows;
    }

    private static List<String> readHeaderRow(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> headers = new ArrayList<>();
        short lastCol = headerRow.getLastCellNum();
        for (int i = 0; i < lastCol; i++) {
            Cell c = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            headers.add(normalizeHeader(c == null ? "" : formatter.formatCellValue(c, evaluator)));
        }
        return headers;
    }
}
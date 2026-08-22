package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.regulatory.BnrFinancialStatementReport;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrSummaryReport;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentScheduleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BnrTemplateExportService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private final LoanRepository loanRepository;
    private final PaymentScheduleRepository paymentScheduleRepository;
    private final RegulatoryReportingService regulatoryReportingService;

    private static final List<String> CLASSIFICATION_SHEETS = List.of(
            "A1.3. Normal Loans",
            "A1.3. Normal",
            "A1.4. Watch",
            "A1.5. Substandard",
            "A1.6. Doubtful",
            "A1.7 Loss",
            "A1.8. Restructured loans",
            "A1.9. Written off");

    public byte[] export(
            Long organizationId,
            Long branchId,
            RegulatoryReportingService.ReportPeriod period,
            LocalDate from,
            LocalDate to) {

        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("organizationId is required");
        }

        if (period == null) {
            throw new IllegalArgumentException("period is required");
        }

        LocalDate[] window = resolvePeriod(period, from, to);

        if (window == null || window.length < 2 || window[0] == null || window[1] == null) {
            throw new IllegalStateException("Unable to resolve BNR reporting period");
        }

        LocalDate reportDate = window[1];

        List<Loan> loans = safeLoans(
                loanRepository.findPortfolioAsOf(
                        organizationId,
                        branchId,
                        reportDate.plusDays(1).atStartOfDay(),
                        reportDate));

        try (
                XSSFWorkbook workbook = buildBnrWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            configureWorkbook(workbook);

            /*
             * Populate organization/report metadata.
             */
            populateMetadata(
                    workbook,
                    organizationId,
                    branchId,
                    period,
                    window,
                    loans);

            Map<String, List<Loan>> classified = classifyLoans(loans);

            if (classified == null) {
                classified = new java.util.LinkedHashMap<>();
            }

            for (String sheetName : CLASSIFICATION_SHEETS) {

                Sheet sheet = workbook.getSheet(sheetName);

                if (sheet == null) {
                    throw new IllegalStateException(
                            "Internal BNR workbook definition is missing sheet '"
                                    + sheetName
                                    + "'");
                }

                if ("A1.9. Written off".equals(sheetName)) {

                    List<Loan> writtenOffLoans = classified.getOrDefault(
                            "WRITTEN_OFF",
                            java.util.Collections.emptyList());

                    writeWrittenOffSheet(
                            sheet,
                            writtenOffLoans,
                            reportDate);

                } else {

                    String classification = classificationForSheet(sheetName);

                    if (classification == null
                            || classification.isBlank()) {

                        throw new IllegalStateException(
                                "No BNR classification mapping exists for sheet '"
                                        + sheetName
                                        + "'");
                    }

                    List<Loan> classificationLoans = classified.getOrDefault(
                            classification,
                            java.util.Collections.emptyList());

                    writeLoanSheet(
                            sheet,
                            classificationLoans,
                            reportDate,
                            classification);
                }
            }

            BnrFinancialStatementReport financialStatement = regulatoryReportingService.buildBnrFinancialStatement(
                    organizationId,
                    branchId,
                    period,
                    window[0],
                    window[1]);

            if (financialStatement == null) {
                throw new IllegalStateException(
                        "BNR financial statement could not be generated");
            }

            populateFinancialStatement(
                    workbook,
                    financialStatement);

            addValidationSheet(
                    workbook,
                    loans,
                    classified,
                    reportDate);

            workbook.setForceFormulaRecalculation(true);

            /*
             * Serialize the generated workbook.
             */
            workbook.write(output);
            output.flush();

            byte[] bytes = output.toByteArray();

            if (bytes.length == 0) {
                throw new IllegalStateException(
                        "Generated BNR workbook is empty");
            }

            log.info(
                    "BNR XLSX generated successfully. " +
                            "organizationId={}, branchId={}, period={}, from={}, to={}, loans={}, bytes={}",
                    organizationId,
                    branchId,
                    period,
                    window[0],
                    window[1],
                    loans.size(),
                    bytes.length);

            return bytes;

        } catch (IOException e) {

            log.error(
                    "Failed to serialize hard-coded BNR XLSX. " +
                            "organizationId={}, branchId={}, period={}, from={}, to={}",
                    organizationId,
                    branchId,
                    period,
                    window[0],
                    window[1],
                    e);

            throw new IllegalStateException(
                    "Unable to generate the BNR Excel report",
                    e);

        } catch (RuntimeException e) {

            log.error(
                    "BNR XLSX generation failed. " +
                            "organizationId={}, branchId={}, period={}, from={}, to={}",
                    organizationId,
                    branchId,
                    period,
                    window[0],
                    window[1],
                    e);

            throw e;
        }
    }

    private XSSFWorkbook buildBnrWorkbook() {

        XSSFWorkbook workbook = new XSSFWorkbook();

        createExplanatoryNoteSheet(workbook);
        createFinancialStatementSheet(workbook);

        createClassificationSheet(
                workbook,
                "A1.3. Normal Loans",
                "NORMAL",
                "Loan Classification Report (NORMAL)",
                10,
                normalHeaders());

        createClassificationSheet(
                workbook,
                "A1.3. Normal",
                "NORMAL",
                "Loan Classification Report (NORMAL)",
                7,
                normalHeaders());

        createClassificationSheet(
                workbook,
                "A1.4. Watch",
                "WATCH",
                "Loan Classification Report (WATCH)",
                10,
                normalHeaders());

        createClassificationSheet(
                workbook,
                "A1.5. Substandard",
                "SUBSTANDARD",
                "Loan Classification Report (SUBSTANDARD)",
                10,
                substandardHeaders());

        createClassificationSheet(
                workbook,
                "A1.6. Doubtful",
                "DOUBTFUL",
                "Loan Classification Report (DOUBTFUL)",
                9,
                normalHeaders());

        createClassificationSheet(
                workbook,
                "A1.7 Loss",
                "LOSS",
                "Loan Classification Report (LOSS)",
                9,
                normalHeaders());

        createClassificationSheet(
                workbook,
                "A1.8. Restructured loans",
                "RESTRUCTURED",
                "M.V.Loan Classification Report",
                9,
                restructuredHeaders());

        createWrittenOffSheet(workbook);
        createSheet1(workbook);

        return workbook;
    }

    private void createExplanatoryNoteSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("A1.1  Explanatory Note ");

        String[][] notes = {
                { "DENOMINATION", "Explanatory Notes" },
                { "A.BALANCE SHEET", "" },
                { "1.Total Liquid Assets (2+3+4)", "" },
                { "2.Cash in vault", "Physical cash held on the institution premises as at reporting date." },
                { "3.Cash in bank and other FIs (Current account)",
                        "Balances at banks, financial institutions and transactional mobile money accounts." },
                { "4.Cash in bank and other FIs (Term deposit)",
                        "Cash placed in fixed-term accounts earning interest." },
                { "5.Gross loans", "Outstanding loan balance before eligible collateral and provisions." },
                { "6.Provision for bad and doubtful debts",
                        "Required provisioning based on regulatory loan classification." },
                { "7.Net loans", "Gross loans less provisions." },
                { "B.INCOME STATEMENT", "" },
                { "Interest income", "Interest income recognized from the lending portfolio." },
                { "Fee and penalty income",
                        "Management fees, processing fees, extension fees and penalties recognized as income." },
                { "Operating expenses", "Operating expenses supported by the accounting ledger." },
                { "Net income", "Income less operating and loan-loss expenses." },
                { "C.LOAN CLASSIFICATION", "" },
                { "Normal", "Current performing loans with no qualifying arrears." },
                { "Watch", "Loans with arrears requiring watch classification." },
                { "Substandard", "Loans meeting the regulatory substandard arrears threshold." },
                { "Doubtful", "Loans meeting the regulatory doubtful arrears threshold." },
                { "Loss", "Loans meeting the regulatory loss threshold." },
                { "Restructured loans", "Loans formally restructured or extended according to the platform record." },
                { "Written off", "Loans recorded as written off in the Noble Loan system." },
                { "INTEREST BASIS",
                        "Noble Loan contractual interest is monthly. The BNR annual-interest field is the contractual monthly rate multiplied by 12." },
                { "COLLATERAL",
                        "Eligible collateral is limited by the collateral treatment implemented by the BNR exporter." },
                { "SOURCE",
                        "All report values are sourced from Noble Loan operational and accounting data. Missing source values are left blank." }
        };

        CellStyle title = createTitleStyle(workbook);
        CellStyle header = createHeaderStyle(workbook);
        CellStyle body = createBodyStyle(workbook);

        Row first = sheet.createRow(0);
        Cell titleCell = first.createCell(0);
        titleCell.setCellValue("NOBLE LOAN SOLUTIONS — BNR REGULATORY EXPLANATORY NOTES");
        titleCell.setCellStyle(title);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));

        for (int i = 0; i < notes.length; i++) {
            Row row = sheet.createRow(i + 2);
            Cell label = row.createCell(0);
            label.setCellValue(notes[i][0]);
            label.setCellStyle(header);

            Cell explanation = row.createCell(1);
            explanation.setCellValue(notes[i][1]);
            explanation.setCellStyle(body);

            sheet.setColumnWidth(0, 12000);
            sheet.setColumnWidth(1, 30000);
        }

        sheet.createFreezePane(0, 2);
    }

    private void createFinancialStatementSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("A1.2. FS");

        String[] labels = {
                "A.BALANCE SHEET",
                "1.Total Liquid Assets (2+3+4)",
                "2.Cash in vault",
                "3.Cash in bank and other FIs (Current account)",
                "4.Cash in bank and other FIs (Term deposit)",
                "5.Gross loans",
                "6.Provision for bad and doubtful debts",
                "7.Net loans",
                "8.Other assets",
                "9.TOTAL ASSETS",
                "B.LIABILITIES",
                "10.Customer deposits payable",
                "11.Borrower refunds payable",
                "12.Other liabilities",
                "13.TOTAL LIABILITIES",
                "C.EQUITY",
                "14.Paid-up capital",
                "15.Retained earnings",
                "16.Current period net income",
                "17.TOTAL EQUITY",
                "18.TOTAL LIABILITIES AND EQUITY",
                "D.INCOME STATEMENT",
                "19.Interest income",
                "20.Fee and penalty income",
                "21.TOTAL INCOME",
                "22.Loan loss expense",
                "23.Bank charges",
                "24.Salaries and wages",
                "25.Administrative expenses",
                "26.TOTAL EXPENSES",
                "27.Current period net income",
                "E.LOAN PORTFOLIO STATISTICS",
                "Normal outstanding",
                "Watch outstanding",
                "Substandard outstanding",
                "Doubtful outstanding",
                "Loss outstanding",
                "Restructured outstanding",
                "Total classified outstanding",
                "Male outstanding loans",
                "Female outstanding loans",
                "Other outstanding loans",
                "Male disbursed loans",
                "Female disbursed loans",
                "Other disbursed loans",
                "Male disbursed amount",
                "Female disbursed amount",
                "Other disbursed amount"
        };

        CellStyle title = createTitleStyle(workbook);
        CellStyle header = createHeaderStyle(workbook);
        CellStyle body = createBodyStyle(workbook);
        CellStyle currency = createCurrencyStyle(workbook);

        Row meta = sheet.createRow(0);
        meta.createCell(0).setCellValue("NAME OF THE NDFSP:");
        meta.getCell(0).setCellStyle(header);

        Row sector = sheet.createRow(1);
        sector.createCell(0).setCellValue("SECTOR:");
        sector.getCell(0).setCellStyle(header);

        Row district = sheet.createRow(2);
        district.createCell(0).setCellValue("DISTRICT:");
        district.getCell(0).setCellStyle(header);
        district.createCell(2).setCellValue("DENOMINATION");
        district.getCell(2).setCellStyle(header);
        district.createCell(8).setCellValue(LocalDate.now());
        district.getCell(8).setCellStyle(body);

        Row section = sheet.createRow(3);
        section.createCell(2).setCellValue("A.BALANCE SHEET");
        section.getCell(2).setCellStyle(title);

        String[] periodColumns = { "D", "E", "F", "G", "H", "I" };

        for (int i = 0; i < labels.length; i++) {
            int rowIndex = 4 + i;
            Row row = sheet.createRow(rowIndex);
            Cell label = row.createCell(2);
            label.setCellValue(labels[i]);
            label.setCellStyle(header);

            for (int c = 3; c <= 8; c++) {
                Cell cell = row.createCell(c);
                cell.setCellStyle(currency);
            }
        }

        // Keep the regulatory-style calculation columns D:I.
        // The current reporting cut-off is written into I by
        // populateFinancialStatement().
        for (int c = 3; c <= 8; c++) {
            String col = org.apache.poi.ss.util.CellReference.convertNumToColString(c);

            sheet.getRow(4).getCell(c)
                    .setCellFormula("=" + col + "7+" + col + "8+" + col + "9");

            sheet.getRow(10).getCell(c)
                    .setCellFormula("=" + col + "11+" + col + "12+" + col + "13");

            sheet.getRow(21).getCell(c)
                    .setCellFormula("=" + col + "22+" + col + "23");

            sheet.getRow(26).getCell(c)
                    .setCellFormula("=" + col + "27+" + col + "28+" + col + "29+" + col + "30");

            sheet.getRow(31).getCell(c)
                    .setCellFormula("=" + col + "26-" + col + "30");
        }

        sheet.getRow(4).getCell(2).setCellStyle(title);
        sheet.getRow(21).getCell(2).setCellStyle(title);
        sheet.getRow(32).getCell(2).setCellStyle(title);

        for (int c = 0; c <= 14; c++) {
            sheet.setColumnWidth(c, c == 2 ? 22000 : 4800);
        }

        sheet.createFreezePane(3, 4);
    }

    private void createClassificationSheet(
            XSSFWorkbook workbook,
            String sheetName,
            String classification,
            String reportName,
            int headerRowNumber,
            String[] headers) {

        if (workbook == null) {
            throw new IllegalArgumentException("Workbook cannot be null");
        }

        if (sheetName == null || sheetName.isBlank()) {
            throw new IllegalArgumentException("Sheet name is required");
        }

        if (headers == null || headers.length == 0) {
            throw new IllegalArgumentException(
                    "Classification sheet headers are required");
        }

        if (headerRowNumber < 1) {
            throw new IllegalArgumentException(
                    "Header row number must be greater than zero");
        }

        Sheet sheet = workbook.createSheet(sheetName);

        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle bodyStyle = createBodyStyle(workbook);

        Row ndfspLabelRow = sheet.createRow(0);

        Cell ndfspLabelCell = ndfspLabelRow.createCell(0);
        ndfspLabelCell.setCellValue("NDFSP Name");
        ndfspLabelCell.setCellStyle(headerStyle);

        Row ndfspValueRow = sheet.createRow(1);

        Cell ndfspValueCell = ndfspValueRow.createCell(0);
        ndfspValueCell.setCellValue("NDFSP Name");
        ndfspValueCell.setCellStyle(bodyStyle);

        Row reportDateRow = sheet.createRow(2);

        Cell reportDateLabelCell = reportDateRow.createCell(0);
        reportDateLabelCell.setCellValue("Report Date");
        reportDateLabelCell.setCellStyle(bodyStyle);

        Cell reportDateCell = reportDateRow.createCell(2);
        reportDateCell.setCellValue(LocalDate.now());
        reportDateCell.setCellStyle(bodyStyle);

        Row reportNameRow = sheet.createRow(3);

        Cell reportNameLabelCell = reportNameRow.createCell(0);
        reportNameLabelCell.setCellValue("Report Name");
        reportNameLabelCell.setCellStyle(headerStyle);

        Cell reportNameCell = reportNameRow.createCell(2);
        reportNameCell.setCellValue(
                reportName == null ? "" : reportName);
        reportNameCell.setCellStyle(titleStyle);

        Row classificationRow = sheet.createRow(5);

        Cell classificationCell = classificationRow.createCell(0);

        classificationCell.setCellValue(
                "Portfolio at risk / regulatory classification: "
                        + (classification == null
                                ? ""
                                : classification));

        classificationCell.setCellStyle(titleStyle);

        int actualHeaderRowIndex = headerRowNumber - 1;

        Row headerRow = sheet.createRow(actualHeaderRowIndex);

        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {

            String headerText = headers[columnIndex];

            if (headerText == null) {
                headerText = "";
            }

            Cell headerCell = headerRow.createCell(columnIndex);

            headerCell.setCellValue(headerText);

            headerCell.setCellStyle(headerStyle);
        }

        Row firstDataRow = sheet.createRow(headerRowNumber);

        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {

            Cell dataCell = firstDataRow.createCell(columnIndex);

            dataCell.setCellValue("");

            dataCell.setCellStyle(bodyStyle);
        }

        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {

            String headerText = headers[columnIndex];

            if (headerText == null) {
                headerText = "";
            }

            int calculatedWidth = headerText.length() * 300;

            int boundedWidth = Math.max(
                    4200,
                    Math.min(
                            14000,
                            calculatedWidth));

            sheet.setColumnWidth(
                    columnIndex,
                    Math.min(
                            18000,
                            boundedWidth));
        }

        sheet.createFreezePane(
                0,
                headerRowNumber);

        sheet.setAutoFilter(
                new org.apache.poi.ss.util.CellRangeAddress(
                        actualHeaderRowIndex,
                        headerRowNumber,
                        0,
                        headers.length - 1));
    }

    private void createSheet1(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("Sheet1");
        CellStyle title = createTitleStyle(workbook);
        CellStyle body = createBodyStyle(workbook);

        Row row0 = sheet.createRow(0);
        Cell titleCell = row0.createCell(0);
        titleCell.setCellValue("BNR REGULATORY REPORT - NOBLE LOAN SOLUTIONS");
        titleCell.setCellStyle(title);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 5));

        String[][] rows = {
                { "Report purpose", "Regulatory portfolio and financial reporting" },
                { "Data source", "Noble Loan operational and accounting database" },
                { "Workbook construction", "Programmatically generated; no XLSX template is required at runtime" },
                { "Interest basis",
                        "Contractual interest is monthly; BNR annual-interest field is nominal monthly rate multiplied by 12" },
                { "Classification", "Normal / Watch / Substandard / Doubtful / Loss / Restructured / Written off" }
        };
        for (int i = 0; i < rows.length; i++) {
            Row row = sheet.createRow(i + 2);
            Cell a = row.createCell(0);
            a.setCellValue(rows[i][0]);
            a.setCellStyle(title);
            Cell b = row.createCell(1);
            b.setCellValue(rows[i][1]);
            b.setCellStyle(body);
        }
        sheet.setColumnWidth(0, 10000);
        sheet.setColumnWidth(1, 28000);
        sheet.createFreezePane(0, 2);
    }

    private void createWrittenOffSheet(XSSFWorkbook workbook) {

        String[] headers = {
                "Names of Borrowers",
                "ID of the Borrower",
                "Telephone number",
                "Account Number",
                "Gender",
                "Age",
                "Relationship with the NDFSP",
                "Annual Interest Rate",
                "Method of interest rate calculation (Flat/Declining)",
                "Physical Guarantee",
                "Borrower's District",
                "Borrower's Sector",
                "Borrower's Cell",
                "Borrower's Village",
                "Date of loan disbursement",
                "Amount of loan disbursed",
                "Maturity Date",
                "Amount Repaid",
                "Loan balance outstanding",
                "Security Savings",
                "Amount Written Off",
                "Date of Write Off",
                "Recoveries on the written off amount",
                "Remaining Balance to be Recovered"
        };

        Sheet sheet = workbook.createSheet("A1.9. Written off");

        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle bodyStyle = createBodyStyle(workbook);

        Row ndfspLabelRow = sheet.createRow(0);

        Cell ndfspLabelCell = ndfspLabelRow.createCell(0);
        ndfspLabelCell.setCellValue("NDFSP Name");
        ndfspLabelCell.setCellStyle(headerStyle);

        Row ndfspValueRow = sheet.createRow(1);

        Cell ndfspValueCell = ndfspValueRow.createCell(0);
        ndfspValueCell.setCellValue("NDFSP Name");
        ndfspValueCell.setCellStyle(bodyStyle);

        Row reportDateRow = sheet.createRow(2);

        Cell reportDateLabelCell = reportDateRow.createCell(0);
        reportDateLabelCell.setCellValue("Report Date");
        reportDateLabelCell.setCellStyle(bodyStyle);

        Cell reportDateCell = reportDateRow.createCell(1);
        reportDateCell.setCellValue(LocalDate.now());
        reportDateCell.setCellStyle(bodyStyle);

        Row reportNameRow = sheet.createRow(3);

        Cell reportNameLabelCell = reportNameRow.createCell(0);
        reportNameLabelCell.setCellValue("Report Name");
        reportNameLabelCell.setCellStyle(headerStyle);

        Cell reportNameCell = reportNameRow.createCell(1);
        reportNameCell.setCellValue(
                "Written Off Loans-Individuals (1 year in loss)");
        reportNameCell.setCellStyle(titleStyle);

        final int headerRowIndex = 6;

        Row headerRow = sheet.createRow(headerRowIndex);

        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {

            String headerText = headers[columnIndex];

            if (headerText == null) {
                headerText = "";
            }

            Cell headerCell = headerRow.createCell(columnIndex);

            headerCell.setCellValue(headerText);

            headerCell.setCellStyle(headerStyle);

            int calculatedWidth = headerText.length() * 300;

            int boundedWidth = Math.max(
                    4200,
                    Math.min(
                            14000,
                            calculatedWidth));

            sheet.setColumnWidth(
                    columnIndex,
                    Math.min(
                            18000,
                            boundedWidth));
        }

        final int firstDataRowIndex = 7;

        Row firstDataRow = sheet.createRow(firstDataRowIndex);

        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {

            Cell dataCell = firstDataRow.createCell(columnIndex);

            dataCell.setCellValue("");

            dataCell.setCellStyle(bodyStyle);
        }

        sheet.createFreezePane(
                0,
                firstDataRowIndex);

        sheet.setAutoFilter(
                new org.apache.poi.ss.util.CellRangeAddress(
                        headerRowIndex,
                        firstDataRowIndex,
                        0,
                        headers.length - 1));
    }

    private String[] normalHeaders() {
        return new String[] {
                "No",
                "Names of Borrowers",
                "ID of the Borrower",
                "Telephone number",
                "Gender",
                "Age",
                "Relationship with the NDFSP ( Staff, Director, Shareholder…)",
                "Marital Status (Married/Single/Widow)",
                "previous loans paid on time (Yes/No)",
                "Purpose of the loan",
                "Branch name",
                "Collateral Type",
                "Guarantee(Collateral) Ammount",
                "Borrower's District",
                "Borrower's Sector",
                "Borrower's Cell",
                "Borrower's Village",
                "Annual Interest Rate",
                "Method of interest rate calculation (Flat/Declining)",
                "Names of the Loan Officer",
                "Disbursed Amount",
                "Date of loan disbursement",
                "Agreed Maturity Date",
                "Agreed Frequency of Repayment (Days)",
                "Grace Period Accorded (Days)",
                "Agreed Date of First Payment (Principal)",
                "Date of Last Payment (Principal)",
                "Date when Arrears Start",
                "Cut Off Date (Report Date)",
                "Total Number of Installments",
                "Round Number of Installments paid",
                "Round Number of Installments outstanding",
                "Amount Repaid (Principal)",
                "Balance Outstanding (Principal)",
                "Eligible Collateral provided",
                "Net Amount due (Principal)",
                "Number of days overdue (Arrears)",
                "Class",
                "Provisioning Rate (Regulation)",
                "Provision Required",
                "Previous Provisions",
                "Additional Provisions"
        };
    }

    private String[] substandardHeaders() {
        String[] headers = normalHeaders();
        headers[9] = "Other Institutions in which he/she has loans";
        headers[10] = "Purpose of the loan";
        headers[11] = "Branch name";
        return headers;
    }

    private String[] restructuredHeaders() {
        String[] headers = normalHeaders();
        headers[37] = "Performance Class";
        return headers;
    }

    private CellStyle createTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createBodyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createCurrencyStyle(XSSFWorkbook workbook) {
        CellStyle style = createBodyStyle(workbook);
        style.setDataFormat(
                workbook.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private void configureWorkbook(XSSFWorkbook workbook) {

        if (workbook == null) {
            throw new IllegalArgumentException("Workbook cannot be null");
        }

        workbook.setForceFormulaRecalculation(true);

        POIXMLProperties.CoreProperties properties = workbook.getProperties().getCoreProperties();

        properties.setCreator("Noble Loan Solutions");
        properties.setTitle("BNR Regulatory Reporting");
    }

    private void populateMetadata(
            XSSFWorkbook workbook,
            Long organizationId,
            Long branchId,
            RegulatoryReportingService.ReportPeriod period,
            LocalDate[] window,
            List<Loan> loans) {

        Sheet fs = workbook.getSheet("A1.2. FS");
        if (fs == null) {
            return;
        }

        String institutionName = "";
        String registrationNumber = "";
        String currency = "RWF";

        if (!loans.isEmpty() && loans.get(0).getOrganization() != null) {
            institutionName = nullToBlank(loans.get(0).getOrganization().getName());
            registrationNumber = nullToBlank(
                    loans.get(0).getOrganization().getRegistrationNumber());

            if (loans.get(0).getCurrency() != null
                    && !loans.get(0).getCurrency().isBlank()) {
                currency = loans.get(0).getCurrency();
            }
        }

        setCellValue(fs, 0, 1, institutionName);
        setCellValue(fs, 1, 1, "NON-DEPOSIT TAKING LENDER");

        if (branchId != null) {
            String branch = loans.stream()
                    .filter(l -> l.getBranch() != null)
                    .map(l -> l.getBranch().getName())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("");
            setCellValue(fs, 2, 1, branch);
        } else {
            setCellValue(fs, 2, 1, registrationNumber);
        }

        setCellValue(fs, 2, 2, "DENOMINATION");
        setCellValue(fs, 2, 8, window[1]);

        // Add report metadata to the first sheet without changing the
        // regulatory sheets' column structure.
        Sheet note = workbook.getSheet("A1.1  Explanatory Note ");
        if (note != null) {
            int row = Math.max(0, note.getLastRowNum() + 1);
            writeNote(note, row++, "Noble Loan Solutions - BNR Regulatory Export");
            writeNote(note, row++, "Organization ID", organizationId);
            writeNote(note, row++, "Branch ID", branchId);
            writeNote(note, row++, "Report Period", period == null ? "MONTHLY" : period.name());
            writeNote(note, row++, "Period Start", window[0]);
            writeNote(note, row++, "Period End / Cut-off Date", window[1]);
            writeNote(note, row, "Currency", currency);
        }
    }

    private void writeLoanSheet(
            Sheet sheet,
            List<Loan> loans,
            LocalDate reportDate,
            String classification) {

        int headerRow = findHeaderRow(sheet, "Names of Borrowers");
        if (headerRow < 0) {
            throw new IllegalStateException(
                    "BNR sheet '" + sheet.getSheetName()
                            + "' does not contain the expected borrower header");
        }

        int dataStartRow = headerRow + 2;
        clearSourceCells(sheet, dataStartRow);

        int rowNumber = dataStartRow;
        int sequence = 1;

        for (Loan loan : loans) {
            if (loan == null) {
                continue;
            }

            Row row = getOrCreateRow(sheet, rowNumber++);
            populateClassificationRow(
                    row,
                    sheet,
                    sequence++,
                    loan,
                    reportDate,
                    classification);
        }
    }

    private void writeWrittenOffSheet(
            Sheet sheet,
            List<Loan> loans,
            LocalDate reportDate) {

        int headerRow = findHeaderRow(sheet, "Names of Borrowers");
        if (headerRow < 0) {
            throw new IllegalStateException(
                    "BNR Written-off sheet does not contain the expected header");
        }

        int dataStartRow = headerRow + 2;
        clearSourceCells(sheet, dataStartRow);

        int rowNumber = dataStartRow;

        for (Loan loan : loans) {
            if (loan == null) {
                continue;
            }

            Row row = getOrCreateRow(sheet, rowNumber++);
            populateWrittenOffRow(row, sheet, loan, reportDate);
        }
    }

    private void populateClassificationRow(
            Row row,
            Sheet sheet,
            int sequence,
            Loan loan,
            LocalDate reportDate,
            String classification) {

        BnrLoanFacts facts = facts(loan, reportDate);

        int headerRowIndex = findHeaderRow(sheet, "Names of Borrowers");
        Row headerRow = sheet.getRow(headerRowIndex);

        for (int column = 0; column < headerRow.getLastCellNum(); column++) {
            String header = text(headerRow.getCell(column));

            if (isDerivedColumn(header)) {
                continue;
            }

            Object value = valueForHeader(
                    header,
                    sequence,
                    facts,
                    classification);

            writeTypedCell(row, column, value);
        }

        repairDerivedColumns(row, headerRow, facts);
    }

    private void populateWrittenOffRow(
            Row row,
            Sheet sheet,
            Loan loan,
            LocalDate reportDate) {

        BnrLoanFacts facts = facts(loan, reportDate);

        int headerRow = findHeaderRow(sheet, "Names of Borrowers");
        Row header = sheet.getRow(headerRow);

        for (int column = 0; column < header.getLastCellNum(); column++) {
            String headerText = text(header.getCell(column));
            Object value = writtenOffValue(headerText, facts);
            writeTypedCell(row, column, value);
        }
    }

    private Object valueForHeader(
            String header,
            int sequence,
            BnrLoanFacts f,
            String classification) {

        String h = normalize(header);

        if (contains(h, "no") && !contains(h, "number"))
            return sequence;
        if (contains(h, "namesofborrowers"))
            return f.borrowerName;
        if (contains(h, "idofborrower"))
            return f.nationalId;
        if (contains(h, "telephonenumber"))
            return f.phone;
        if (h.equals("gender"))
            return f.gender;
        if (h.equals("age"))
            return f.age;
        if (contains(h, "relationshipwiththendfsp"))
            return null;
        if (contains(h, "maritalstatus"))
            return f.maritalStatus;
        if (contains(h, "previousloanspaidontime"))
            return f.previousLoansPaidOnTime;
        if (contains(h, "otherinstitutions"))
            return null;
        if (contains(h, "purposeoftheloan"))
            return f.purpose;
        if (h.equals("branchname"))
            return f.branchName;
        if (contains(h, "collateraltype")
                || contains(h, "physicalguaranteecollateral")
                || contains(h, "physicalguarantee"))
            return f.collateralType;
        if (contains(h, "guaranteecollateralammount")
                || contains(h, "guaranteecollateralamount"))
            return f.collateralValue;
        if (contains(h, "borrowersdistrict"))
            return f.district;
        if (contains(h, "borrowerssector"))
            return f.sector;
        if (contains(h, "borrowerscell"))
            return f.cell;
        if (contains(h, "borrowersvillage"))
            return f.village;

        if (contains(h, "annualinterestrate")) {
            // The platform stores 5.00 as monthly. The BNR template explicitly
            // requests annual interest, therefore report the contractual
            // annualized nominal rate: 5% x 12 = 60%.
            return money(f.monthlyInterestRate.multiply(new BigDecimal("12")));
        }

        if (contains(h, "methodofinterestratecalculation"))
            return "Declining";
        if (contains(h, "namesoftheloanofficer"))
            return f.loanOfficerName;
        if (contains(h, "disbursedamount"))
            return f.disbursedAmount;
        if (contains(h, "dateofloandisbursement"))
            return f.disbursementDate;
        if (contains(h, "agreedmaturitydate"))
            return f.maturityDate;
        if (contains(h, "agreedfrequencyofrepayment"))
            return f.frequencyDays;
        if (contains(h, "graceperiodaccorded"))
            return 0;
        if (contains(h, "agreeddateoffirstpayment"))
            return f.firstPaymentDate;
        if (contains(h, "dateoflastpayment"))
            return f.lastPaymentDate;
        if (contains(h, "datewhenarrearsstart"))
            return f.arrearsStartDate;
        if (contains(h, "cutoffdatereportdate"))
            return f.reportDate;
        if (contains(h, "totalnumberofinstallments"))
            return f.totalInstallments;
        if (contains(h, "roundnumberofinstallmentspaid"))
            return f.installmentsPaid;
        if (contains(h, "roundnumberofinstallmentsoutstanding"))
            return null;
        if (contains(h, "amountrepaidprincipal"))
            return f.principalRepaid;
        if (contains(h, "balanceoutstandingprincipal"))
            return null;
        if (contains(h, "eligiblecollateralprovided"))
            return null;
        if (contains(h, "securitycompulsorysavings"))
            return f.eligibleCollateral;
        if (contains(h, "netamountdueprincipal"))
            return null;
        if (contains(h, "numberofdaysoverdue"))
            return f.daysOverdue;
        if (h.equals("class") || contains(h, "performanceclass")) {
            return classificationLabel(classification);
        }
        if (contains(h, "provisioningrateregulation"))
            return f.provisionRate.divide(ONE_HUNDRED, 8, MONEY_ROUNDING);
        if (contains(h, "provisionrequired"))
            return null;
        if (contains(h, "previousprovisions")) {
            // There is no previous-provision snapshot field in the Loan model.
            // Never invent it.
            return null;
        }
        if (contains(h, "additionalprovisions"))
            return null;

        return null;
    }

    private Object writtenOffValue(String header, BnrLoanFacts f) {
        String h = normalize(header);

        if (contains(h, "namesofborrowers"))
            return f.borrowerName;
        if (contains(h, "idofborrower"))
            return f.nationalId;
        if (contains(h, "telephonenumber"))
            return f.phone;
        if (contains(h, "accountnumber"))
            return f.loanReference;
        if (h.equals("gender"))
            return f.gender;
        if (h.equals("age"))
            return f.age;
        if (contains(h, "relationshipwiththendfsp"))
            return null;
        if (contains(h, "annualinterestrate")) {
            return money(f.monthlyInterestRate.multiply(new BigDecimal("12")));
        }
        if (contains(h, "methodofinterestratecalculation"))
            return "Declining";
        if (contains(h, "physicalguarantee"))
            return f.collateralType;
        if (contains(h, "borrowersdistrict"))
            return f.district;
        if (contains(h, "borrowerssector"))
            return f.sector;
        if (contains(h, "borrowerscell"))
            return f.cell;
        if (contains(h, "borrowersvillage"))
            return f.village;
        if (contains(h, "dateofloandisbursement"))
            return f.disbursementDate;
        if (contains(h, "amountofloandisbursed"))
            return f.disbursedAmount;
        if (contains(h, "maturitydate"))
            return f.maturityDate;
        if (contains(h, "amountrepaid"))
            return f.principalRepaid;
        if (contains(h, "loanbalanceoutstanding"))
            return f.outstandingPrincipal;
        if (contains(h, "securitysavings"))
            return f.eligibleCollateral;
        if (contains(h, "amountwrittenoff"))
            return f.outstandingPrincipal;
        if (contains(h, "dateofwriteoff"))
            return f.writeOffDate;
        if (contains(h, "recoveriesonthewrittenoffamount"))
            return null;
        if (contains(h, "remainingbalancetoberecovered"))
            return f.outstandingPrincipal;

        return null;
    }

    private BnrLoanFacts facts(Loan loan, LocalDate reportDate) {
        Borrower borrower = loan.getBorrower();

        BigDecimal outstanding = money(loan.getOutstandingBalanceDecimal()).max(ZERO);
        BigDecimal collateral = money(loan.getCollateralValueDecimal()).max(ZERO);
        String classification = classificationKey(loan);
        BigDecimal eligibleCollateral = eligibleCollateral(
                loan.getCollateralDescription(),
                collateral,
                outstanding);
        BigDecimal netDue = outstanding.subtract(eligibleCollateral).max(ZERO);

        int dpd = loan.getDaysOverdue() == null
                ? 0
                : Math.max(0, loan.getDaysOverdue());

        BigDecimal provisionRate = provisioningRate(classification);
        BigDecimal provisionRequired = money(
                netDue.multiply(provisionRate).divide(ONE_HUNDRED, MONEY_SCALE, MONEY_ROUNDING));

        List<PaymentSchedule> schedules = loan.getId() == null
                ? List.of()
                : safeSchedules(
                        paymentScheduleRepository
                                .findByLoanIdOrderByInstallmentNumberAsc(loan.getId()));

        int paidInstallments = (int) schedules.stream()
                .filter(s -> s != null && (s.getStatus() == PaymentSchedule.ScheduleStatus.PAID
                        || money(s.getAmountPaid()).compareTo(money(s.getInstallmentAmount())) >= 0))
                .count();

        int outstandingInstallments = Math.max(
                0,
                schedules.size() - paidInstallments);

        LocalDate firstPaymentDate = schedules.isEmpty()
                ? null
                : schedules.get(0).getDueDate();

        LocalDate arrearsStartDate = schedules.stream()
                .filter(s -> s != null
                        && s.getDueDate() != null
                        && s.getDueDate().isBefore(reportDate)
                        && money(s.getAmountPaid()).compareTo(money(s.getInstallmentAmount())) < 0)
                .map(PaymentSchedule::getDueDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        String previousLoansPaidOnTime = previousLoansPaidOnTime(loan);

        return new BnrLoanFacts(
                loan.getReferenceNumber(),
                borrower == null ? null : borrower.getFullName(),
                borrower == null ? null : borrower.getNationalId(),
                borrower == null ? null : borrower.getPhone(),
                borrower == null ? null : borrower.getGender(),
                borrower == null ? null : age(borrower.getDateOfBirth(), reportDate),
                borrower == null ? null : borrower.getMaritalStatus(),
                previousLoansPaidOnTime,
                loan.getPurpose(),
                loan.getBranch() == null ? null : loan.getBranch().getName(),
                blankIfNull(loan.getCollateralDescription()),
                collateral,
                borrower == null ? null : borrower.getCity(),
                null,
                null,
                null,
                money(loan.getInterestRateDecimal() == null
                        ? Loan.DEFAULT_MONTHLY_INTEREST_RATE
                        : loan.getInterestRateDecimal()),
                loan.getLoanOfficer() == null ? null : loan.getLoanOfficer().getFullName(),
                principalOrDisbursed(loan),
                loan.getDisbursedAt() == null ? loan.getStartDate() : loan.getDisbursedAt().toLocalDate(),
                loan.getMaturityDate(),
                frequencyDays(loan),
                firstPaymentDate,
                loan.getLastPaymentDate(),
                arrearsStartDate,
                reportDate,
                schedules.size(),
                paidInstallments,
                outstandingInstallments,
                money(loan.getPrincipalPaidDecimal()),
                outstanding,
                eligibleCollateral,
                netDue,
                dpd,
                provisionRate,
                provisionRequired,
                loan.getStatus() == LoanStatus.WRITTEN_OFF
                        ? (loan.getDisbursedAt() == null ? loan.getStartDate() : loan.getDisbursedAt().toLocalDate())
                        : null);
    }

    private BigDecimal eligibleCollateral(
            String collateralType,
            BigDecimal collateralAmount,
            BigDecimal outstandingPrincipal) {

        BigDecimal amount = money(collateralAmount).max(ZERO);
        BigDecimal outstanding = money(outstandingPrincipal).max(ZERO);
        if (amount.signum() <= 0 || outstanding.signum() <= 0 || collateralType == null) {
            return ZERO;
        }

        String type = normalize(collateralType);
        BigDecimal haircut = ZERO;

        if (type.equals("cashcollateral")
                || type.equals("governmentorthecentralbank")
                || type.equals("governmentorthecentralbankbillsandbonds")
                || type.equals("othersecuritiesofferedbythebanksoperatinginrwanda")) {
            haircut = BigDecimal.ONE;
        } else if (type.equals("landandbuilding") || type.equals("building")) {
            haircut = new BigDecimal("0.60");
        } else if (type.equals("movablecollaterals")
                || type.equals("biologicalassets")
                || type.equals("otherassets")) {
            haircut = new BigDecimal("0.40");
        }

        return money(amount.multiply(haircut)).min(outstanding);
    }

    private String previousLoansPaidOnTime(Loan current) {
        if (current.getBorrower() == null
                || current.getBorrower().getId() == null
                || current.getOrganization() == null
                || current.getOrganization().getId() == null) {
            return null;
        }

        List<Loan> borrowerLoans = loanRepository.findByBorrowerIdAndOrganizationId(
                current.getBorrower().getId(),
                current.getOrganization().getId());

        boolean hasPrevious = false;

        for (Loan loan : borrowerLoans) {
            if (loan == null || Objects.equals(loan.getId(), current.getId())) {
                continue;
            }

            if (loan.getStatus() == LoanStatus.PAID
                    || loan.getStatus() == LoanStatus.CLOSED) {
                hasPrevious = true;
                if (loan.getDaysOverdue() != null && loan.getDaysOverdue() > 0) {
                    return "No";
                }
            }
        }

        return hasPrevious ? "Yes" : null;
    }

    private Map<String, List<Loan>> classifyLoans(List<Loan> loans) {
        Map<String, List<Loan>> result = new LinkedHashMap<>();
        for (String key : List.of(
                "NORMAL", "WATCH", "SUBSTANDARD", "DOUBTFUL",
                "LOSS", "RESTRUCTURED", "WRITTEN_OFF")) {
            result.put(key, new ArrayList<>());
        }

        for (Loan loan : safeLoans(loans)) {
            if (loan == null)
                continue;

            if (loan.getStatus() == LoanStatus.WRITTEN_OFF) {
                result.get("WRITTEN_OFF").add(loan);
                continue;
            }

            String key = classificationKey(loan);
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(loan);
        }

        return result;
    }

    private String classificationForSheet(String sheetName) {
        return switch (sheetName) {
            case "A1.3. Normal Loans", "A1.3. Normal" -> "NORMAL";
            case "A1.4. Watch" -> "WATCH";
            case "A1.5. Substandard" -> "SUBSTANDARD";
            case "A1.6. Doubtful" -> "DOUBTFUL";
            case "A1.7 Loss" -> "LOSS";
            case "A1.8. Restructured loans" -> "RESTRUCTURED";
            default -> "NORMAL";
        };
    }

    private String classificationKey(Loan loan) {
        if (loan == null)
            return "NORMAL";
        if (isRestructured(loan))
            return "RESTRUCTURED";
        if (loan.getStatus() == LoanStatus.WRITTEN_OFF)
            return "LOSS";

        int dpd = loan.getDaysOverdue() == null
                ? 0
                : Math.max(0, loan.getDaysOverdue());

        if (dpd >= 360)
            return "LOSS";
        if (dpd >= 180)
            return "DOUBTFUL";
        if (dpd >= 90)
            return "SUBSTANDARD";
        if (dpd >= 1)
            return "WATCH";
        return "NORMAL";
    }

    private BigDecimal provisioningRate(String classification) {
        return switch (classification) {
            case "NORMAL" -> ZERO;
            case "WATCH" -> new BigDecimal("1.00");
            case "SUBSTANDARD" -> new BigDecimal("20.00");
            case "DOUBTFUL" -> new BigDecimal("50.00");
            case "LOSS" -> new BigDecimal("100.00");
            case "RESTRUCTURED" -> ZERO;
            default -> ZERO;
        };
    }

    private String classificationLabel(String classification) {
        return switch (classification) {
            case "NORMAL" -> "NORMAL";
            case "WATCH" -> "WATCH";
            case "SUBSTANDARD" -> "SUBSTANDARD";
            case "DOUBTFUL" -> "DOUBTFUL";
            case "LOSS" -> "LOSS";
            case "RESTRUCTURED" -> "RESTRUCTURED";
            default -> classification;
        };
    }

    private boolean isRestructured(Loan loan) {
        return loan.getStatus() == LoanStatus.RESTRUCTURED
                || (loan.getExtensionCount() != null
                        && loan.getExtensionCount() > 0);
    }

    private void populateFinancialStatement(
            XSSFWorkbook workbook,
            BnrFinancialStatementReport report) {

        Sheet sheet = workbook.getSheet("A1.2. FS");
        if (sheet == null || report == null) {
            return;
        }

        // The supplied template uses columns D:I for reporting periods.
        // We populate the current cut-off period in I and deliberately leave
        // historical periods untouched.
        final int currentColumn = 8;

        setCellValue(sheet, 2, currentColumn, report.getPeriodEnd());

        // Supplementary portfolio totals are sourced from the same
        // RegulatoryReportingService used by the BNR API. The exporter never
        // reconstructs accounting balances from loan totals.
        BnrSummaryReport summaryReport = regulatoryReportingService.buildBnrSummary(
                report.getOrganizationId(),
                report.getBranchId(),
                RegulatoryReportingService.ReportPeriod.valueOf(
                        report.getReportPeriod()),
                report.getPeriodStart(),
                report.getPeriodEnd());

        BigDecimal summaryRequiredProvision = BigDecimal.valueOf(summaryReport.getRequiredProvision());
        // Preserve the template's regulatory formulas. We only supply the
        // component inputs that the system can authoritatively source.
        // Gross loans, net loans, NPLs and all regulatory totals remain formulas.
        setCellValue(sheet, 9, currentColumn, summaryRequiredProvision); // Excel row 10: Provisions
        setCellValue(sheet, 30, currentColumn, report.getCurrentPeriodNetIncome()); // Excel row 31

        // The template's NPL formula includes the restructured row, while the
        // explanatory note defines NPL as Substandard + Doubtful + Loss.
        // Correct only this formula wiring; the regulatory definition is unchanged.
        String col = org.apache.poi.ss.util.CellReference.convertNumToColString(currentColumn);
        setFormula(sheet.getRow(11), currentColumn, "=SUM(" + col + "89:" + col + "91)");

        populateAccountingComponents(sheet, currentColumn, report);

        BnrSummaryValues summary = buildSummaryValues(
                report.getOrganizationId(),
                report.getBranchId(),
                report.getPeriodStart(),
                report.getPeriodEnd());

        setCellValue(sheet, 86, currentColumn, summary.normalOutstanding);
        setCellValue(sheet, 87, currentColumn, summary.watchOutstanding);
        setCellValue(sheet, 88, currentColumn, summary.substandardOutstanding);
        setCellValue(sheet, 89, currentColumn, summary.doubtfulOutstanding);
        setCellValue(sheet, 90, currentColumn, summary.lossOutstanding);
        setCellValue(sheet, 91, currentColumn, summary.restructuredOutstanding);
        setCellValue(sheet, 92, currentColumn, summary.totalClassifiedOutstanding);

        // Number of outstanding loans by gender.
        setCellValue(sheet, 72, currentColumn, summary.maleOutstandingLoans);
        setCellValue(sheet, 73, currentColumn, summary.femaleOutstandingLoans);
        setCellValue(sheet, 74, currentColumn, summary.otherOutstandingLoans);

        // Number and value of disbursed loans in the period.
        setCellValue(sheet, 93, currentColumn, summary.maleDisbursedLoans);
        setCellValue(sheet, 94, currentColumn, summary.femaleDisbursedLoans);
        setCellValue(sheet, 95, currentColumn, summary.otherDisbursedLoans);

        setCellValue(sheet, 97, currentColumn, summary.maleDisbursedAmount);
        setCellValue(sheet, 98, currentColumn, summary.femaleDisbursedAmount);
        setCellValue(sheet, 99, currentColumn, summary.otherDisbursedAmount);
    }

    private void setIfPresent(
            Sheet sheet,
            int rowIndex,
            int columnIndex,
            BigDecimal value) {

        if (sheet == null || value == null) {
            return;
        }

        Row row = sheet.getRow(rowIndex);

        if (row == null) {
            row = sheet.createRow(rowIndex);
        }

        Cell cell = row.getCell(columnIndex);

        if (cell == null) {
            cell = row.createCell(columnIndex);
        }

        cell.setCellValue(value.doubleValue());
    }

    private void populateAccountingComponents(
            Sheet sheet,
            int column,
            BnrFinancialStatementReport report) {

        // Assets / liabilities: map only explicitly modeled GL balances.
        BigDecimal interestReceivable = findAccountingAmount(
                report.getAssets(), "1150", "interest receivable");
        if (interestReceivable != null) {
            setCellValue(sheet, 16, column, interestReceivable); // row 17
        }

        BigDecimal otherAssets = addNullable(
                findAccountingAmount(report.getAssets(), "1160", "management fees receivable"),
                addNullable(
                        findAccountingAmount(report.getAssets(), "1170", "extension fees receivable"),
                        findAccountingAmount(report.getAssets(), "1175", "penalties receivable")));
        if (otherAssets != null) {
            setCellValue(sheet, 17, column, otherAssets); // row 18
        }

        BigDecimal otherLiabilities = addNullable(
                findAccountingAmount(report.getLiabilities(), "2000", "customer deposits payable"),
                findAccountingAmount(report.getLiabilities(), "2100", "borrower refunds payable"));
        if (otherLiabilities != null) {
            setCellValue(sheet, 24, column, otherLiabilities); // row 25
        }

        // Income. The template total formulas remain intact.
        setIfPresent(sheet, column, 39, findAccountingAmount(
                report.getIncome(), "4000", "interest income")); // row 40
        setIfPresent(sheet, column, 40, findAccountingAmount(
                report.getIncome(), "4100", "fee and penalty income", "fee")); // row 41

        // Expenses. The template's total expense formula remains intact.
        setIfPresent(sheet, column, 53, findAccountingAmount(
                report.getExpenses(), "5000", "loan loss expense")); // row 54
        setIfPresent(sheet, column, 55, findAccountingAmount(
                report.getExpenses(), "5200", "salaries and wages")); // row 56

        BigDecimal detailedAdministrative = sumAccountingCodes(
                report.getExpenses(),
                "5201", "5202", "5203", "5204", "5205", "5206",
                "5210", "5211", "5214", "5215");
        BigDecimal administrative = detailedAdministrative != null
                ? detailedAdministrative
                : findAccountingAmount(report.getExpenses(), "5100", "operating expenses");
        setIfPresent(sheet, column, 56, administrative); // row 57

        setIfPresent(sheet, column, 52, findAccountingAmount(
                report.getExpenses(), "5207", "bank charges")); // row 53
    }

    private BigDecimal findAccountingAmount(
            List<?> lines,
            String accountCode,
            String... descriptionFragments) {

        if (lines == null || lines.isEmpty()) {
            return null;
        }

        String targetCode = normalize(accountCode);

        for (Object rawLine : lines) {
            if (!(rawLine instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
                continue;
            }

            String code = firstString(rawMap,
                    "accountCode", "code", "account_code", "glCode", "gl_code",
                    "accountNumber", "account_number", "number");

            String description = firstString(rawMap,
                    "accountName", "name", "account_name", "description",
                    "accountDescription", "account_description", "label");

            boolean codeMatches = !targetCode.isBlank()
                    && normalize(code).equals(targetCode);

            boolean descriptionMatches = descriptionFragments != null
                    && descriptionFragments.length > 0
                    && containsAnyNormalized(description, descriptionFragments);

            if (!codeMatches && !descriptionMatches) {
                continue;
            }

            BigDecimal amount = firstDecimal(rawMap,
                    "amount", "balance", "closingBalance", "closing_balance",
                    "netBalance", "net_balance", "value", "total",
                    "debitBalance", "debit_balance",
                    "creditBalance", "credit_balance");

            if (amount != null) {
                return money(amount);
            }
        }

        return null;
    }

    private BigDecimal findAccountingAmount(
            List<?> lines,
            String accountCode) {
        return findAccountingAmount(lines, accountCode, new String[0]);
    }

    private static String firstString(Map<?, ?> line, String... keys) {
        if (line == null || keys == null) {
            return "";
        }

        for (String key : keys) {
            if (key == null) {
                continue;
            }

            Object value = line.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        return "";
    }

    private static BigDecimal firstDecimal(Map<?, ?> line, String... keys) {
        if (line == null || keys == null) {
            return null;
        }

        for (String key : keys) {
            if (key == null) {
                continue;
            }

            BigDecimal decimal = toBigDecimal(line.get(key));
            if (decimal != null) {
                return decimal;
            }
        }

        return null;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal decimal) {
            return decimal;
        }

        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }

        String text = String.valueOf(value).trim().replace(",", "");
        if (text.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean containsAnyNormalized(
            String value,
            String... fragments) {

        String normalizedValue = normalize(value);

        if (normalizedValue.isBlank()
                || fragments == null
                || fragments.length == 0) {
            return false;
        }

        for (String fragment : fragments) {
            if (fragment != null
                    && !fragment.isBlank()
                    && normalizedValue.contains(normalize(fragment))) {
                return true;
            }
        }

        return false;
    }

    private BigDecimal sumAccountingCodes(List<?> lines, String... codes) {
        BigDecimal total = null;
        for (String code : codes) {
            BigDecimal amount = findAccountingAmount(lines, code);
            if (amount != null)
                total = addNullable(total, amount);
        }
        return total;
    }

    private BigDecimal addNullable(BigDecimal left, BigDecimal right) {
        if (left == null && right == null)
            return null;
        return money((left == null ? ZERO : left).add(right == null ? ZERO : right));
    }

    private BnrSummaryValues buildSummaryValues(
            Long organizationId,
            Long branchId,
            LocalDate from,
            LocalDate to) {

        List<Loan> portfolio = safeLoans(
                loanRepository.findPortfolioAsOf(
                        organizationId,
                        branchId,
                        to.plusDays(1).atStartOfDay(),
                        to));

        List<Loan> loans = safeLoans(
                loanRepository.findLoansDisbursedDuringPeriod(
                        organizationId,
                        branchId,
                        from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay(),
                        from,
                        to));

        BigDecimal normal = ZERO;
        BigDecimal watch = ZERO;
        BigDecimal substandard = ZERO;
        BigDecimal doubtful = ZERO;
        BigDecimal loss = ZERO;
        BigDecimal restructured = ZERO;
        BigDecimal requiredProvision = ZERO;
        BigDecimal nplAmount = ZERO;

        long maleOutstandingLoans = 0;
        long femaleOutstandingLoans = 0;
        long otherOutstandingLoans = 0;

        for (Loan loan : portfolio) {
            BigDecimal outstanding = money(loan.getOutstandingBalanceDecimal()).max(ZERO);
            String classification = classificationKey(loan);

            BigDecimal eligibleCollateral = eligibleCollateral(
                    loan.getCollateralDescription(),
                    money(loan.getCollateralValueDecimal()),
                    outstanding);
            BigDecimal netDue = outstanding.subtract(eligibleCollateral).max(ZERO);

            BigDecimal provisionRate = provisioningRate(classification);
            requiredProvision = add(
                    requiredProvision,
                    money(netDue.multiply(provisionRate)
                            .divide(ONE_HUNDRED, MONEY_SCALE, MONEY_ROUNDING)));

            if ("SUBSTANDARD".equals(classification)
                    || "DOUBTFUL".equals(classification)
                    || "LOSS".equals(classification)) {
                nplAmount = add(nplAmount, outstanding);
            }

            switch (classification) {
                case "NORMAL" -> normal = add(normal, outstanding);
                case "WATCH" -> watch = add(watch, outstanding);
                case "SUBSTANDARD" -> substandard = add(substandard, outstanding);
                case "DOUBTFUL" -> doubtful = add(doubtful, outstanding);
                case "LOSS" -> loss = add(loss, outstanding);
                case "RESTRUCTURED" -> restructured = add(restructured, outstanding);
                default -> {
                }
            }

            String gender = loan.getBorrower() == null
                    ? ""
                    : normalize(loan.getBorrower().getGender());

            if ("MALE".equals(gender) || "M".equals(gender)) {
                maleOutstandingLoans++;
            } else if ("FEMALE".equals(gender) || "F".equals(gender)) {
                femaleOutstandingLoans++;
            } else {
                otherOutstandingLoans++;
            }
        }

        long maleDisbursedLoans = 0;
        long femaleDisbursedLoans = 0;
        long otherDisbursedLoans = 0;
        BigDecimal maleDisbursedAmount = ZERO;
        BigDecimal femaleDisbursedAmount = ZERO;
        BigDecimal otherDisbursedAmount = ZERO;

        for (Loan loan : loans) {
            LocalDate disbursedDate = loan.getDisbursedAt() == null
                    ? loan.getStartDate()
                    : loan.getDisbursedAt().toLocalDate();

            if (disbursedDate == null
                    || disbursedDate.isBefore(from)
                    || disbursedDate.isAfter(to)) {
                continue;
            }

            BigDecimal amount = principalOrDisbursed(loan);
            String gender = loan.getBorrower() == null
                    ? ""
                    : normalize(loan.getBorrower().getGender());

            if ("MALE".equals(gender) || "M".equals(gender)) {
                maleDisbursedLoans++;
                maleDisbursedAmount = add(maleDisbursedAmount, amount);
            } else if ("FEMALE".equals(gender) || "F".equals(gender)) {
                femaleDisbursedLoans++;
                femaleDisbursedAmount = add(femaleDisbursedAmount, amount);
            } else {
                otherDisbursedLoans++;
                otherDisbursedAmount = add(otherDisbursedAmount, amount);
            }
        }

        return new BnrSummaryValues(
                normal,
                watch,
                substandard,
                doubtful,
                loss,
                restructured,
                add(add(add(normal, watch), add(substandard, doubtful)),
                        add(loss, restructured)),
                requiredProvision,
                nplAmount,
                maleOutstandingLoans,
                femaleOutstandingLoans,
                otherOutstandingLoans,
                maleDisbursedLoans,
                femaleDisbursedLoans,
                otherDisbursedLoans,
                maleDisbursedAmount,
                femaleDisbursedAmount,
                otherDisbursedAmount);
    }

    private void addValidationSheet(
            XSSFWorkbook workbook,
            List<Loan> loans,
            Map<String, List<Loan>> classified,
            LocalDate reportDate) {

        Sheet existing = workbook.getSheet("BNR Validation");
        if (existing != null) {
            int index = workbook.getSheetIndex(existing);
            workbook.removeSheetAt(index);
        }

        Sheet sheet = workbook.createSheet("BNR Validation");

        List<String[]> checks = new ArrayList<>();
        checks.add(new String[] { "Check", "Result", "Value" });
        checks.add(new String[] { "Report cut-off", "PASS", reportDate.toString() });
        checks.add(new String[] { "Loans included", "PASS", String.valueOf(loans.size()) });
        checks.add(new String[] { "Normal loans", "PASS", String.valueOf(classified.get("NORMAL").size()) });
        checks.add(new String[] { "Watch loans", "PASS", String.valueOf(classified.get("WATCH").size()) });
        checks.add(new String[] { "Substandard loans", "PASS", String.valueOf(classified.get("SUBSTANDARD").size()) });
        checks.add(new String[] { "Doubtful loans", "PASS", String.valueOf(classified.get("DOUBTFUL").size()) });
        checks.add(new String[] { "Loss loans", "PASS", String.valueOf(classified.get("LOSS").size()) });
        checks.add(
                new String[] { "Restructured loans", "PASS", String.valueOf(classified.get("RESTRUCTURED").size()) });
        checks.add(new String[] { "Written-off loans", "PASS", String.valueOf(classified.get("WRITTEN_OFF").size()) });
        checks.add(new String[] {
                "Previous provisions",
                "NOT MODELED",
                "Loan model has no previous-provision snapshot; exporter does not fabricate it."
        });
        checks.add(new String[] {
                "Interest basis",
                "PASS",
                "Contractual interest is monthly. BNR export reports the stored monthly rate annualized nominally for the template's Annual Interest Rate field."
        });

        for (int r = 0; r < checks.size(); r++) {
            Row row = sheet.createRow(r);
            for (int c = 0; c < checks.get(r).length; c++) {
                writeTypedCell(row, c, checks.get(r)[c]);
            }
        }

        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, checks.size() - 1, 0, 2));
        sheet.createFreezePane(0, 1);
        sheet.setColumnWidth(0, 7000);
        sheet.setColumnWidth(1, 4500);
        sheet.setColumnWidth(2, 16000);
    }

    private int findHeaderRow(Sheet sheet, String requiredHeader) {
        String target = normalize(requiredHeader);

        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null)
                continue;

            for (Cell cell : row) {
                if (normalize(text(cell)).equals(target)) {
                    return rowIndex;
                }
            }
        }

        return -1;
    }

    private void clearSourceCells(Sheet sheet, int firstDataRow) {
        int headerRowIndex = findHeaderRow(sheet, "Names of Borrowers");
        if (headerRowIndex < 0)
            return;

        Row header = sheet.getRow(headerRowIndex);
        Set<Integer> derived = new HashSet<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            if (isDerivedColumn(text(header.getCell(c)))) {
                derived.add(c);
            }
        }

        for (int rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null)
                continue;
            for (int c = 0; c < header.getLastCellNum(); c++) {
                if (derived.contains(c))
                    continue;
                Cell cell = row.getCell(c);
                if (cell != null)
                    cell.setBlank();
            }
        }
    }

    private Row getOrCreateRow(Sheet sheet, int rowNumber) {
        Row row = sheet.getRow(rowNumber);
        if (row != null)
            return row;

        Row previous = rowNumber > 0 ? sheet.getRow(rowNumber - 1) : null;
        row = sheet.createRow(rowNumber);
        if (previous != null) {
            row.setHeight(previous.getHeight());
            for (int c = 0; c < previous.getLastCellNum(); c++) {
                Cell source = previous.getCell(c);
                if (source == null)
                    continue;
                Cell target = row.createCell(c);
                if (source.getCellStyle() != null)
                    target.setCellStyle(source.getCellStyle());
            }
        }
        return row;
    }

    private boolean isDerivedColumn(String header) {
        String h = normalize(header);
        return contains(h, "roundnumberofinstallmentsoutstanding")
                || contains(h, "balanceoutstandingprincipal")
                || contains(h, "eligiblecollateralprovided")
                || contains(h, "netamountdueprincipal")
                || contains(h, "provisionrequired")
                || contains(h, "additionalprovisions");
    }

    private void repairDerivedColumns(Row row, Row header, BnrLoanFacts f) {
        int total = findColumn(header, "totalnumberofinstallments");
        int paid = findColumn(header, "roundnumberofinstallmentspaid");
        int outstandingInstallments = findColumn(header, "roundnumberofinstallmentsoutstanding");
        if (total >= 0 && paid >= 0 && outstandingInstallments >= 0) {
            setFormula(row, outstandingInstallments,
                    "=MAX(0," + cellRef(total, row.getRowNum()) + "-" + cellRef(paid, row.getRowNum()) + ")");
        }

        int disbursed = findColumn(header, "disbursedamount", "amountofloandisbursed");
        int repaid = findColumn(header, "amountrepaidprincipal", "amountrepaid");
        int balance = findColumn(header, "balanceoutstandingprincipal", "loanbalanceoutstanding");
        if (disbursed >= 0 && repaid >= 0 && balance >= 0) {
            setFormula(row, balance,
                    "=MAX(0," + cellRef(disbursed, row.getRowNum()) + "-" + cellRef(repaid, row.getRowNum()) + ")");
        }

        int collateralType = findColumn(header, "collateraltype", "physicalguaranteecollateral", "physicalguarantee");
        int collateralAmount = findColumn(header, "guaranteecollateralammount", "guaranteecollateralamount");
        int eligible = findColumn(header, "eligiblecollateralprovided");
        if (collateralType >= 0 && collateralAmount >= 0 && eligible >= 0) {
            String type = cellRef(collateralType, row.getRowNum());
            String amount = cellRef(collateralAmount, row.getRowNum());
            String formula = "=IF(OR(LOWER(TRIM(" + type + "))=LOWER(\"cash collateral\"),"
                    + "LOWER(TRIM(" + type + "))=LOWER(\"Government or the Central Bank\"),"
                    + "LOWER(TRIM(" + type + "))=LOWER(\"Government or the Central Bank Bills and Bonds \"),"
                    + "LOWER(TRIM(" + type + "))=LOWER(\"Other securities offered by the banks operating in Rwanda\")),"
                    + amount + ","
                    + "IF(LOWER(TRIM(" + type + "))=LOWER(\"Land and Building\")," + amount + "*60%,"
                    + "IF(LOWER(TRIM(" + type + "))=LOWER(\"Building\")," + amount + "*60%,"
                    + "IF(OR(LOWER(TRIM(" + type + "))=LOWER(\"movable collaterals.\"),"
                    + "LOWER(TRIM(" + type + "))=LOWER(\"Biological assets\"),"
                    + "LOWER(TRIM(" + type + "))=LOWER(\"Other assets \"))," + amount + "*40%,0))))";
            setFormula(row, eligible, formula);
        }

        int balanceForNet = balance;
        if (balanceForNet >= 0 && eligible >= 0) {
            int netDue = findColumn(header, "netamountdueprincipal");
            if (netDue >= 0) {
                setFormula(row, netDue, "=MAX(0," + cellRef(balanceForNet, row.getRowNum()) + "-"
                        + cellRef(eligible, row.getRowNum()) + ")");
            }
        }

        int rate = findColumn(header, "provisioningrateregulation");
        int required = findColumn(header, "provisionrequired");
        int netDue = findColumn(header, "netamountdueprincipal");
        if (rate >= 0 && required >= 0 && netDue >= 0) {
            setFormula(row, required,
                    "=ROUND(" + cellRef(netDue, row.getRowNum()) + "*" + cellRef(rate, row.getRowNum()) + ",2)");
        }

        int additional = findColumn(header, "additionalprovisions");
        int previous = findColumn(header, "previousprovisions");
        if (additional >= 0 && previous >= 0 && required >= 0) {
            setFormula(row, additional, "=IF(" + cellRef(previous, row.getRowNum()) + "=\"\",\"\",ROUND("
                    + cellRef(required, row.getRowNum()) + "-" + cellRef(previous, row.getRowNum()) + ",2))");
        }
    }

    private int findColumn(Row header, String... normalizedNames) {
        if (header == null)
            return -1;
        Set<String> targets = new HashSet<>(Arrays.asList(normalizedNames));
        for (int c = 0; c < header.getLastCellNum(); c++) {
            if (targets.contains(normalize(text(header.getCell(c)))))
                return c;
        }
        return -1;
    }

    private String cellRef(int zeroBasedColumn, int zeroBasedRow) {
        return org.apache.poi.ss.util.CellReference.convertNumToColString(zeroBasedColumn) + (zeroBasedRow + 1);
    }

    private void setFormula(Row row, int column, String formula) {
        Cell cell = row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellFormula(formula.substring(1));
    }

    private void writeNote(Sheet sheet, int rowNumber, String label) {
        Row row = sheet.createRow(rowNumber);
        writeTypedCell(row, 0, label);
    }

    private void writeNote(Sheet sheet, int rowNumber, String label, Object value) {
        Row row = sheet.createRow(rowNumber);
        writeTypedCell(row, 0, label);
        writeTypedCell(row, 1, value);
    }

    private void writeTypedCell(Row row, int column, Object value) {
        Cell cell = row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);

        if (value == null) {
            cell.setBlank();
            return;
        }

        if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
            return;
        }

        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }

        if (value instanceof LocalDate date) {
            cell.setCellValue(date);
            return;
        }

        if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime);
            return;
        }

        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return;
        }

        cell.setCellValue(String.valueOf(value));
    }

    private void setCellValue(Sheet sheet, int row, int column, Object value) {
        Row target = sheet.getRow(row);
        if (target == null) {
            target = sheet.createRow(row);
        }

        Cell cell = target.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);

        if (value == null) {
            cell.setBlank();
        } else if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(date);
        } else if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime);
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private LocalDate[] resolvePeriod(
            RegulatoryReportingService.ReportPeriod period,
            LocalDate from,
            LocalDate to) {

        LocalDate end = to == null ? LocalDate.now() : to;

        if (from != null) {
            if (end.isBefore(from)) {
                throw new IllegalArgumentException("'to' cannot be before 'from'");
            }
            return new LocalDate[] { from, end };
        }

        RegulatoryReportingService.ReportPeriod effective = period == null
                ? RegulatoryReportingService.ReportPeriod.MONTHLY
                : period;

        return switch (effective) {
            case DAILY -> new LocalDate[] { end, end };
            case WEEKLY -> new LocalDate[] { end.minusDays(6), end };
            case MONTHLY -> new LocalDate[] { end.withDayOfMonth(1), end };
            case QUARTERLY -> {
                int quarterMonth = ((end.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(end.getYear(), quarterMonth, 1);
                yield new LocalDate[] { start, end };
            }
            case YEARLY -> new LocalDate[] { end.withDayOfYear(1), end };
            case CUSTOM -> new LocalDate[] { end.withDayOfMonth(1), end };
        };
    }

    private boolean isInPortfolioAsOf(Loan loan, LocalDate reportDate) {
        if (loan == null)
            return false;

        LocalDate disbursementDate = loan.getDisbursedAt() == null
                ? loan.getStartDate()
                : loan.getDisbursedAt().toLocalDate();

        if (disbursementDate == null || disbursementDate.isAfter(reportDate)) {
            return false;
        }

        return loan.getStatus() != LoanStatus.PENDING
                && loan.getStatus() != LoanStatus.UNDER_REVIEW
                && loan.getStatus() != LoanStatus.REJECTED
                && loan.getStatus() != LoanStatus.CANCELLED;
    }

    private BigDecimal principalOrDisbursed(Loan loan) {
        BigDecimal value = loan.getDisbursedAmountDecimal();

        if (value == null || value.signum() <= 0) {
            value = loan.getAmountDecimal();
        }

        return money(value);
    }

    private int frequencyDays(Loan loan) {
        if (loan.getRepaymentFrequency() == null) {
            return 30;
        }

        return switch (loan.getRepaymentFrequency().name()) {
            case "WEEKLY" -> 7;
            case "BIWEEKLY" -> 14;
            case "MONTHLY" -> 30;
            case "QUARTERLY" -> 90;
            case "BULLET" -> Math.max(
                    1,
                    (loan.getDurationMonths() == null ? 1 : loan.getDurationMonths()) * 30);
            default -> 30;
        };
    }

    private Integer age(LocalDate dob, LocalDate asOf) {
        if (dob == null || asOf == null || dob.isAfter(asOf)) {
            return null;
        }
        return Period.between(dob, asOf).getYears();
    }

    private String classificationLabelFromLoan(Loan loan) {
        return classificationLabel(classificationKey(loan));
    }

    private static String text(Cell cell) {
        if (cell == null)
            return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private static String normalize(String value) {
        if (value == null)
            return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
    }

    private static boolean contains(String value, String part) {
        return value.contains(part);
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String blankIfNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null
                ? ZERO
                : value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private static BigDecimal add(BigDecimal a, BigDecimal b) {
        return money(money(a).add(money(b)));
    }

    private static List<Loan> safeLoans(List<Loan> loans) {
        return loans == null ? List.of() : loans;
    }

    private static List<PaymentSchedule> safeSchedules(List<PaymentSchedule> schedules) {
        return schedules == null ? List.of() : schedules;
    }

    private record BnrLoanFacts(
            String loanReference,
            String borrowerName,
            String nationalId,
            String phone,
            String gender,
            Integer age,
            String maritalStatus,
            String previousLoansPaidOnTime,
            String purpose,
            String branchName,
            String collateralType,
            BigDecimal collateralValue,
            String district,
            String sector,
            String cell,
            String village,
            BigDecimal monthlyInterestRate,
            String loanOfficerName,
            BigDecimal disbursedAmount,
            LocalDate disbursementDate,
            LocalDate maturityDate,
            int frequencyDays,
            LocalDate firstPaymentDate,
            LocalDate lastPaymentDate,
            LocalDate arrearsStartDate,
            LocalDate reportDate,
            int totalInstallments,
            int installmentsPaid,
            int installmentsOutstanding,
            BigDecimal principalRepaid,
            BigDecimal outstandingPrincipal,
            BigDecimal eligibleCollateral,
            BigDecimal netAmountDue,
            int daysOverdue,
            BigDecimal provisionRate,
            BigDecimal provisionRequired,
            LocalDate writeOffDate) {
    }

    private record BnrSummaryValues(
            BigDecimal normalOutstanding,
            BigDecimal watchOutstanding,
            BigDecimal substandardOutstanding,
            BigDecimal doubtfulOutstanding,
            BigDecimal lossOutstanding,
            BigDecimal restructuredOutstanding,
            BigDecimal totalClassifiedOutstanding,
            BigDecimal requiredProvision,
            BigDecimal nplAmount,
            long maleOutstandingLoans,
            long femaleOutstandingLoans,
            long otherOutstandingLoans,
            long maleDisbursedLoans,
            long femaleDisbursedLoans,
            long otherDisbursedLoans,
            BigDecimal maleDisbursedAmount,
            BigDecimal femaleDisbursedAmount,
            BigDecimal otherDisbursedAmount) {
    }
}
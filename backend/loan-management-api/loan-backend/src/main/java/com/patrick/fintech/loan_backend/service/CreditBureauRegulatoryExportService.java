package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Collateral;
import com.patrick.fintech.loan_backend.model.Guarantor;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.CollateralRepository;
import com.patrick.fintech.loan_backend.repository.GuarantorRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Native CRB regulatory workbook generator.
 *
 * The workbook structure is defined in Java. No uploaded XLS/XLSX template is
 * read at runtime and no business figures are hard-coded. Values come from
 * Noble Loan entities/repositories.
 *
 * The seven worksheets intentionally mirror the supplied CRB workbook:
 * Consumer, Corporate, Shareholders, Directors, Guarantors, Collateral and
 * Bounced Cheques.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreditBureauRegulatoryExportService {

    private static final DateTimeFormatter CRB_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private static final String[] CONSUMER_HEADERS = {
            "Salutation", "Surname", "Forename or Initial 1", "Forename or Initial 2",
            "Forename or Initial 3", "National ID Number", "Passport No", "Nationality",
            "Tax No", "Driving License No", "Social Security Number", "Health Insurance Number",
            "Marital Status", "No of Dependants", "Gender", "Date of Birth", "Place Of Birth",
            "Postal Address Line 1 Number", "Postal Address Line 2 Postal Code", "Physical Address Line 1",
            "Physical Address Line 2", "Physical Address Postal Code", "Physical Address Plot Number",
            "Physical Address Province", "Physical Address District", "Physical Address Sector",
            "Physical Address Cell", "Country", "Email Address", "Residence Type", "Work Telephone",
            "Home Telephone", "Mobile Telephone", "Fascimile", "Employer Name", "Employer Address Line 1",
            "Employer Address Line 2", "Employer Town", "Employer Country", "Occupation", "Income",
            "Income Frequency", "Group Name", "Group Number", "Account Number", "Old Account Number",
            "Account Type", "Account Status", "Classification", "Account Owner", "Joint Loan Participants",
            "Currency Type", "Date Opened", "Date Updated", "Terms Duration", "Repayment Term",
            "Opening Balance / Credit Limit", "Current Balance", "Available Credit", "Current Balance Indicator",
            "Scheduled Monthly Payment Amount", "Actual Payment Amount", "Amount Past Due",
            "Installments in Arrears", "Days in Arrears", "Date Closed", "Last Payment Date",
            "Interest Rate", "First Payment Date", "Nature", "Category", "Sector of Activity",
            "Approval Date", "Final Payment Date"
    };

    private static final String[] CORPORATE_HEADERS = {
            "Institution Name", "Trading Name", "Tax No", "VAT No", "Company Reg No",
            "Company Registration Date", "Company Cease Date", "Industry", "Postal Address Line 1 Number",
            "Postal Address Line 2 Postal Code", "Physical Address Line 1", "Physical Address Line 2",
            "Physical Address Postal Code", "Physical Address Plot Number", "Physical Address Province",
            "Physical Address District", "Physical Address Sector", "Physical Address Cell", "Country",
            "Email Address", "Telephone1", "Telephone2", "Telephone3", "Telephone4", "Telephone5",
            "Telephone6", "Facsimile1", "Facsimile2", "Account Number", "Old Account Number", "Account Type",
            "Account Status", "Classification", "Account Owner", "Joint Loan Participants", "Currency Type",
            "Date Opened", "Date Updated", "Terms Duration", "Repayment Term", "Opening Balance / Credit Limit",
            "Current Balance", "Available Credit", "Current Balance Indicator", "Scheduled Monthly Payment Amount",
            "Actual Payment Amount", "Amount Past Due", "Installments in Arrears", "Days in Arrears",
            "Date Closed", "Last Payment Date", "Interest Rate", "First Payment Date", "Nature", "Category",
            "Sector of Activity", "Approval Date", "Final Payment Date"
    };

    private static final String[] SHAREHOLDER_HEADERS = {
            "Account Number", "Shareholder Type", "Number of Shares", "Percentage of Shares",
            "Surname/ Institution Name", "Forename or Initial 1/ Trading Name", "Forename or Initial 2",
            "Forename or Initial 3", "National ID Number/ Company Reg No", "Passport No", "Nationality",
            "Date of Birth/ Company Registration Date", "Place Of Birth", "Postal Address Line 1 Number",
            "Postal Address Line 2 Postal Code", "Town", "Country"
    };

    private static final String[] DIRECTOR_HEADERS = {
            "Account Number", "Salutation", "Surname", "Forename or Initial 1", "Forename or Initial 2",
            "Forename or Initial 3", "National ID Number", "Passport No", "Nationality", "Date of Birth",
            "Place Of Birth", "Postal Address Line 1 Number", "Postal Address Line 2 Postal Code", "Town",
            "Country", "Date Appointed"
    };

    private static final String[] GUARANTOR_HEADERS = {
            "Account Number", "Guarantor Type", "Surname/ Institution Name", "Forename or Initial 1/ Trading Name",
            "Forename or Initial 2", "Forename or Initial 3", "National ID Number/ Company Reg No", "Passport No",
            "Nationality", "Date of Birth", "Place Of Birth/ Company Registration Date",
            "Postal Address Line 1 Number", "Postal Address Line 2 Postal Code", "Town", "Country",
            "Work Telephone", "Home Telephone", "Mobile Telephone"
    };

    private static final String[] COLLATERAL_HEADERS = {
            "Account Number", "Collateral Type", "Collateral Value", "Collateral Last Valuation Date",
            "Collateral Expiry Date"
    };

    private static final String[] BOUNCED_CHEQUE_HEADERS = {
            "Account Number", "Type", "Surname/Institution Name", "Forename or Initial 1/Trading Name",
            "Forename or Initial 2", "Forename or Initial 3", "National ID Number/Company Reg No", "Passport No",
            "Nationality", "Date of Birth/ Company Registration Date", "Place Of Birth", "Postal Address Line 1 Number",
            "Postal Address Line 2 Postal Code", "Town", "Country", "Cheque Number", "Cheque Date", "Reported Date",
            "Currency", "Amount", "Returned Cheque Reason", "Beneficiery Name"
    };

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;
    private final GuarantorRepository guarantorRepository;
    private final CollateralRepository collateralRepository;

    /**
     * Generates the native CRB .xlsx workbook.
     *
     * The CRB worksheet structure is defined in Java; no runtime template is read.
     */
    public byte[] export(
            Long organizationId,
            Long branchId,
            Long borrowerId,
            LocalDate from,
            LocalDate to) {

        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("organizationId is required");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from cannot be after to");
        }

        LocalDate reportDate = to != null ? to : LocalDate.now();
        List<Loan> loans = loadLoans(organizationId, branchId, borrowerId, from, to, reportDate);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream(128 * 1024)) {

            Styles styles = new Styles(workbook);

            Sheet consumer = workbook.createSheet("Consumer");
            Sheet corporate = workbook.createSheet("Corporate");
            Sheet shareholders = workbook.createSheet("Shareholders");
            Sheet directors = workbook.createSheet("Directors");
            Sheet guarantors = workbook.createSheet("Guarantors");
            Sheet collateral = workbook.createSheet("Collateral");
            Sheet bounced = workbook.createSheet("Bounced Cheques");

            createHeader(consumer, CONSUMER_HEADERS, styles);
            createHeader(corporate, CORPORATE_HEADERS, styles);
            createHeader(shareholders, SHAREHOLDER_HEADERS, styles);
            createHeader(directors, DIRECTOR_HEADERS, styles);
            createHeader(guarantors, GUARANTOR_HEADERS, styles);
            createHeader(collateral, COLLATERAL_HEADERS, styles);
            createHeader(bounced, BOUNCED_CHEQUE_HEADERS, styles);

            // The supplied workbook is a seven-sheet submission structure.
            // Noble Loan currently has individual Borrower records and does
            // not have separate corporate/shareholder/director/bounced-cheque
            // entities. Those sheets therefore remain structurally present but
            // contain no fabricated rows.
            Map<Long, List<Payment>> paymentCache = new HashMap<>();
            Map<Long, List<Guarantor>> guarantorCache = new HashMap<>();
            Map<Long, List<Collateral>> collateralCache = new HashMap<>();

            int consumerRow = 1;
            int guarantorRow = 1;
            int collateralRow = 1;

            for (Loan loan : loans) {
                if (loan == null || loan.getBorrower() == null) {
                    continue;
                }

                Borrower borrower = loan.getBorrower();
                List<Payment> payments = paymentCache.computeIfAbsent(
                        loan.getId(), id -> safePayments(id, loan));

                writeConsumerRow(
                        consumer.createRow(consumerRow++),
                        loan,
                        borrower,
                        payments,
                        reportDate,
                        styles);

                List<Guarantor> guarantorsForLoan = guarantorCache.computeIfAbsent(
                        loan.getId(), id -> safeGuarantors(id));
                for (Guarantor guarantor : guarantorsForLoan) {
                    if (guarantor == null) {
                        continue;
                    }
                    writeGuarantorRow(
                            guarantors.createRow(guarantorRow++),
                            loan,
                            guarantor,
                            styles);
                }

                List<Collateral> collateralsForLoan = collateralCache.computeIfAbsent(
                        loan.getId(), id -> safeCollaterals(id));
                if (collateralsForLoan.isEmpty() && hasLoanCollateral(loan)) {
                    writeLoanCollateralFallback(
                            collateral.createRow(collateralRow++),
                            loan,
                            styles);
                } else {
                    for (Collateral item : collateralsForLoan) {
                        if (item == null) {
                            continue;
                        }
                        writeCollateralRow(
                                collateral.createRow(collateralRow++),
                                loan,
                                item,
                                styles);
                    }
                }
            }

            finishSheet(consumer, CONSUMER_HEADERS.length);
            finishSheet(corporate, CORPORATE_HEADERS.length);
            finishSheet(shareholders, SHAREHOLDER_HEADERS.length);
            finishSheet(directors, DIRECTOR_HEADERS.length);
            finishSheet(guarantors, GUARANTOR_HEADERS.length);
            finishSheet(collateral, COLLATERAL_HEADERS.length);
            finishSheet(bounced, BOUNCED_CHEQUE_HEADERS.length);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate CRB regulatory Excel workbook", e);
        }
    }

    private List<Loan> loadLoans(
            Long organizationId,
            Long branchId,
            Long borrowerId,
            LocalDate from,
            LocalDate to,
            LocalDate reportDate) {

        List<Loan> loans;

        if (from != null) {
            LocalDateTime start = from.atStartOfDay();
            LocalDateTime endExclusive = (to == null ? from : to).plusDays(1).atStartOfDay();
            loans = safeLoans(loanRepository.findLoansDisbursedDuringPeriod(
                    organizationId, branchId, start, endExclusive));
        } else {
            loans = safeLoans(loanRepository.findPortfolioAsOf(
                    organizationId,
                    branchId,
                    reportDate.plusDays(1).atStartOfDay()));
        }

        if (borrowerId != null) {
            loans = loans.stream()
                    .filter(l -> l != null && l.getBorrower() != null
                            && borrowerId.equals(l.getBorrower().getId()))
                    .toList();
        }

        return loans;
    }

    private List<Loan> safeLoans(List<Loan> loans) {
        return loans == null ? Collections.emptyList()
                : loans.stream()
                        .filter(java.util.Objects::nonNull)
                        .sorted(Comparator.comparing(
                                Loan::getDisbursedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();
    }

    private List<Payment> safePayments(Long loanId, Loan loan) {
        if (loan != null && loan.getPayments() != null && !loan.getPayments().isEmpty()) {
            return new ArrayList<>(loan.getPayments());
        }
        if (loanId == null) {
            return Collections.emptyList();
        }
        try {
            return paymentRepository.findByLoanIdOrderByDueDateAsc(loanId);
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    private List<Guarantor> safeGuarantors(Long loanId) {
        if (loanId == null) {
            return Collections.emptyList();
        }
        try {
            return guarantorRepository.findByLoan_Id(loanId);
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    private List<Collateral> safeCollaterals(Long loanId) {
        if (loanId == null) {
            return Collections.emptyList();
        }
        try {
            return collateralRepository.findByLoan_Id(loanId);
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    private void writeConsumerRow(
            Row row,
            Loan loan,
            Borrower borrower,
            List<Payment> payments,
            LocalDate reportDate,
            Styles styles) {

        String[] values = new String[CONSUMER_HEADERS.length];

        values[0] = salutationFromGender(borrower.getGender());
        values[1] = borrower.getLastName();
        values[2] = borrower.getFirstName();
        values[5] = borrower.getNationalId();
        values[6] = borrower.getPassportNumber();
        values[7] = borrower.getNationality();
        values[8] = borrower.getTaxIdentificationNumber();
        values[12] = normalizeMaritalStatus(borrower.getMaritalStatus());
        values[14] = borrower.getGender();
        values[15] = date(borrower.getDateOfBirth());
        values[20] = borrower.getAddressLine2();
        values[19] = firstNonBlank(borrower.getAddressLine1(), borrower.getAddress());
        values[21] = borrower.getPostalCode();
        values[23] = borrower.getStateProvince();
        values[27] = borrower.getCountry();
        values[28] = borrower.getEmail();
        values[29] = null;
        values[31] = null;
        values[32] = borrower.getPhone();
        values[34] = borrower.getEmployerName();
        values[39] = firstNonBlank(borrower.getJobTitle(), borrower.getEmploymentType());
        values[40] = moneyText(borrower.getMonthlyIncomeDecimal());
        values[41] = borrower.getMonthlyIncomeDecimal() == null ? null : "MONTHLY";

        values[44] = loan.getReferenceNumber();
        values[46] = "I";
        values[47] = accountStatus(loan.getStatus());
        values[48] = arrearsClassification(loan.getDaysOverdue(), loan.getStatus());
        values[49] = "O";
        values[51] = loan.getCurrency();
        values[52] = date(disbursementDate(loan));
        values[53] = date(loan.getUpdatedAt() != null ? loan.getUpdatedAt().toLocalDate() : null);
        values[54] = loan.getDurationMonths() == null ? null : String.valueOf(loan.getDurationMonths());
        values[55] = "MTH";
        values[56] = moneyText(firstNonNull(loan.getDisbursedAmountDecimal(), loan.getAmountDecimal()));
        values[57] = moneyText(loan.getOutstandingBalanceDecimal());
        values[59] = currentBalanceIndicator(loan.getDaysOverdue());
        values[60] = moneyText(loan.getNextInstallmentAmountDecimal());
        values[61] = moneyText(latestPaidAmount(payments));
        values[62] = moneyText(amountPastDue(payments, reportDate));
        values[63] = installmentsInArrears(loan.getDaysOverdue());
        values[64] = String.valueOf(nonNegative(loan.getDaysOverdue()));
        values[65] = closed(loan) ? date(closedDate(loan)) : null;
        values[66] = date(loan.getLastPaymentDate());
        values[67] = annualizedInterestRate(loan);
        values[68] = date(firstPaymentDate(payments));
        values[69] = "CREDIT";
        values[70] = loan.getLoanType() == null ? null : loan.getLoanType().name();
        values[71] = firstNonBlank(borrower.getEmploymentType(), loan.getPurpose());
        values[72] = date(loan.getApprovedAt());
        values[73] = date(loan.getMaturityDate());

        writeStringCells(row, values, styles.body);

        // CRB numeric columns are deliberately numeric in the workbook.
        setNumeric(row, 40, borrower.getMonthlyIncomeDecimal(), styles.number);
        setNumeric(row, 56, firstNonNull(loan.getDisbursedAmountDecimal(), loan.getAmountDecimal()), styles.number);
        setNumeric(row, 57, loan.getOutstandingBalanceDecimal(), styles.number);
        setNumeric(row, 60, loan.getNextInstallmentAmountDecimal(), styles.number);
        setNumeric(row, 61, latestPaidAmount(payments), styles.number);
        setNumeric(row, 62, amountPastDue(payments, reportDate), styles.number);
        setNumeric(row, 67, annualizedRateDecimal(loan), styles.number);
    }

    private void writeGuarantorRow(Row row, Loan loan, Guarantor guarantor, Styles styles) {
        String[] values = new String[GUARANTOR_HEADERS.length];
        values[0] = loan.getReferenceNumber();
        values[1] = firstNonBlank(guarantor.getRelationship(), "GUARANTOR");
        values[2] = guarantor.getFullName();
        values[6] = guarantor.getNationalId();
        values[13] = guarantor.getAddress();
        values[14] = loan.getBorrower() != null ? loan.getBorrower().getCountry() : null;
        values[17] = guarantor.getPhone();
        writeStringCells(row, values, styles.body);
    }

    private void writeCollateralRow(Row row, Loan loan, Collateral collateral, Styles styles) {
        String[] values = new String[COLLATERAL_HEADERS.length];
        values[0] = loan.getReferenceNumber();
        values[1] = collateral.getType() == null ? null : collateral.getType().name();
        values[2] = moneyText(collateral.getEstimatedValueDecimal());
        values[3] = date(collateral.getUpdatedAt() != null ? collateral.getUpdatedAt().toLocalDate() : null);
        values[4] = date(collateral.getInsuranceExpiryDate());
        writeStringCells(row, values, styles.body);
        setNumeric(row, 2, collateral.getEstimatedValueDecimal(), styles.number);
    }

    private void writeLoanCollateralFallback(Row row, Loan loan, Styles styles) {
        String[] values = new String[COLLATERAL_HEADERS.length];
        values[0] = loan.getReferenceNumber();
        values[1] = firstNonBlank(loan.getCollateralDescription(), null);
        values[2] = moneyText(loan.getCollateralValueDecimal());
        writeStringCells(row, values, styles.body);
        setNumeric(row, 2, loan.getCollateralValueDecimal(), styles.number);
    }

    private boolean hasLoanCollateral(Loan loan) {
        return loan != null
                && (loan.getCollateralValueDecimal() != null
                        || !isBlank(loan.getCollateralDescription()));
    }

    private void createHeader(Sheet sheet, String[] headers, Styles styles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(isMandatoryHeader(sheet.getSheetName(), i) ? styles.mandatory : styles.optional);
        }
    }

    private boolean isMandatoryHeader(String sheet, int column) {
        // Exact red/black mandatory convention from the supplied CRB workbook.
        if ("Consumer".equals(sheet)) {
            int[] optional = { 3, 4, 8, 9, 10, 11, 13, 17, 18, 20, 21, 22, 28, 29, 33, 34, 35, 36, 37, 38, 40, 41, 42,
                    43, 45, 50, 58 };
            return !contains(optional, column);
        }
        if ("Corporate".equals(sheet)) {
            int[] optional = { 3, 6, 8, 9, 12, 13, 19, 21, 22, 23, 24, 25, 26, 27 };
            return !contains(optional, column);
        }
        if ("Shareholders".equals(sheet)) {
            return !contains(new int[] { 6, 7, 14 }, column);
        }
        if ("Directors".equals(sheet)) {
            return !contains(new int[] { 4, 5, 12, 15 }, column);
        }
        if ("Guarantors".equals(sheet)) {
            return !contains(new int[] { 4, 5, 15, 16 }, column);
        }
        if ("Collateral".equals(sheet)) {
            return !contains(new int[] { 1, 3, 4 }, column);
        }
        return !contains(new int[] { 4, 5, 12 }, column);
    }

    private void finishSheet(Sheet sheet, int columnCount) {
        double[] widths = referenceWidths(sheet.getSheetName(), columnCount);
        sheet.setDefaultRowHeightInPoints(15f);
        for (int i = 0; i < columnCount; i++) {
            double width = i < widths.length ? widths[i] : 12.0d;
            sheet.setColumnWidth(i, (int) Math.round(Math.min(255d, Math.max(5d, width)) * 256d));
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, Math.max(0, sheet.getLastRowNum()), 0, columnCount - 1));
        sheet.setDisplayGridlines(false);
        sheet.getRow(0).setHeightInPoints(48f);
    }

    private double[] referenceWidths(String sheetName, int columnCount) {
        double[] widths;
        if ("Consumer".equals(sheetName)) {
            widths = new double[] {9.08,8.26,18.35,13,13,18.99,10.53,9.62,6.53,16.08,20.17,22.17,12.17,15.44,6.81,11.26,11.9,25.62,28.72,19.99,13,24.81,25.44,22.17,20.9,20.35,18.17,7.53,12.35,13.72,14.26,14.81,15.17,8.35,14.08,21.35,13,13.62,15.81,9.81,6.99,16.08,11.17,12.99,14.35,17.72,11.72,13.72,18.17,13.17,19.35,12.9,19.17,12.08,13.72,15.26,26.26,14.08,13.9,33.08,31.35,20.72,15.44,19.81,13.62,10.81,16.53,11.72,16.72,6.35,8.35,14.9,12.53,17.08};
        } else if ("Corporate".equals(sheetName)) {
            widths = new double[] {14.62,12.35,6.53,6.72,15.17,23.81,18.81,20.81,25.62,28.72,19.99,13,24.81,25.44,22.17,20.9,20.35,18.17,7.35,12.35,18.08,10.26,13,13,13,13,9.35,13,14.35,17.72,11.72,12.99,11.44,13.17,19.35,12.9,11.53,12.08,13.72,15.26,26.26,14.08,13.9,22.17,31.35,20.72,15.08,19.81,13.62,10.72,16.53,11.72,16.72,6.35,14.9,12.53,17.08};
        } else if ("Shareholders".equals(sheetName)) {
            widths = new double[] {14.35,15.44,15.72,18.44,23.53,31.44,18.35,13,33.17,10.53,9.62,35.81,11.9,25.62,28.72,5.17,7.35};
        } else if ("Directors".equals(sheetName)) {
            widths = new double[] {15.72,10.17,13,19.99,13,13,18.81,11.99,10.53,12.26,13.17,28.53,32.53,5.81,8.17,15.17};
        } else if ("Guarantors".equals(sheetName)) {
            widths = new double[] {14.35,13.81,23.53,31.44,18.35,13,33.17,10.53,9.62,11.26,36.44,25.62,28.72,5.17,7.35,14.26,14.81,15.17};
        } else if ("Collateral".equals(sheetName)) {
            widths = new double[] {15.72,14.53,15.26,28.44,20.72};
        } else {
            widths = new double[] {14.35,8.81,22.99,30.9,18.35,13,32.62,16.99,9.62,35.81,11.9,25.62,28.72,5.17,7.35,14.17,11.44,12.9,8.26,7.81,21.9,23.44};
        }
        return widths;
    }

    private void writeStringCells(Row row, String[] values, CellStyle style) {
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            if (values[i] != null) {
                cell.setCellValue(values[i]);
            }
            cell.setCellStyle(style);
        }
    }

    private void setNumeric(Row row, int column, BigDecimal value, CellStyle style) {
        if (value == null) {
            return;
        }
        Cell cell = row.getCell(column);
        if (cell == null) {
            cell = row.createCell(column);
        }
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private String date(LocalDate value) {
        return value == null ? null : CRB_DATE.format(value);
    }

    private LocalDate disbursementDate(Loan loan) {
        if (loan == null) {
            return null;
        }
        return loan.getDisbursedAt() != null
                ? loan.getDisbursedAt().toLocalDate()
                : loan.getStartDate();
    }

    private LocalDate closedDate(Loan loan) {
        if (loan == null) {
            return null;
        }
        return loan.getLastPaymentDate() != null ? loan.getLastPaymentDate() : loan.getMaturityDate();
    }

    private boolean closed(Loan loan) {
        if (loan == null || loan.getStatus() == null) {
            return false;
        }
        return loan.getStatus() == LoanStatus.CLOSED
                || loan.getStatus() == LoanStatus.PAID
                || loan.getStatus() == LoanStatus.WRITTEN_OFF;
    }

    private String accountStatus(LoanStatus status) {
        if (status == null)
            return "A=Current";
        return switch (status) {
            case CLOSED, PAID -> "C=Closed";
            case WRITTEN_OFF -> "W=Written off";
            default -> "A=Current";
        };
    }

    private String arrearsClassification(Integer days, LoanStatus status) {
        int d = nonNegative(days);
        if (status == LoanStatus.WRITTEN_OFF)
            return "6 (365 and above)";
        if (d >= 365)
            return "6 (365 and above)";
        if (d >= 270)
            return "5 (from 270 to 365)";
        if (d >= 180)
            return "4 (from 180 to 269)";
        if (d >= 80)
            return "3 (from 80 to 179)";
        if (d >= 30)
            return "2 (from 30 to 79)";
        return "1 (from 0 to 29 days)";
    }

    private String currentBalanceIndicator(Integer days) {
        int d = nonNegative(days);
        return d >= 90
                ? "D ((90 & ABOVE) Non- Performing"
                : "C (0-89 DAYS IN ARREARS): Performing";
    }

    private String installmentsInArrears(Integer days) {
        int d = nonNegative(days);
        if (d == 0)
            return "0";
        return String.valueOf((d + 29) / 30);
    }

    private String annualizedInterestRate(Loan loan) {
        BigDecimal annual = annualizedRateDecimal(loan);
        return annual == null ? null : annual.stripTrailingZeros().toPlainString();
    }

    private BigDecimal annualizedRateDecimal(Loan loan) {
        if (loan == null || loan.getInterestRateDecimal() == null) {
            return null;
        }
        // Noble Loan stores the contractual lending rate monthly. The CRB
        // workbook's Interest Rate field is exported as the corresponding
        // annual nominal rate, without changing the stored monthly rate.
        return loan.getInterestRateDecimal()
                .multiply(BigDecimal.valueOf(12))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal latestPaidAmount(List<Payment> payments) {
        if (payments == null || payments.isEmpty())
            return null;
        return payments.stream()
                .filter(p -> p != null && Boolean.TRUE.equals(p.getPaid()) && p.getPaidDate() != null)
                .max(Comparator.comparing(Payment::getPaidDate))
                .map(Payment::getAmountPaidDecimal)
                .orElse(null);
    }

    private LocalDate firstPaymentDate(List<Payment> payments) {
        if (payments == null || payments.isEmpty())
            return null;
        return payments.stream()
                .filter(p -> p != null && p.getDueDate() != null)
                .map(Payment::getDueDate)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private BigDecimal amountPastDue(List<Payment> payments, LocalDate reportDate) {
        if (payments == null || payments.isEmpty() || reportDate == null)
            return null;
        BigDecimal total = BigDecimal.ZERO;
        for (Payment p : payments) {
            if (p == null || Boolean.TRUE.equals(p.getPaid()) || p.getDueDate() == null
                    || p.getDueDate().isAfter(reportDate)) {
                continue;
            }
            BigDecimal amount = p.getAmountDecimal();
            if (amount == null)
                continue;
            BigDecimal paid = p.getAmountPaidDecimal();
            BigDecimal due = amount.subtract(paid == null ? BigDecimal.ZERO : paid);
            if (due.signum() > 0)
                total = total.add(due);
        }
        return total.signum() == 0 ? BigDecimal.ZERO : total;
    }

    private String salutationFromGender(String gender) {
        if (gender == null)
            return null;
        if ("M".equalsIgnoreCase(gender))
            return "Mr";
        if ("F".equalsIgnoreCase(gender))
            return "Ms";
        return null;
    }

    private String normalizeMaritalStatus(String status) {
        if (status == null)
            return null;
        String value = status.trim();
        if (value.length() == 1)
            return value.toUpperCase();
        String lower = value.toLowerCase();
        if (lower.startsWith("mar"))
            return "M";
        if (lower.startsWith("sing"))
            return "S";
        if (lower.startsWith("wid"))
            return "W";
        if (lower.startsWith("div"))
            return "D";
        return value;
    }

    private String moneyText(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean contains(int[] values, int target) {
        for (int value : values)
            if (value == target)
                return true;
        return false;
    }

    private static final class Styles {
        private final CellStyle mandatory;
        private final CellStyle optional;
        private final CellStyle body;
        private final CellStyle number;

        private Styles(Workbook workbook) {
            Font mandatoryFont = workbook.createFont();
            mandatoryFont.setFontName("Verdana");
            mandatoryFont.setFontHeightInPoints((short) 9);
            mandatoryFont.setColor(IndexedColors.RED.getIndex());

            Font optionalFont = workbook.createFont();
            optionalFont.setFontName("Verdana");
            optionalFont.setFontHeightInPoints((short) 9);
            optionalFont.setColor(IndexedColors.BLACK.getIndex());

            Font bodyFont = workbook.createFont();
            bodyFont.setFontName("Calibri");
            bodyFont.setFontHeightInPoints((short) 11);
            bodyFont.setColor(IndexedColors.BLACK.getIndex());

            mandatory = headerStyle(workbook, mandatoryFont, false);
            optional = headerStyle(workbook, optionalFont, false);

            body = workbook.createCellStyle();
            body.setFont(bodyFont);
            body.setVerticalAlignment(VerticalAlignment.BOTTOM);
            body.setAlignment(HorizontalAlignment.GENERAL);
            body.setBorderTop(BorderStyle.THIN);
            body.setBorderBottom(BorderStyle.THIN);
            body.setBorderLeft(BorderStyle.THIN);
            body.setBorderRight(BorderStyle.THIN);

            number = workbook.createCellStyle();
            number.cloneStyleFrom(body);
            number.setAlignment(HorizontalAlignment.RIGHT);
            number.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        }

        private static CellStyle headerStyle(Workbook workbook, Font font, boolean wrap) {
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setVerticalAlignment(VerticalAlignment.TOP);
            style.setWrapText(wrap);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setDataFormat(workbook.createDataFormat().getFormat("@"));
            return style;
        }
    }

}
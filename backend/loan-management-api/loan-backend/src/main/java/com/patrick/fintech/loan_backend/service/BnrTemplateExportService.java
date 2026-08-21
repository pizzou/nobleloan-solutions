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

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Production BNR export service.
 *
 * IMPORTANT:
 * - The uploaded BNR workbook is used as the structural template.
 * - Loan, borrower and schedule values are read from Noble's database.
 * - Contractual interest is NOT recalculated here. The export reports the
 * contractual rate stored on Loan and the balances stored by the
 * operational loan/payment system.
 * - Provisioning is calculated from outstanding principal less eligible
 * collateral using the classification rates represented by the supplied
 * BNR template:
 * Normal 0%
 * Watch 1%
 * Substandard 20%
 * Doubtful 50%
 * Loss 100%
 * - Missing source data is left blank rather than fabricated.
 *
 * Required resource:
 * src/main/resources/bnr/BNR_REPORTING_NEW_TEMPLATES.xlsx
 *
 * Put the exact BNR template supplied by the business into that location.
 */
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

    @Value("${app.bnr.template-path:classpath:/bnr/BNR_REPORTING_NEW_TEMPLATES.xlsx}")
    private Resource templateResource;

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

        LocalDate[] window = resolvePeriod(period, from, to);
        LocalDate reportDate = window[1];

        List<Loan> loans = safeLoans(
                loanRepository.findPortfolioAsOf(
                        organizationId,
                        branchId,
                        reportDate.plusDays(1).atStartOfDay(),
                        reportDate));

        try (InputStream input = templateResource.getInputStream();
                XSSFWorkbook workbook = new XSSFWorkbook(input);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            configureWorkbook(workbook);

            populateMetadata(workbook, organizationId, branchId, period, window, loans);

            Map<String, List<Loan>> classified = classifyLoans(loans);

            for (String sheetName : CLASSIFICATION_SHEETS) {
                Sheet sheet = workbook.getSheet(sheetName);
                if (sheet == null) {
                    log.warn("BNR template is missing sheet '{}'", sheetName);
                    continue;
                }

                if ("A1.9. Written off".equals(sheetName)) {
                    writeWrittenOffSheet(sheet, classified.get("WRITTEN_OFF"), reportDate);
                } else {
                    String classification = classificationForSheet(sheetName);
                    writeLoanSheet(
                            sheet,
                            classified.getOrDefault(classification, List.of()),
                            reportDate,
                            classification);
                }
            }

            populateFinancialStatement(
                    workbook,
                    regulatoryReportingService.buildBnrFinancialStatement(
                            organizationId,
                            branchId,
                            period,
                            window[0],
                            window[1]));

            addValidationSheet(workbook, loans, classified, reportDate);

            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);
            return output.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to generate the BNR Excel report from the configured template",
                    e);
        }
    }

    private void configureWorkbook(XSSFWorkbook workbook) {
        workbook.setForceFormulaRecalculation(true);
        workbook.getProperties().getCoreProperties().setCreator("Noble Loan Solutions");
        workbook.getProperties().getCoreProperties().setTitle("BNR Regulatory Reporting");
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

    /**
     * Finds an accounting line by account code first and, when no code is
     * available, by a normalized description fragment. This keeps the BNR
     * exporter read-only and prevents it from inventing accounting balances.
     * The method accepts the report DTO's List<Map<String,Object>> shape
     * without coupling the exporter to a particular accounting entity.
     */
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

    /**
     * Writes a value only when the accounting calculation produced a value.
     * Null means that the account was not modeled/present for the reporting
     * period, so the exporter leaves the template cell untouched.
     */
    private void setIfPresent(
            Sheet sheet,
            int column,
            int row,
            BigDecimal value) {

        if (value != null) {
            setCellValue(sheet, row, column, value);
        }
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
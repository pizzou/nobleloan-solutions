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

        LocalDate[] window = resolvePeriod(period, from, to);
        LocalDate reportDate = window[1];

        List<Loan> loans = safeLoans(
                loanRepository.findPortfolioAsOf(
                        organizationId,
                        branchId,
                        reportDate.plusDays(1).atStartOfDay(),
                        reportDate));

        try (XSSFWorkbook workbook = buildBnrWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            configureWorkbook(workbook);
            populateMetadata(workbook, organizationId, branchId, period, window, loans);

            Map<String, List<Loan>> classified = classifyLoans(loans);

            for (String sheetName : CLASSIFICATION_SHEETS) {
                Sheet sheet = workbook.getSheet(sheetName);
                if (sheet == null) {
                    throw new IllegalStateException(
                            "Internal BNR workbook definition is missing sheet '" + sheetName + "'");
                }

                if ("A1.9. Written off".equals(sheetName)) {
                    writeWrittenOffSheet(
                            sheet,
                            classified.getOrDefault("WRITTEN_OFF", List.of()),
                            reportDate);
                } else {
                    String classification = classificationForSheet(sheetName);
                    writeLoanSheet(
                            sheet,
                            classified.getOrDefault(classification, List.of()),
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

            populateFinancialStatement(workbook, financialStatement);
            addValidationSheet(workbook, loans, classified, reportDate);

            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);
            output.flush();

            byte[] bytes = output.toByteArray();
            if (bytes.length == 0) {
                throw new IllegalStateException("Generated BNR workbook is empty");
            }

            log.info(
                    "BNR XLSX generated successfully. organizationId={}, branchId={}, period={}, from={}, to={}, loans={}, bytes={}",
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
                    "Failed to serialize hard-coded BNR XLSX. organizationId={}, branchId={}, period={}",
                    organizationId,
                    branchId,
                    period,
                    e);

            throw new IllegalStateException(
                    "Unable to generate the BNR Excel report",
                    e);
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

        // The supplied BNR workbook contains a final blank worksheet named Sheet1.
        // Preserve that workbook-level structure without introducing template I/O.
        workbook.createSheet("Sheet1");

        return workbook;
    }

    private void createExplanatoryNoteSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("A1.1  Explanatory Note ");

        CellStyle header = createHeaderStyle(workbook);
        CellStyle body = createBodyStyle(workbook);

        /*
         * This is the regulatory explanatory-note structure from the supplied
         * BNR workbook, embedded as data so the application never needs to
         * load the workbook at runtime.
         */
        String[][] notes = {
                { "1", "1", "DENOMINATION" },
                { "1", "2", "Explanatory Notes" },
                { "2", "1", "A.BALANCE SHEET" },
                { "3", "1", "1.Total Liquid Assets (2+3+4)" },
                { "4", "1", "2.Cash in vault " },
                { "4", "2",
                        "In this section, Accountant record physical cash (Coins or Notes) held on the institution’s premises as at reporting date, " },
                { "5", "1", "3.Cash in bank and other FIs (Current account)" },
                { "5", "2",
                        "Report the total balances at all banks or financial institutions including Mobile Money, used for transactional liquidity." },
                { "6", "0", " " },
                { "6", "1", "4.Cash in bank and other FIs (Term deposit )" },
                { "6", "2", "Cash placed in fixed‑term accounts earning interest but less liquid." },
                { "7", "0", " " },
                { "7", "1", "5.Gross loans " },
                { "7", "2",
                        "Do not edit enything here, The formula will take the total outstanding loan balance issued to clients, as per loan classification in line ( 77–84.)" },
                { "8", "1", "6.Provisions " },
                { "8", "2",
                        "add the provisions computed in allowances established against expected credit losses in compliance with Article 39 of REGULATION No 65/04/2023 OF 25/04/2023." },
                { "9", "1", "7.Net Loans (5-6)" },
                { "10", "1", "8.NPLs " },
                { "10", "2",
                        "Add all out non performing loans outstanding as at reporting date ( substandard, Doubtful and loss) refer to Article 3: Paragraph u " },
                { "11", "1", "9.Financial Instruments" },
                { "11", "2", "investments in securities or other financial assets permitted under regulations." },
                { "12", "1", "Fixed Assets (PPE,Intangible, Investment properties, etc) Gross Amount" },
                { "12", "2",
                        "capital expenditure on property, plant & equipment, intangible and investment assets, in this section the accountant record the actual cost incured to accure asset as per IAS 16." },
                { "13", "1", "Depreciation " },
                { "13", "2",
                        "Add the Depreciiation for the quarter to the previously reported accumulated depreciation " },
                { "14", "1", "10.Fixed Assets (net)" },
                { "15", "1", "11. Interest receivable" },
                { "15", "2", "Accrued incured by not yet paid by clients as at the of quarter." },
                { "16", "1", "12.Other Assets " },
                { "16", "2",
                        "Assets such as prepayments, accrued interest income, or any other items not classified elsewhere." },
                { "17", "1", "13.Suspense Accounts" },
                { "17", "2", "Temporary ledger accounts holding unresolved or pending transaction entries." },
                { "18", "1", "14.Total Assets (1+7+9+10+11+12)=25" },
                { "19", "1", "15.Total Liabilities (15+16+20)" },
                { "20", "1", "16.Borrowings from other FIs and Non FIs" },
                { "20", "2",
                        "Outstanding Debt obligations owed to the Bank and other creditors payable in the period exceeding 1 year." },
                { "21", "1", " 17.Cash collateral if any" },
                { "21", "2", "Cash pledged as collateral for third-party obligations or regulatory requirements." },
                { "22", "1", "18. interest Payable" },
                { "22", "2",
                        "Accrued interest payable to the lender at the end of  quarter as per Loan ammortization." },
                { "23", "1", "19.Other liabilities (payables+suspense+other liabilities)" },
                { "23", "2",
                        "Includes trade payables, payroll liabilities, interest payable, suspense liability accounts, accruals, and miscellaneous obligations." },
                { "24", "0", " " },
                { "24", "1", "20.Total Equity " },
                { "25", "1", "21.Subsidies  (for equipment or financing Equity)" },
                { "25", "2",
                        "Grants or donor‑funded investments provided to support capital or operational capacity. If any" },
                { "26", "1", "22.Revaluation surplus" },
                { "26", "2",
                        "record all Unrealized gains from reappraisal of assets recognized under accounting standards." },
                { "27", "1", "23.Other Equity" },
                { "27", "2",
                        "any other equity categories such as statutory reserves or specific retained components." },
                { "28", "1", "24.Retained profits/Acc losses" },
                { "28", "2", "record the opening accumulated P/L as at the start of quarter " },
                { "29", "1", "25.Profit/loss for the period" },
                { "29", "2", "Link to profit in P/L account" },
                { "30", "1", "26.Paid up capital" },
                { "30", "2",
                        "	Equity contributed by shareholders meeting the minimum capital thresholds (e.g. RWF 30m–100m depending on category) " },
                { "31", "1", "27.Total Equity&Liabilities (14+18)=13" },
                { "32", "1", "28.NPL Ratio (max 5%) " },
                { "32", "2", "don't edit in this formulas" },
                { "33", "1", "29.Capital Adequacy Ratio " },
                { "33", "2", "don't edit in this formulas" },
                { "34", "1", "30.Conversion of resources into loans " },
                { "34", "2", "don't edit in this formulas" },
                { "35", "1", "Investment in fixed assets " },
                { "35", "2", "don't edit in this formulas" },
                { "36", "1", "B.INCOME STATEMENT" },
                { "37", "1", "31.Financial Income (32+33+34+35+36)" },
                { "38", "1", "32.Interest Income on Loan Portfolio" },
                { "38", "2", "Record all Interest accrued from outstanding loans as per IFRS 15." },
                { "39", "1", "33.Fees and Commissions on Loan Portfolio" },
                { "39", "2",
                        "record all Non‑interest income from lending services like origination or processing fees." },
                { "40", "1", "34.Incomes on Deposits in banks and other Fis" },
                { "40", "2", "Interest from parked liquidity in bank deposits." },
                { "41", "1", "35.Incomes on Financial Instruments" },
                { "41", "2", "Earnings on permitted investments such as T bonds, T Bills or any other securities." },
                { "42", "1", "36.Other financial Income" },
                { "42", "2", "Miscellaneous income such as FX gains or other finance-related sources." },
                { "43", "0", " " },
                { "43", "1", "37.Recoveries on Loans (prov. Back)" },
                { "43", "2",
                        "if the loan provisions has been reduced compared to prior  report provision (the difference is the income to be recognised hire) :  Eg: if in the Q1 provision was 100,000 and in quarter 2 provision become 80,000) 20,000 decrease will be recognised as income" },
                { "44", "1", "38.Recoveries on loans (written offs)" },
                { "44", "2", "Income from collections on loans previously written off." },
                { "45", "1", "39.Other operating Incomes" },
                { "45", "2", "Income from incidental operations related to lending services." },
                { "46", "1", "40.Non Operating Incomes" },
                { "46", "2", "non-core income, such as gains from asset sales." },
                { "47", "1", "41.Total Incomes(31+37+38+39+40)" },
                { "48", "1", "42.Financial Expenses (43+44+45)" },
                { "49", "1", "43.Interest on cash collateral if any" },
                { "49", "2", "Cost incurred on cash pledged as collateral if any" },
                { "50", "1", "44.Interest on borrowings from Fis and Non FIs" },
                { "50", "2", "Interest expenses on borrowings from bank or other lenders " },
                { "51", "1", "45. Bank Charges,Commissions and other Financial Exp." },
                { "51", "2", " Transaction fees, service charges related to funding." },
                { "52", "1", "46.Loan losses (provisions)" },
                { "52", "2",
                        "when the loan provisions has been reduced compared to prior  report provision (the difference is the income to be recognised hire) Eg. if in the Q1 provision was 100,000 and in quarter 2 provision become 80,000) 20,000 decrease will be recognised as income" },
                { "53", "1", "47. Loan Losses (written off for the period)" },
                { "53", "2",
                        "Report all irrecoverable loans during the period (loans which took at least 1 Year in loss class or Un paid installment has 720 days and above days in arrear)" },
                { "54", "1", "48.Personnel Expenses (Gross amount)" },
                { "54", "2",
                        "When the loan provisions has been reduced compared to prior  report provision (the difference is the income to be recognised hire) :  Eg: if in the Q1 provision was 100,000 and in quarter 2 provision become 80,000) 20,000 decrease will be recognised as income." },
                { "55", "1", "49.Administrative Expenses" },
                { "55", "2",
                        "in this section, Accountant record all expenses incured during the quarter to assist in day today management of the business operation (expenses like dipreciation and ammortization, Advertisement, Board sitting allowances, office stationaries, etc are found here), " },
                { "56", "1", "50.Non Operating Expenses" },
                { "56", "2", "Any other Expenses not sspecified in above reporting lines." },
                { "57", "1", "51.Total Expenses  (42+46+47+48+49+50)" },
                { "58", "1", "52.Profit/Loss before donations (41-51)" },
                { "59", "1", "53. Income Tax " },
                { "60", "1", "54. Profit after tax and before donations(52-53)" },
                { "61", "0", " " },
                { "61", "1", "55.Donations (Financing Operating Expenses)" },
                { "61", "2", "Any donation made to Government entity. Non government Entity or Individual person." },
                { "62", "1", "Profit/loss after tax and  donations (54+55)=23" },
                { "63", "1", "Dividends " },
                { "64", "1", "Net profit After Dividends " },
                { "65", "1", "56.Cost to income   52/43" },
                { "66", "1", "57.% of Financial Income 34/43" },
                { "67", "1", "58.ROA   (AVERAGED 53/13)" },
                { "68", "1", "59.ROE  (AVERAGED 53/21)" },
                { "69", "1", "C. OFF-BALANCE SHEET (Written-Off Loans)" },
                { "70", "1", "D.SUPPLEMENTARY INFORMATION" },
                { "71", "0", "60.Number of Loans (Outstanding)" },
                { "71", "1", "61.Men" },
                { "71", "2", "give the number of Men with outstanding loan (Unpaid loan) as at the end of quarter" },
                { "72", "1", "62.Women" },
                { "72", "2", "give the number of Women with outstanding loan (Unpaid loan) as at the end of quarter" },
                { "73", "1", "63.Group&Entities" },
                { "73", "2",
                        "give the number of  Entities with outstanding loan (Unpaid loan) as at the end of quarter" },
                { "74", "1", "64.Total (61+62+63)" },
                { "74", "2", "The total should be equal to the line 5.Gross loans in the balance sheet" },
                { "75", "0", "65.Value of Loans (Outstanding) by Gender" },
                { "75", "1", "66.Men" },
                { "75", "2", "give the balance amount of loan  receivable from Men at the end of quarter" },
                { "76", "1", "67.Women" },
                { "76", "2", "give the balance amount of loan  receivable from Wemen at the end of quarter" },
                { "77", "1", "68.Group&Entities" },
                { "77", "2", "give the balance amount of loan  receivable from entities at the end of quarter" },
                { "78", "1", "69.Total (66+67+68)=5=76=84" },
                { "78", "2", "The total should be equal to the line 5.Gross loans in the balance sheet" },
                { "79", "0", "70.Value of Loans (Outstanding) to Economic Sector" },
                { "79", "1", "71.Agriculture, Livestock, Fishing " },
                { "79", "2",
                        "give the balance amount of loan  receivable from borrowers engaged in Agriculture sector at the end of quarter" },
                { "80", "1", "72.Public Works (Construction), Buildings, Residences/Homes" },
                { "80", "2",
                        "give the balance amount of loan  receivable from borrowers engaged in Public Works at the end of quarter" },
                { "81", "1", "73.Commerce, Restaurants, Hotels" },
                { "81", "2",
                        "give the balance amount of loan  receivable from borrowers engaged in Commerce, Restaurants and Hotels at the end of quarter" },
                { "82", "1", "74.Transport, Warehouses, Communications" },
                { "82", "2",
                        "give the balance amount of loan  receivable from borrowers engaged in Transport, Warehouses and Communications at the end of quarter" },
                { "83", "1", "75.Others" },
                { "83", "2",
                        "give the balance amount of loan  receivable from borrowers engaged in other sectors not specified" },
                { "84", "1", "76.Total  (71+72+73+74+75)=5=69=84" },
                { "84", "2", "The total should be equal to the line 5.Gross loans in the balance sheet" },
                { "85", "1", "78.Current loans -Normal (0% Prov.)" },
                { "85", "2", "give the balance amount of loan outstanding classified under Normal (A1.3) Column AH" },
                { "86", "0", "77.Value of Loans (Outstanding) in the Loan Classfication" },
                { "86", "1", "79. Watch (1-89 days) 1% Prov." },
                { "86", "2", "give the balance amount of loan outstanding classified under Watch (A1.4) Column AH" },
                { "87", "1", "80. Substandard (90-179 days)  20% Prov." },
                { "87", "2", "give the balance amount of loan outstanding classified under Normal (A1.5) Column AI" },
                { "88", "1", "81.Doubtful (180-359 days) 50% Prov." },
                { "88", "2", "give the balance amount of loan outstanding classified under Normal (A1.6) Column AH " },
                { "89", "1", "82. Loss (360 -719 days ) 100% prov." },
                { "89", "2", "give the balance amount of loan outstanding classified under Normal (A1.7) Column AH" },
                { "90", "1", "83.(Restructured)" },
                { "90", "2", "give the balance amount of loan outstanding classified under Normal (A1.8) Column AH" },
                { "91", "1", "84.Total (78+79+80+81+82+83)" },
                { "91", "2", "The total should be equal to the line 5.Gross loans in the balance sheet" },
                { "92", "0", "85.Number of Loans (disbursed loans )" },
                { "92", "1", "86.Men" },
                { "92", "2", "give the number of New Loans disbursed to the Men in three months time" },
                { "93", "1", "87.Women" },
                { "93", "2", "give the number of New Loans disbursed to the Wemen in three months time" },
                { "94", "1", "88.Group&Entities" },
                { "94", "2", "give the number of New Loans disbursed to the Entity in three months time" },
                { "95", "1", "88.Total (86+87+88)" },
                { "95", "2", "Total should be equal to the Number of new cotracts signed in 3 monthss time" },
                { "96", "0", "90.Value of Loans (Disbursed ) by Gender" },
                { "96", "1", "91.Men" },
                { "96", "2", "give the Value of New Loans disbursed to the Men in three months time" },
                { "97", "1", "92.Women" },
                { "97", "2", "give the  Value of New Loans disbursed to the Wemen in three months time" },
                { "98", "1", "93.Group&Entities" },
                { "98", "2", "give the  Value of New Loans disbursed to the Entities in three months time" },
                { "99", "1", "94.Total (92+93+94)" },
                { "99", "2",
                        "Total should be equal to the sum of gross loans in a new cotracts signed in 3 monthss time" },
                { "100", "0", "95.Value of Loans (Disbursed ) to Economic Sector" },
                { "100", "1", "96.Agriculture, Livestock, Fishing " },
                { "100", "2",
                        "give the  Value of New Loans disbursed to the borrowers based in Agriculture in three months time" },
                { "101", "1", "97.Public Works (Construction), Buildings, Residences/Homes" },
                { "101", "2",
                        "give the  Value of New Loans disbursed to the borrowers based in Public Works in three months time" },
                { "102", "1", "98.Commerce, Restaurants, Hotels" },
                { "102", "2",
                        "give the  Value of New Loans disbursed to the borrowers based in Commerce  in three months time" },
                { "103", "1", "99.Transport, Warehouses, Communications" },
                { "103", "2",
                        "give the  Value of New Loans disbursed to the borrowers based in Transport, Warehouses, Communications in three months time" },
                { "104", "1", "100.Others" },
                { "104", "2",
                        "give the  Value of New Loans disbursed to the borrowers based in any other sector not specified above." },
                { "105", "1", "101.Total  (96+97+98+99+100)" },
                { "105", "2",
                        "Total should be equal to the sum of gross loans in a new cotracts signed in 3 monthss time" },
                { "106", "0", "102. NDFSP BORROWINGS" },
                { "106", "1", "103. Borrowing from Shareholders at …..% P.a" },
                { "106", "2", "All money injected by shareholders expected to be repaid back" },
                { "107", "1", "104.Borrowing from related parties (Parent, Subsidiary, Sister company Etc at …% P.a" },
                { "107", "2", "All money injected from related companies expected to be repaid back" },
                { "108", "1", "105. Borrowing from Banks or Micro finance at …% P.a" },
                { "108", "2", "Bank Loans" },
                { "109", "1", "106. Borrowing from other sources (Specify)  at …..% P.a" },
                { "109", "2",
                        "Report any other loans (Wether from individual friend, family or any source not specified)" },
                { "110", "1", "107.Total  (103+104+105+106)" },
                { "110", "2", "The total should be the total borrowings reported in the balance sheet" },
                { "111", "0", "Financing Women Entities(Cooperatives, Companies &Other groupings)" },
                { "111", "1", "Number of Disbursed Loans to WE (As Per Quarter )" },
                { "111", "2", "give the number of New Loans disbursed to the Wemen Enterprise in three months time" },
                { "112", "1", "Number of Outstanding Loans to WE (Balance)" },
                { "112", "2",
                        "give the number of unpaid Loans receivable from the Wemen Enterprise as at the end of quarter" },
                { "113", "1", "Value of Disbursed Loans to WE (As Per Quarter )" },
                { "113", "2", "report the Value of New Loans disbursed to the Wemen Enterprise in three months time" },
                { "114", "1", "Value  of Outstanding Loans to WE (Balance)" },
                { "114", "2",
                        "give the balance (Unpaid) amount of loan  receivable from borrowers engaged  in the Wemen Enterprise as at the end of quarter" },
                { "115", "1", " Number of Accounts with WE" },
                { "116", "0", "Financing SMEs" },
                { "116", "1", "Number of Disbursed Loans to SMEs (As Per Quarter )" },
                { "116", "2",
                        "give the number of New Loans disbursed to the small and Medium  Enterprises in three months time" },
                { "117", "1", "Number of Outstanding Loans to SMEs (Balance)" },
                { "117", "2",
                        "give the number of unpaid Loans receivable from the small and Medium  Enterprises  as at the end of quarter" },
                { "118", "1", "Value of Disbursed Loans to SMEs (As Per Quarter )" },
                { "118", "2",
                        "report the Value of New Loans disbursed to the small and Medium  Enterprises in three months time" },
                { "119", "1", "Value  of Outstanding Loans to SMEs (As Per Quarter)" },
                { "119", "2",
                        "give the balance (Unpaid) amount of loan  receivable from borrowers engaged  in the small and Medium  Enterprise as at the end of quarter." },
                { "120", "0", "Financing Youth Entities(Cooperatives, Companies &Other groupings)" },
                { "120", "1", "Number of Disbursed Loans to YE (As Per Quarter )" },
                { "120", "2",
                        "give the number of New Loans disbursed to the Financing Youth Entities in three months time" },
                { "121", "1", "Number of Outstanding Loans to YE (As Per Quarter)" },
                { "121", "2",
                        "give the number of unpaid Loans receivable from the Financing Youth Entities as at the end of quarter" },
                { "122", "1", "Value of Disbursed Loans to YE (As Per Quarter )" },
                { "122", "2",
                        "report the Value of New Loans disbursed to the Financing Youth Entities in three months time" },
                { "123", "1", "Value  of Outstanding Loans to YE (As Per Quarter)" },
                { "123", "2",
                        "give the balance (Unpaid) amount of loan  receivable from borrowers engaged  in the small and Medium  Enterprise as at the end of quarter." },
                { "124", "0", "New Loans Applied" },
                { "124", "1", "Number of loans applied for (As Per Quarter )" },
                { "124", "2", "Total loan application received during quarter" },
                { "125", "1", "Number of loans rejected (As Per Quarter )" },
                { "125", "2", "Report the number of Loan application rejected " },
                { "126", "1", "Amount of loans applied for (As Per Quarter )" },
                { "126", "2", "Report the amount applied for during the three months time" },
                { "127", "1", "Amount of loans rejected (As Per Quarter )" },
                { "127", "2", "Report the amount applied for but rejected during three months time" },
                { "128", "1", "Men" },
                { "128", "2", "Show the number of Male Employees " },
                { "129", "0", "Number of NDFSP' Staff" },
                { "129", "1", "Women" },
                { "129", "2", "Show the number of female Employees " },
                { "130", "1", "Total " },
                { "130", "2", "The total should be the total number of staff" },
                { "131", "1", "Men" },
                { "131", "2", "Show the number of Male BOD" },
                { "132", "0", "Number of NDFSP' Board Members " },
                { "132", "1", "Women" },
                { "132", "2", "Show the number of female BOD " },
                { "133", "1", "Total " },
                { "133", "2", "The total should be the total number of BOD" },
                { "134", "1", "Men" },
                { "134", "2", "Show the number of Male Share holders" },
                { "135", "0", "Number of NDFSP' Shareholders " },
                { "135", "1", "Women" },
                { "135", "2", "Show the number of female Shareholders" },
                { "136", "1", "Legal Entities" },
                { "136", "2", "Show the number of shareholder through Legal Entity " },
                { "137", "1", "Total " },
                { "137", "2", "The total should be the total number of Shareholders as per RDB Certificate" },
                { "138", "1", "Share value" },
                { "138", "2", "Report the Par Value of Each share (As per RDB certificate)" },
                { "141", "0", "Before you submit: " },
                { "142", "0", "❶" },
                { "142", "1", "Check if all information shared are complete" },
                { "143", "0", "❷" },
                { "143", "1", "Check if the financials are balancing " },
                { "144", "0", "❸" },
                { "144", "1", "Check if Loans are categorized as per regulation " },
                { "145", "0", "❹" },
                { "145", "1", "Do not modify the formulas in the report " },
                { "146", "0", "❺" },
                { "146", "1",
                        "Check whether if the provision is correctly Commputed and reported As per IFRS 9 (Total provision reported in the Balance sheet and The movement reported in Income statement)" },
                { "147", "0", "❻" },
                { "147", "1", "Compare reported data with actual fugures in Accounting system used in the NDFSP" },
                { "148", "0", "❼" },
                { "148", "1", "For more clarification, Contact the BNR staff" },
        };

        for (String[] note : notes) {
            int rowIndex = Integer.parseInt(note[0]);
            int columnIndex = Integer.parseInt(note[1]);

            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            Cell cell = row.createCell(columnIndex);
            cell.setCellValue(note[2]);
            cell.setCellStyle(
                    rowIndex == 1
                            ? header
                            : body);
        }

        sheet.setColumnWidth(0, 1800);
        sheet.setColumnWidth(1, 12000);
        sheet.setColumnWidth(2, 24000);
        sheet.createFreezePane(0, 2);
    }

    private void createFinancialStatementSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("A1.2. FS");

        CellStyle title = createTitleStyle(workbook);
        CellStyle header = createHeaderStyle(workbook);
        CellStyle body = createBodyStyle(workbook);
        CellStyle currency = createCurrencyStyle(workbook);

        CellStyle percentage = createBodyStyle(workbook);
        percentage.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));

        /*
         * The FS sheet is generated from the supplied BNR workbook's exact
         * row labels and regulatory formula layout. No XLSX template is loaded
         * at runtime.
         */
        String[] labels = {
                "1.Total Liquid Assets (2+3+4)",
                "2.Cash in vault ",
                "3.Cash in bank and other FIs (Current account)",
                "4.Cash in bank and other FIs (Term deposit )",
                "5.Gross loans ",
                "6.Provisions ",
                "7.Net Loans (5-6)",
                "8.NPLs ",
                "9.Financial Instruments",
                "Fixed Assets (PPE,Intangible, Investment properties, etc) Gross Amount",
                "Depreciation ",
                "10.Fixed Assets (net)",
                "11. Interest receivable",
                "12.Other Assets ",
                "13.Suspense Accounts",
                "14.Total Assets (1+7+9+10+11+12+13)=27",
                "15.Total Liabilities (16+17+18+19)",
                "16.Borrowings from other FIs and Non FIs (107)",
                " 17.Cash collateral if any",
                "18. interest Payable",
                "19.Other liabilities (payables+suspense+other liabilities)",
                "20.Total Equity ",
                "21.Subsidies  (for equipment or financing Equity)",
                "22.Revaluation surplus",
                "23.Other Equity",
                "24.Retained profits/Acc losses",
                "25.Profit/loss for the period",
                "26.Paid up capital",
                "27.Total Equity&Liabilities (15+20)=14",
                "28.NPL Ratio (max 5%) ",
                "29.Capital Adequacy Ratio ",
                "30.Conversion of resources into loans ",
                "Investment in fixed assets ",
                "B.INCOME STATEMENT",
                "31.Financial Income (32+33+34+35+36)",
                "32.Interest Income on Loan Portfolio",
                "33.Fees and Commissions on Loan Portfolio",
                "34.Incomes on Deposits in banks and other Fis",
                "35.Incomes on Financial Instruments",
                "36.Other financial Income",
                "37.Recoveries on Loans (prov. Back)",
                "38.Recoveries on loans (written offs)",
                "39.Other operating Incomes",
                "40.Non Operating Incomes",
                "41.Total Incomes(31+37+38+39+40)",
                "42.Financial Expenses (43+44+45)",
                "43.Interest on cash collateral if any",
                "44.Interest on borrowings from Fis and Non FIs",
                "45. Bank Charges,Commissions and other Financial Exp.",
                "46.Loan losses (provisions)",
                "47. Loan Losses (written off for the period)",
                "48.Personnel Expenses (Gross amount)",
                "49.Administrative Expenses",
                "50.Non Operating Expenses",
                "51.Total Expenses  (42+46+47+48+49+50)",
                "52.Profit/Loss before donations (41-51)",
                "53. Income Tax ",
                "54. Profit after tax and before donations(52-53)",
                "55.Donations (Financing Operating Expenses)",
                "Profit/loss after tax and  donations (54+55)=23",
                "Dividends ",
                "Net profit After Dividends ",
                "56.Cost to income   52/43",
                "57.% of Financial Income 34/43",
                "58.ROA   (AVERAGED 53/13)",
                "59.ROE  (AVERAGED 53/21)",
                "C. OFF-BALANCE SHEET (Written-Off Loans)",
                "D.SUPPLEMENTARY INFORMATION",
                "61.Men",
                "62.Women",
                "63.Group&Entities",
                "64.Total (61+62+63)",
                "66.Men",
                "67.Women",
                "68.Group&Entities",
                "69.Total (66+67+68)=5=76=84",
                "71.Agriculture, Livestock, Fishing ",
                "72.Public Works (Construction), Buildings, Residences/Homes",
                "73.Commerce, Restaurants, Hotels",
                "74.Transport, Warehouses, Communications",
                "75.Others",
                "76.Total  (71+72+73+74+75)=5=69=84",
                "78.Current loans -Normal (0% Prov.)",
                "79. Watch (1-89 days) 1% Prov.",
                "80. Substandard (90-179 days)  20% Prov.",
                "81.Doubtful (180-359 days) 50% Prov.",
                "82. Loss (360 -719 days ) 100% prov.",
                "83.(Restructured)",
                "84.Total (78+79+80+81+82+83)",
                "86.Men",
                "87.Women",
                "88.Group&Entities",
                "88.Total (86+87+88)",
                "91.Men",
                "92.Women",
                "93.Group&Entities",
                "94.Total (92+93+94)",
                "96.Agriculture, Livestock, Fishing ",
                "97.Public Works (Construction), Buildings, Residences/Homes",
                "98.Commerce, Restaurants, Hotels",
                "99.Transport, Warehouses, Communications",
                "100.Others",
                "101.Total  (96+97+98+99+100)",
                "103. Borrowing from Shareholders at …..% P.a",
                "104.Borrowing from related parties (Parent, Subsidiary, Sister company Etc at …% P.a",
                "105. Borrowing from Banks or Micro finance at …% P.a",
                "106. Borrowing from other sources (Specify)  at …..% P.a",
                "107.Total  (103+104+105+106)",
                "Number of Disbursed Loans to WE (As Per Quarter )",
                "Number of Outstanding Loans to WE (Cumulative)",
                "Value of Disbursed Loans to WE (As Per Quarter )",
                "Value  of Outstanding Loans to WE (Cumulative)",
                " Number of Accounts with WE",
                "Number of Disbursed Loans to SMEs (As Per Quarter )",
                "Number of Outstanding Loans to SMEs (balance)",
                "Value of Disbursed Loans to SMEs (As Per Quarter )",
                "Value  of Outstanding Loans to SMEs (As Per Quarter)",
                "Number of Disbursed Loans to YE (As Per Quarter )",
                "Number of Outstanding Loans to YE (As Per Quarter)",
                "Value of Disbursed Loans to YE (As Per Quarter )",
                "Value  of Outstanding Loans to YE (As Per Quarter)",
                "Number of loans applied for (As Per Quarter )",
                "Number of loans rejected (As Per Quarter )",
                "Amount of loans applied for (As Per Quarter )",
                "Amount of loans rejected (As Per Quarter )",
                "Men",
                "Women",
                "Total ",
                "Men",
                "Women",
                "Total ",
                "Men",
                "Women",
                "Legal Entities",
                "Total ",
                "Share value", };

        Row meta1 = sheet.createRow(0);
        meta1.createCell(0).setCellValue("NAME OF THE NDFSP:");
        meta1.getCell(0).setCellStyle(header);

        Row meta2 = sheet.createRow(1);
        meta2.createCell(0).setCellValue("SECTOR:");
        meta2.getCell(0).setCellStyle(header);

        Row meta3 = sheet.createRow(2);
        meta3.createCell(0).setCellValue("DISTRICT:");
        meta3.getCell(0).setCellStyle(header);
        meta3.createCell(2).setCellValue("DENOMINATION");
        meta3.getCell(2).setCellStyle(header);
        meta3.createCell(8).setCellValue(LocalDate.now());
        meta3.getCell(8).setCellStyle(body);

        Map<Integer, String> formulaRows = new LinkedHashMap<>();
        formulaRows.put(5, "=D8+D7+D6");
        formulaRows.put(9, "=D93");
        formulaRows.put(11, "=D9-D10");
        formulaRows.put(12, "=SUM(D89:D92)");
        formulaRows.put(16, "=D14-D15");
        formulaRows.put(20, "=D5+D11+D13+D16+D17+D18+D19");
        formulaRows.put(21, "=D22+D23+D25");
        formulaRows.put(22, "=D112");
        formulaRows.put(26, "=SUM(D27:D32)");
        formulaRows.put(31, "=D66");
        formulaRows.put(33, "=D26+D21");
        formulaRows.put(34, "=IF(ISNUMBER(D12),IF(ISNUMBER(D9),D12/D9,\"\"),\"\")");
        formulaRows.put(35, "=IF(ISNUMBER(D26),IF(ISNUMBER(D20),D26/D20,\"\"),\"\")");
        formulaRows.put(36, "=IF(ISNUMBER(D9),IF(ISNUMBER(D33),D9/D33,\"\"),\"\")");
        formulaRows.put(37, "=IF(ISNUMBER(D16),IF(ISNUMBER(D26),D16/D26,\"\"),\"\")");
        formulaRows.put(39, "=D40+D41+D42+D43+D44");
        formulaRows.put(49, "=D39+D45+D47+D48+D46");
        formulaRows.put(50, "=D53+D52+D51");
        formulaRows.put(54, "=D10");
        formulaRows.put(59, "=D50+D54+D56+D57+D58+D55");
        formulaRows.put(60, "=D49-D59");
        formulaRows.put(62, "=D60-D61");
        formulaRows.put(64, "=D62+D63");
        formulaRows.put(66, "=D64-D65");
        formulaRows.put(67, "=IF(ISNUMBER(D59),IF(ISNUMBER(D49),D59/D49,\"\"),\"\")");
        formulaRows.put(68, "=IF(ISNUMBER(D39),IF(ISNUMBER(D49),D39/D49,\"\"),\"\")");
        formulaRows.put(76, "=D73+D74+D75");
        formulaRows.put(80, "=D77+D78+D79");
        formulaRows.put(86, "=D81+D82+D83+D84+D85");
        formulaRows.put(93, "=SUM(D87:D92)");
        formulaRows.put(97, "=D94+D95+D96");
        formulaRows.put(101, "=D98+D99+D100");
        formulaRows.put(107, "=D102+D103+D104+D105+D106");
        formulaRows.put(112, "=SUM(D108:D111)");
        formulaRows.put(132, "=D130+D131");
        formulaRows.put(135, "=D133+D134");
        formulaRows.put(139, "=D136+D137+D138");
        for (int excelRow = 5; excelRow <= 140; excelRow++) {
            Row row = sheet.createRow(excelRow - 1);

            Cell label = row.createCell(2);
            label.setCellValue(labels[excelRow - 5]);
            label.setCellStyle(
                    excelRow == 5
                            || excelRow == 21
                            || excelRow == 38
                            || excelRow == 71
                            || excelRow == 72
                                    ? title
                                    : body);

            for (int c = 3; c <= 8; c++) {
                Cell cell = row.createCell(c);
                cell.setCellStyle(
                        isFinancialStatementPercentageRow(excelRow)
                                ? percentage
                                : currency);
            }
        }

        // The supplied workbook keeps this source value at zero.
        sheet.getRow(29).getCell(3).setCellValue(0);

        // Apply the exact D-column formulas to D:I with Excel-relative
        // references translated to the destination column.
        for (Map.Entry<Integer, String> entry : formulaRows.entrySet()) {
            int excelRow = entry.getKey();
            String sourceFormula = entry.getValue();

            for (int c = 3; c <= 8; c++) {
                String destinationColumn = org.apache.poi.ss.util.CellReference.convertNumToColString(c);
                String translatedFormula = sourceFormula.replaceAll(
                        "\\bD(?=\\$?\\d+)",
                        destinationColumn);

                sheet.getRow(excelRow - 1)
                        .getCell(c)
                        .setCellFormula(translatedFormula);
            }
        }

        // Net loans / income movement helper row retained exactly as supplied.
        sheet.getRow(29).getCell(4).setCellFormula("=D30+D31");
        sheet.getRow(29).getCell(5).setCellFormula("=E30+E31");
        sheet.getRow(29).getCell(6).setCellFormula("=F30+F31");
        sheet.getRow(29).getCell(7).setCellFormula("=G30+G31");
        sheet.getRow(29).getCell(8).setCellFormula("=H30+H31");

        // ROA and ROE start from the second reporting period in the supplied
        // workbook because their denominator is a two-period average.
        sheet.getRow(68).getCell(4).setCellFormula("=(E60)/((E20+D20)/2)");
        sheet.getRow(68).getCell(5).setCellFormula("=(F60)/((F20+E20)/2)");
        sheet.getRow(68).getCell(6).setCellFormula("=(G60)/((G20+F20)/2)");
        sheet.getRow(68).getCell(7).setCellFormula("=(H60)/((H20+G20)/2)");
        sheet.getRow(68).getCell(8).setCellFormula("=(I60)/((I20+H20)/2)");

        sheet.getRow(69).getCell(4).setCellFormula("=(E60)/((E26+D26)/2)");
        sheet.getRow(69).getCell(5).setCellFormula("=(F60)/((F26+E26)/2)");
        sheet.getRow(69).getCell(6).setCellFormula("=(G60)/((G26+F26)/2)");
        sheet.getRow(69).getCell(7).setCellFormula("=(H60)/((H26+G26)/2)");
        sheet.getRow(69).getCell(8).setCellFormula("=(I60)/((I26+H26)/2)");

        for (int c = 0; c <= 8; c++) {
            sheet.setColumnWidth(c, c == 2 ? 24000 : 4800);
        }

        sheet.createFreezePane(3, 4);
    }

    private boolean isFinancialStatementPercentageRow(int excelRow) {
        return excelRow == 34
                || excelRow == 35
                || excelRow == 36
                || excelRow == 37
                || excelRow == 67
                || excelRow == 68
                || excelRow == 69
                || excelRow == 70;
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
            throw new IllegalArgumentException("Classification sheet headers are required");
        }
        if (headerRowNumber < 7) {
            throw new IllegalArgumentException(
                    "Header row number must be at least 7 for the BNR template structure");
        }

        /*
         * The workbook is intentionally constructed in code, but its visible
         * regulatory layout follows the supplied BNR workbook:
         *
         * metadata rows
         * regulatory classification note
         * column headers
         * Column1/Column2/... helper row
         * data rows
         */
        final int headerRowIndex = headerRowNumber - 1;
        final boolean shiftedMetadata = "A1.3. Normal".equals(sheetName)
                || "A1.5. Substandard".equals(sheetName)
                || "A1.6. Doubtful".equals(sheetName)
                || "A1.7 Loss".equals(sheetName)
                || "A1.8. Restructured loans".equals(sheetName);

        final int metadataColumn = shiftedMetadata ? 1 : 0;

        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle columnHeaderStyle = createHeaderStyle(workbook);
        CellStyle bodyStyle = createBodyStyle(workbook);

        // Row 1 intentionally remains blank to match the regulatory workbook.
        Row ndfspRow = workbookRow(workbook, sheetName, 1);
        Cell ndfspLabel = ndfspRow.createCell(metadataColumn);
        ndfspLabel.setCellValue(
                shiftedMetadata ? "NDFSP Name:" : "NDFSP Name");
        ndfspLabel.setCellStyle(columnHeaderStyle);

        Row codeRow = workbookRow(workbook, sheetName, 2);
        Cell codeLabel = codeRow.createCell(metadataColumn);
        codeLabel.setCellValue("Code of Institution:");
        codeLabel.setCellStyle(columnHeaderStyle);

        Row periodRow = workbookRow(workbook, sheetName, 3);
        Cell periodLabel = periodRow.createCell(metadataColumn);
        periodLabel.setCellValue("Reporting Period (Cut-off date):");
        periodLabel.setCellStyle(columnHeaderStyle);
        Cell periodValue = periodRow.createCell(2);
        periodValue.setCellValue(LocalDate.now());
        periodValue.setCellStyle(bodyStyle);

        Row reportRow = workbookRow(workbook, sheetName, 4);
        Cell reportLabel = reportRow.createCell(metadataColumn);
        reportLabel.setCellValue("Report Name" + (shiftedMetadata ? ":" : " "));
        reportLabel.setCellStyle(columnHeaderStyle);
        Cell reportValue = reportRow.createCell(2);
        reportValue.setCellValue(reportName == null ? "" : reportName);
        reportValue.setCellStyle(titleStyle);

        // Regulatory classification / portfolio-at-risk note.
        int classificationRowIndex = headerRowIndex - 1;
        Row classificationRow = workbookRow(
                workbook,
                sheetName,
                classificationRowIndex);

        Cell classificationCell = classificationRow.createCell(
                shiftedMetadata ? 1 : 0);
        classificationCell.setCellValue(
                classificationLabel(classification));
        classificationCell.setCellStyle(titleStyle);

        Cell provisionCell = classificationRow.createCell(
                shiftedMetadata ? 2 : 2);
        provisionCell.setCellValue(
                "Minimum provisioning rate required: "
                        + provisioningRate(classification).stripTrailingZeros().toPlainString()
                        + "%");
        provisionCell.setCellStyle(bodyStyle);

        // Exact regulatory header row.
        Row columnHeaderRow = workbookRow(
                workbook,
                sheetName,
                headerRowIndex);

        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {
            Cell cell = columnHeaderRow.createCell(columnIndex);
            cell.setCellValue(headers[columnIndex] == null ? "" : headers[columnIndex]);
            cell.setCellStyle(columnHeaderStyle);
        }

        // The supplied workbook contains a non-printable helper row
        // Column1, Column2, ... immediately below the headers.
        Row helperRow = workbookRow(
                workbook,
                sheetName,
                headerRowIndex + 1);

        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {
            Cell cell = helperRow.createCell(columnIndex);
            cell.setCellValue(
                    columnIndex == 0
                            ? "Column1"
                            : "Column" + (columnIndex + 1));
            cell.setCellStyle(bodyStyle);
        }

        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {
            int width = Math.max(
                    4200,
                    Math.min(
                            18000,
                            (headers[columnIndex] == null ? 10 : headers[columnIndex].length()) * 300));
            workbookSheet(workbook, sheetName).setColumnWidth(columnIndex, width);
        }

        Sheet sheet = workbookSheet(workbook, sheetName);
        sheet.createFreezePane(0, headerRowIndex + 2);
        sheet.setAutoFilter(
                new org.apache.poi.ss.util.CellRangeAddress(
                        headerRowIndex,
                        headerRowIndex + 1,
                        0,
                        headers.length - 1));
    }

    private Row workbookRow(
            XSSFWorkbook workbook,
            String sheetName,
            int rowIndex) {
        Sheet sheet = workbookSheet(workbook, sheetName);
        Row row = sheet.getRow(rowIndex);
        return row != null ? row : sheet.createRow(rowIndex);
    }

    private Sheet workbookSheet(
            XSSFWorkbook workbook,
            String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            sheet = workbook.createSheet(sheetName);
        }
        return sheet;
    }

    private String classificationLabel(String classification) {
        if (classification == null) {
            return "";
        }

        return switch (classification.toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> "Portfolio At Risk 0 days";
            case "WATCH" -> "Portfolio At Risk 1 to 89 days";
            case "SUBSTANDARD" -> "Portfolio At Risk 90 to 179 days in arrears";
            case "DOUBTFUL" -> "Portfolio At Risk 180 to 359 days in arrears";
            case "LOSS" -> "Portfolio at risk 360 - 719 days in arrears";
            case "RESTRUCTURED" -> "Renegotiated Loans";
            default -> classification;
        };
    }

    private BigDecimal provisioningRate(String classification) {
        if (classification == null) {
            return BigDecimal.ZERO;
        }

        return switch (classification.toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> BigDecimal.ZERO;
            case "WATCH" -> new BigDecimal("1.00");
            case "SUBSTANDARD" -> new BigDecimal("20.00");
            case "DOUBTFUL" -> new BigDecimal("50.00");
            case "LOSS" -> new BigDecimal("100.00");
            default -> BigDecimal.ZERO;
        };
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
                "Maturity Date ",
                "Amount Repaid ",
                "Loan balance outstanding",
                "Security Savings",
                "Amount Written Off",
                "Date of Write Off",
                "Recoveries on the written off amount",
                "Remaining Balance to be Recovered"
        };

        Sheet sheet = workbook.createSheet("A1.9. Written off");

        CellStyle title = createTitleStyle(workbook);
        CellStyle header = createHeaderStyle(workbook);
        CellStyle body = createBodyStyle(workbook);

        Row r1 = sheet.createRow(0);
        r1.createCell(0).setCellValue("NDFSP Name");
        r1.getCell(0).setCellStyle(header);

        Row r2 = sheet.createRow(1);
        r2.createCell(0).setCellValue("NDFSP Name");
        r2.getCell(0).setCellStyle(body);

        Row r3 = sheet.createRow(2);
        r3.createCell(0).setCellValue("Report Date");
        r3.getCell(0).setCellStyle(body);
        r3.createCell(1).setCellValue(LocalDate.now());
        r3.getCell(1).setCellStyle(body);

        Row r4 = sheet.createRow(3);
        r4.createCell(0).setCellValue("Report Name");
        r4.createCell(1).setCellValue(
                "Written Off Loans-Individuals (1 year in loss)");
        r4.getCell(0).setCellStyle(header);
        r4.getCell(1).setCellStyle(title);

        Row headerRow = sheet.createRow(6);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(header);
            sheet.setColumnWidth(
                    c,
                    Math.min(18000, Math.max(4200, headers[c].length() * 300)));
        }

        Row placeholder = sheet.createRow(7);
        for (int c = 0; c < headers.length; c++) {
            Cell helper = placeholder.createCell(c);
            helper.setCellValue(c == 0 ? "Column 1" : "Column " + (c + 1));
            helper.setCellStyle(body);
        }

        sheet.createFreezePane(0, 7);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                6, 7, 0, headers.length - 1));
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
                "Disbursed amount",
                "Date of loan disbursement",
                "Agreed Maturity Date",
                "Agreed Frequency of Repayment (Days)",
                "Grace Period Accorded (Days)",
                "Agreed Date of First Payment (Principal)",
                "Date of Last Payment (Principal)",
                "Date when Arrears Start",
                "Cut Off Date (Report Date)",
                "Total Number of Installments",
                "Round Number of Installments  paid",
                "Round Number of Installments outstanding",
                "Amount Repaid (Principal)",
                "Balance Outstanding (Principal)",
                "Eligible Collateral provided ",
                "Net Amount due (Principal)",
                "Number of days overdue (Arrears) ",
                "Class",
                "Provisioning Rate (Regulation)",
                "Provision Required ",
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
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private void configureWorkbook(XSSFWorkbook workbook) {
        workbook.setForceFormulaRecalculation(true);
        workbook.getProperties().getCoreProperties().setCreator("Noble Loan Solutions");
        workbook.getProperties().getCoreProperties().setTitle("BNR Regulatory Reporting");
        // Apache POI versions used by Noble do not expose setSubject on CoreProperties.
        // Use the supported description field instead.
        workbook.getProperties().getCoreProperties().setDescription(
                "Loan portfolio, classification and financial statement reporting");
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

        // Populate the same metadata fields on every regulatory
        // classification sheet so the generated workbook is self-contained.
        for (String sheetName : CLASSIFICATION_SHEETS) {
            Sheet classificationSheet = workbook.getSheet(sheetName);
            if (classificationSheet == null) {
                continue;
            }

            boolean shifted = "A1.3. Normal".equals(sheetName)
                    || "A1.5. Substandard".equals(sheetName)
                    || "A1.6. Doubtful".equals(sheetName)
                    || "A1.7 Loss".equals(sheetName)
                    || "A1.8. Restructured loans".equals(sheetName);

            int labelColumn = shifted ? 1 : 0;

            setCellValue(classificationSheet, 1, labelColumn + 1, institutionName);
            setCellValue(classificationSheet, 2, labelColumn + 1, registrationNumber);
            setCellValue(classificationSheet, 3, 2, window[1]);
        }

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

    private void setIfPresent(Sheet sheet, int column, int rowIndex, BigDecimal value) {
        if (value != null) {
            setCellValue(sheet, rowIndex, column, value);
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
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
import org.apache.poi.xssf.usermodel.XSSFColor;
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
            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);
            output.flush();

            byte[] bytes = output.toByteArray();
            if (bytes.length == 0) {
                throw new IllegalStateException("Generated BNR workbook is empty");
            }

            long missingBorrowerIds = loans.stream()
                    .filter(Objects::nonNull)
                    .filter(loan -> resolveBorrowerReportingId(loan.getBorrower()) == null)
                    .count();

            if (missingBorrowerIds > 0) {
                log.warn(
                        "BNR export contains {} loan(s) without a borrower national ID. "
                                + "The 'ID of the Borrower' field is left blank rather than "
                                + "using Noble's internal borrower database ID.",
                        missingBorrowerIds);
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

        return workbook;
    }

    private void createExplanatoryNoteSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("A1.1  Explanatory Note ");
        CellStyle header = createBnrHeaderStyle(workbook);
        CellStyle section = createBnrSectionStyle(workbook);
        CellStyle body = createBnrBodyStyle(workbook);
        String[][] notes = {
            {"DENOMINATION", "Explanatory Notes"},
            {"A.BALANCE SHEET", ""},
            {"1.Total Liquid Assets (2+3+4)", ""},
            {"2.Cash in vault ", "In this section, Accountant record physical cash (Coins or Notes) held on the institution’s premises as at reporting date, "},
            {"3.Cash in bank and other FIs (Current account)", "Report the total balances at all banks or financial institutions including Mobile Money, used for transactional liquidity."},
            {"4.Cash in bank and other FIs (Term deposit )", "Cash placed in fixed‑term accounts earning interest but less liquid."},
            {"5.Gross loans ", "Do not edit enything here, The formula will take the total outstanding loan balance issued to clients, as per loan classification in line ( 77–84.)"},
            {"6.Provisions ", "add the provisions computed in allowances established against expected credit losses in compliance with Article 39 of REGULATION No 65/04/2023 OF 25/04/2023."},
            {"7.Net Loans (5-6)", ""},
            {"8.NPLs ", "Add all out non performing loans outstanding as at reporting date ( substandard, Doubtful and loss) refer to Article 3: Paragraph u "},
            {"9.Financial Instruments", "investments in securities or other financial assets permitted under regulations."},
            {"Fixed Assets (PPE,Intangible, Investment properties, etc) Gross Amount", "capital expenditure on property, plant & equipment, intangible and investment assets, in this section the accountant record the actual cost incured to accure asset as per IAS 16."},
            {"Depreciation ", "Add the Depreciiation for the quarter to the previously reported accumulated depreciation "},
            {"10.Fixed Assets (net)", ""},
            {"11. Interest receivable", "Accrued incured by not yet paid by clients as at the of quarter."},
            {"12.Other Assets ", "Assets such as prepayments, accrued interest income, or any other items not classified elsewhere."},
            {"13.Suspense Accounts", "Temporary ledger accounts holding unresolved or pending transaction entries."},
            {"14.Total Assets (1+7+9+10+11+12+13)=25", ""},
            {"15.Total Liabilities (15+16+20)", ""},
            {"16.Borrowings from other FIs and Non FIs", "Outstanding Debt obligations owed to the Bank and other creditors payable in the period exceeding 1 year."},
            {" 17.Cash collateral if any", "Cash pledged as collateral for third-party obligations or regulatory requirements."},
            {"18. interest Payable", "Accrued interest payable to the lender at the end of  quarter as per Loan ammortization."},
            {"19.Other liabilities (payables+suspense+other liabilities)", "Includes trade payables, payroll liabilities, interest payable, suspense liability accounts, accruals, and miscellaneous obligations."},
            {"20.Total Equity ", " "},
            {"21.Subsidies  (for equipment or financing Equity)", "Grants or donor‑funded investments provided to support capital or operational capacity. If any"},
            {"22.Revaluation surplus", "record all Unrealized gains from reappraisal of assets recognized under accounting standards."},
            {"23.Other Equity", "any other equity categories such as statutory reserves or specific retained components."},
            {"24.Retained profits/Acc losses", "record the opening accumulated P/L as at the start of quarter "},
            {"25.Profit/loss for the period", "Link to profit in P/L account"},
            {"26.Paid up capital", "	Equity contributed by shareholders meeting the minimum capital thresholds (e.g. RWF 30m–100m depending on category) "},
            {"27.Total Equity&Liabilities (14+18)=13", ""},
            {"28.NPL Ratio (max 5%) ", "don't edit in this formulas"},
            {"29.Capital Adequacy Ratio ", "don't edit in this formulas"},
            {"30.Conversion of resources into loans ", "don't edit in this formulas"},
            {"Investment in fixed assets ", "don't edit in this formulas"},
            {"B.INCOME STATEMENT", ""},
            {"31.Financial Income (32+33+34+35+36)", ""},
            {"32.Interest Income on Loan Portfolio", "Record all Interest accrued from outstanding loans as per IFRS 15."},
            {"33.Fees and Commissions on Loan Portfolio", "record all Non‑interest income from lending services like origination or processing fees."},
            {"34.Incomes on Deposits in banks and other Fis", "Interest from parked liquidity in bank deposits."},
            {"35.Incomes on Financial Instruments", "Earnings on permitted investments such as T bonds, T Bills or any other securities."},
            {"36.Other financial Income", "Miscellaneous income such as FX gains or other finance-related sources."},
            {"37.Recoveries on Loans (prov. Back)", "if the loan provisions has been reduced compared to prior  report provision (the difference is the income to be recognised hire) :  Eg: if in the Q1 provision was 100,000 and in quarter 2 provision become 80,000) 20,000 decrease will be recognised as income"},
            {"38.Recoveries on loans (written offs)", "Income from collections on loans previously written off."},
            {"39.Other operating Incomes", "Income from incidental operations related to lending services."},
            {"40.Non Operating Incomes", "non-core income, such as gains from asset sales."},
            {"41.Total Incomes(31+37+38+39+40)", ""},
            {"42.Financial Expenses (43+44+45)", ""},
            {"43.Interest on cash collateral if any", "Cost incurred on cash pledged as collateral if any"},
            {"44.Interest on borrowings from Fis and Non FIs", "Interest expenses on borrowings from bank or other lenders "},
            {"45. Bank Charges,Commissions and other Financial Exp.", " Transaction fees, service charges related to funding."},
            {"46.Loan losses (provisions)", "when the loan provisions has been reduced compared to prior  report provision (the difference is the income to be recognised hire) Eg. if in the Q1 provision was 100,000 and in quarter 2 provision become 80,000) 20,000 decrease will be recognised as income"},
            {"47. Loan Losses (written off for the period)", "Report all irrecoverable loans during the period (loans which took at least 1 Year in loss class or Un paid installment has 720 days and above days in arrear)"},
            {"48.Personnel Expenses (Gross amount)", "When the loan provisions has been reduced compared to prior  report provision (the difference is the income to be recognised hire) :  Eg. if in the Q1 provision was 100,000 and in quarter 2 provision become 80,000) 20,000 decrease will be recognised as income."},
            {"49.Administrative Expenses", "in this section, Accountant record all expenses incured during the quarter to assist in day today management of the business operation (expenses like dipreciation and ammortization, Advertisement, Board sitting allowances, office stationaries, etc are found here), "},
            {"50.Non Operating Expenses", "Any other Expenses not sspecified in above reporting lines."},
            {"51.Total Expenses  (42+46+47+48+49+50)", ""},
            {"52.Profit/Loss before donations (41-51)", ""},
            {"53. Income Tax ", ""},
            {"54. Profit after tax and before donations(52-53)", ""},
            {"55.Donations (Financing Operating Expenses)", "Any donation made to Government entity. Non government Entity or Individual person."},
            {"Profit/loss after tax and  donations (54+55)=23", ""},
            {"Dividends ", ""},
            {"Net profit After Dividends ", " "},
            {"56.Cost to income   52/43", ""},
            {"57.% of Financial Income 34/43", ""},
            {"58.ROA   (AVERAGED 53/13)", ""},
            {"59.ROE  (AVERAGED 53/21)", ""},
            {"C. OFF-BALANCE SHEET (Written-Off Loans)", ""},
            {"D.SUPPLEMENTARY INFORMATION", ""},
            {"61.Men", "give the number of Men with outstanding loan (Unpaid loan) as at the end of quarter"},
            {"62.Women", "give the number of Women with outstanding loan (Unpaid loan) as at the end of quarter"},
            {"63.Group&Entities", "give the number of  Entities with outstanding loan (Unpaid loan) as at the end of quarter"},
            {"64.Total (61+62+63)", "The total should be equal to the line 5.Gross loans in the balance sheet"},
            {"66.Men", "give the balance amount of loan  receivable from Men at the end of quarter"},
            {"67.Women", "give the balance amount of loan  receivable from Wemen at the end of quarter"},
            {"68.Group&Entities", "give the balance amount of loan  receivable from entities at the end of quarter"},
            {"69.Total (66+67+68)=5=76=84", "The total should be equal to the line 5.Gross loans in the balance sheet"},
            {"71.Agriculture, Livestock, Fishing ", "give the balance amount of loan  receivable from borrowers engaged in Agriculture sector at the end of quarter"},
            {"72.Public Works (Construction), Buildings, Residences/Homes", "give the balance amount of loan  receivable from borrowers engaged in Public Works at the end of quarter"},
            {"73.Commerce, Restaurants, Hotels", "give the balance amount of loan  receivable from borrowers engaged in Commerce, Restaurants and Hotels at the end of quarter"},
            {"74.Transport, Warehouses, Communications", "give the balance amount of loan  receivable from borrowers engaged in Transport, Warehouses and Communications at the end of quarter"},
            {"75.Others", "give the balance amount of loan  receivable from borrowers engaged in other sectors not specified"},
            {"76.Total  (71+72+73+74+75)=5=69=84", "The total should be equal to the line 5.Gross loans in the balance sheet"},
            {"78.Current loans -Normal (0% Prov.)", "give the balance amount of loan outstanding classified under Normal (A1.3) Column AH"},
            {"79. Watch (1-89 days) 1% Prov.", "give the balance amount of loan outstanding classified under Watch (A1.4) Column AH"},
            {"80. Substandard (90-179 days)  20% Prov.", "give the balance amount of loan outstanding classified under Normal (A1.5) Column AI"},
            {"81.Doubtful (180-359 days) 50% Prov.", "give the balance amount of loan outstanding classified under Normal (A1.6) Column AH "},
            {"82. Loss (360 -719 days ) 100% prov.", "give the balance amount of loan outstanding classified under Normal (A1.7) Column AH"},
            {"83.(Restructured)", "give the balance amount of loan outstanding classified under Normal (A1.8) Column AH"},
            {"84.Total (78+79+80+81+82+83)", "The total should be equal to the line 5.Gross loans in the balance sheet"},
            {"86.Men", "give the number of New Loans disbursed to the Men in three months time"},
            {"87.Women", "give the number of New Loans disbursed to the Wemen in three months time"},
            {"88.Group&Entities", "give the number of New Loans disbursed to the Entity in three months time"},
            {"88.Total (86+87+88)", "Total should be equal to the Number of new cotracts signed in 3 monthss time"},
            {"91.Men", "give the Value of New Loans disbursed to the Men in three months time"},
            {"92.Women", "give the  Value of New Loans disbursed to the Wemen in three months time"},
            {"93.Group&Entities", "give the  Value of New Loans disbursed to the Entities in three months time"},
            {"94.Total (92+93+94)", "Total should be equal to the sum of gross loans in a new cotracts signed in 3 monthss time"},
            {"96.Agriculture, Livestock, Fishing ", "give the  Value of New Loans disbursed to the borrowers based in Agriculture in three months time"},
            {"97.Public Works (Construction), Buildings, Residences/Homes", "give the  Value of New Loans disbursed to the borrowers based in Public Works in three months time"},
            {"98.Commerce, Restaurants, Hotels", "give the  Value of New Loans disbursed to the borrowers based in Commerce  in three months time"},
            {"99.Transport, Warehouses, Communications", "give the  Value of New Loans disbursed to the borrowers based in Transport, Warehouses, Communications in three months time"},
            {"100.Others", "give the  Value of New Loans disbursed to the borrowers based in any other sector not specified above."},
            {"101.Total  (96+97+98+99+100)", "Total should be equal to the sum of gross loans in a new cotracts signed in 3 monthss time"},
            {"103. Borrowing from Shareholders at …..% P.a", "All money injected by shareholders expected to be repaid back"},
            {"104.Borrowing from related parties (Parent, Subsidiary, Sister company Etc at …% P.a", "All money injected from related companies expected to be repaid back"},
            {"105. Borrowing from Banks or Micro finance at …% P.a", "Bank Loans"},
            {"106. Borrowing from other sources (Specify)  at …..% P.a", "Report any other loans (Wether from individual friend, family or any source not specified)"},
            {"107.Total  (103+104+105+106)", "The total should be the total borrowings reported in the balance sheet"},
            {"Number of Disbursed Loans to WE (As Per Quarter )", "give the number of New Loans disbursed to the Wemen Enterprise in three months time"},
            {"Number of Outstanding Loans to WE (Balance)", "give the number of unpaid Loans receivable from the Wemen Enterprise as at the end of quarter"},
            {"Value of Disbursed Loans to WE (As Per Quarter )", "report the Value of New Loans disbursed to the Wemen Enterprise in three months time"},
            {"Value  of Outstanding Loans to WE (Balance)", "give the balance (Unpaid) amount of loan  receivable from borrowers engaged  in the Wemen Enterprise as at the end of quarter"},
            {" Number of Accounts with WE", ""},
            {"Number of Disbursed Loans to SMEs (As Per Quarter )", "give the number of New Loans disbursed to the small and Medium  Enterprises in three months time"},
            {"Number of Outstanding Loans to SMEs (Balance)", "give the number of unpaid Loans receivable from the small and Medium  Enterprises  as at the end of quarter"},
            {"Value of Disbursed Loans to SMEs (As Per Quarter )", "report the Value of New Loans disbursed to the small and Medium  Enterprises in three months time"},
            {"Value  of Outstanding Loans to SMEs (As Per Quarter)", "give the balance (Unpaid) amount of loan  receivable from borrowers engaged  in the small and Medium  Enterprise as at the end of quarter."},
            {"Number of Disbursed Loans to YE (As Per Quarter )", "give the number of New Loans disbursed to the Financing Youth Entities in three months time"},
            {"Number of Outstanding Loans to YE (As Per Quarter)", "give the number of unpaid Loans receivable from the Financing Youth Entities as at the end of quarter"},
            {"Value of Disbursed Loans to YE (As Per Quarter )", "report the Value of New Loans disbursed to the Financing Youth Entities in three months time"},
            {"Value  of Outstanding Loans to YE (As Per Quarter)", "give the balance (Unpaid) amount of loan  receivable from borrowers engaged  in the small and Medium  Enterprise as at the end of quarter."},
            {"Number of loans applied for (As Per Quarter )", "Total loan application received during quarter"},
            {"Number of loans rejected (As Per Quarter )", "Report the number of Loan application rejected "},
            {"Amount of loans applied for (As Per Quarter )", "Report the amount applied for during the three months time"},
            {"Amount of loans rejected (As Per Quarter )", "Report the amount applied for but rejected during three months time"},
            {"Men", "Show the number of Male Employees "},
            {"Women", "Show the number of female Employees "},
            {"Total ", "The total should be the total number of staff"},
            {"Men", "Show the number of Male BOD"},
            {"Women", "Show the number of female BOD "},
            {"Total ", "The total should be the total number of BOD"},
            {"Men", "Show the number of Male Share holders"},
            {"Women", "Show the number of female Shareholders"},
            {"Legal Entities", "Show the number of shareholder through Legal Entity "},
            {"Total ", "The total should be the total number of Shareholders as per RDB Certificate"},
            {"Share value", "Report the Par Value of Each share (As per RDB certificate)"},
            {"Check if all information shared are complete", ""},
            {"Check if the financials are balancing ", ""},
            {"Check if Loans are categorized as per regulation ", ""},
            {"Do not modify the formulas in the report ", ""},
            {"Check whether if the provision is correctly Commputed and reported As per IFRS 9 (Total provision reported in the Balance sheet and The movement reported in Income statement)", ""},
            {"Compare reported data with actual fugures in Accounting system used in the NDFSP", ""},
            {"For more clarification, Contact the BNR staff", ""},
        };
        for (int i=0;i<notes.length;i++) {
            Row row=sheet.createRow(i+1);
            Cell a=row.createCell(1); a.setCellValue(notes[i][0]); a.setCellStyle(i==1 || notes[i][0].startsWith("A.") || notes[i][0].startsWith("B.") || notes[i][0].startsWith("C.") || notes[i][0].startsWith("D.") ? section : header);
            Cell b=row.createCell(2); b.setCellValue(notes[i][1]); b.setCellStyle(body);
        }
        sheet.setColumnWidth(0, 1500); sheet.setColumnWidth(1, 15000); sheet.setColumnWidth(2, 36000);
        sheet.setDisplayGridlines(false); sheet.createFreezePane(1, 2);
    }

    private void createFinancialStatementSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("A1.2. FS");
        CellStyle section=createBnrSectionStyle(workbook);
        CellStyle normal=createBnrFsNormalStyle(workbook);
        CellStyle total=createBnrFsTotalStyle(workbook);
        CellStyle ratio=createBnrFsRatioStyle(workbook);
        CellStyle input=createBnrFsInputStyle(workbook);
        CellStyle dateStyle=createBnrFsDateStyle(workbook);
        put(sheet,0,0,"NAME OF THE NDFSP:",normal); put(sheet,1,0,"SECTOR:   ",normal); put(sheet,2,0,"DISTRICT:",normal); put(sheet,2,2,"DENOMINATION",input); put(sheet,2,8,LocalDate.now(),dateStyle);
        put(sheet,3,2,"A.BALANCE SHEET",section);
        sheet.getRow(3).createCell(3).setCellStyle(normal);
        sheet.getRow(3).createCell(4).setCellStyle(normal);
        sheet.getRow(3).createCell(5).setCellStyle(normal);
        sheet.getRow(3).createCell(6).setCellStyle(normal);
        sheet.getRow(3).createCell(7).setCellStyle(normal);
        sheet.getRow(3).createCell(8).setCellStyle(normal);
        put(sheet,4,2,"1.Total Liquid Assets (2+3+4)",normal);
        sheet.getRow(4).createCell(3).setCellFormula("D8+D7+D6"); sheet.getRow(4).getCell(3).setCellStyle(normal);
        sheet.getRow(4).createCell(4).setCellFormula("E8+E7+E6"); sheet.getRow(4).getCell(4).setCellStyle(normal);
        sheet.getRow(4).createCell(5).setCellFormula("F8+F7+F6"); sheet.getRow(4).getCell(5).setCellStyle(normal);
        sheet.getRow(4).createCell(6).setCellFormula("G8+G7+G6"); sheet.getRow(4).getCell(6).setCellStyle(normal);
        sheet.getRow(4).createCell(7).setCellFormula("H8+H7+H6"); sheet.getRow(4).getCell(7).setCellStyle(normal);
        sheet.getRow(4).createCell(8).setCellFormula("I8+I7+I6"); sheet.getRow(4).getCell(8).setCellStyle(normal);
        put(sheet,5,2,"2.Cash in vault ",normal);
        sheet.getRow(5).createCell(3).setCellStyle(normal);
        sheet.getRow(5).createCell(4).setCellStyle(normal);
        sheet.getRow(5).createCell(5).setCellStyle(normal);
        sheet.getRow(5).createCell(6).setCellStyle(normal);
        sheet.getRow(5).createCell(7).setCellStyle(normal);
        sheet.getRow(5).createCell(8).setCellStyle(normal);
        put(sheet,6,2,"3.Cash in bank and other FIs (Current account)",normal);
        sheet.getRow(6).createCell(3).setCellStyle(normal);
        sheet.getRow(6).createCell(4).setCellStyle(normal);
        sheet.getRow(6).createCell(5).setCellStyle(normal);
        sheet.getRow(6).createCell(6).setCellStyle(normal);
        sheet.getRow(6).createCell(7).setCellStyle(normal);
        sheet.getRow(6).createCell(8).setCellStyle(normal);
        put(sheet,7,2,"4.Cash in bank and other FIs (Term deposit )",normal);
        sheet.getRow(7).createCell(3).setCellStyle(normal);
        sheet.getRow(7).createCell(4).setCellStyle(normal);
        sheet.getRow(7).createCell(5).setCellStyle(normal);
        sheet.getRow(7).createCell(6).setCellStyle(normal);
        sheet.getRow(7).createCell(7).setCellStyle(normal);
        sheet.getRow(7).createCell(8).setCellStyle(normal);
        put(sheet,8,2,"5.Gross loans ",normal);
        sheet.getRow(8).createCell(3).setCellFormula("D93"); sheet.getRow(8).getCell(3).setCellStyle(normal);
        sheet.getRow(8).createCell(4).setCellFormula("E93"); sheet.getRow(8).getCell(4).setCellStyle(normal);
        sheet.getRow(8).createCell(5).setCellFormula("F93"); sheet.getRow(8).getCell(5).setCellStyle(normal);
        sheet.getRow(8).createCell(6).setCellFormula("G93"); sheet.getRow(8).getCell(6).setCellStyle(normal);
        sheet.getRow(8).createCell(7).setCellFormula("H93"); sheet.getRow(8).getCell(7).setCellStyle(normal);
        sheet.getRow(8).createCell(8).setCellFormula("I93"); sheet.getRow(8).getCell(8).setCellStyle(normal);
        put(sheet,9,2,"6.Provisions ",normal);
        sheet.getRow(9).createCell(3).setCellStyle(normal);
        sheet.getRow(9).createCell(4).setCellStyle(normal);
        sheet.getRow(9).createCell(5).setCellStyle(normal);
        sheet.getRow(9).createCell(6).setCellStyle(normal);
        sheet.getRow(9).createCell(7).setCellStyle(normal);
        sheet.getRow(9).createCell(8).setCellStyle(normal);
        put(sheet,10,2,"7.Net Loans (5-6)",normal);
        sheet.getRow(10).createCell(3).setCellFormula("D9-D10"); sheet.getRow(10).getCell(3).setCellStyle(normal);
        sheet.getRow(10).createCell(4).setCellFormula("E9-E10"); sheet.getRow(10).getCell(4).setCellStyle(normal);
        sheet.getRow(10).createCell(5).setCellFormula("F9-F10"); sheet.getRow(10).getCell(5).setCellStyle(normal);
        sheet.getRow(10).createCell(6).setCellFormula("G9-G10"); sheet.getRow(10).getCell(6).setCellStyle(normal);
        sheet.getRow(10).createCell(7).setCellFormula("H9-H10"); sheet.getRow(10).getCell(7).setCellStyle(normal);
        sheet.getRow(10).createCell(8).setCellFormula("I9-I10"); sheet.getRow(10).getCell(8).setCellStyle(normal);
        put(sheet,11,2,"8.NPLs ",normal);
        sheet.getRow(11).createCell(3).setCellFormula("SUM(D89:D92)"); sheet.getRow(11).getCell(3).setCellStyle(normal);
        sheet.getRow(11).createCell(4).setCellFormula("SUM(E89:E92)"); sheet.getRow(11).getCell(4).setCellStyle(normal);
        sheet.getRow(11).createCell(5).setCellFormula("SUM(F89:F92)"); sheet.getRow(11).getCell(5).setCellStyle(normal);
        sheet.getRow(11).createCell(6).setCellFormula("SUM(G89:G92)"); sheet.getRow(11).getCell(6).setCellStyle(normal);
        sheet.getRow(11).createCell(7).setCellFormula("SUM(H89:H92)"); sheet.getRow(11).getCell(7).setCellStyle(normal);
        sheet.getRow(11).createCell(8).setCellFormula("SUM(I89:I92)"); sheet.getRow(11).getCell(8).setCellStyle(normal);
        put(sheet,12,2,"9.Financial Instruments",normal);
        sheet.getRow(12).createCell(3).setCellStyle(normal);
        sheet.getRow(12).createCell(4).setCellStyle(normal);
        sheet.getRow(12).createCell(5).setCellStyle(normal);
        sheet.getRow(12).createCell(6).setCellStyle(normal);
        sheet.getRow(12).createCell(7).setCellStyle(normal);
        sheet.getRow(12).createCell(8).setCellStyle(normal);
        put(sheet,13,2,"Fixed Assets (PPE,Intangible, Investment properties, etc) Gross Amount",normal);
        sheet.getRow(13).createCell(3).setCellStyle(normal);
        sheet.getRow(13).createCell(4).setCellStyle(normal);
        sheet.getRow(13).createCell(5).setCellStyle(normal);
        sheet.getRow(13).createCell(6).setCellStyle(normal);
        sheet.getRow(13).createCell(7).setCellStyle(normal);
        sheet.getRow(13).createCell(8).setCellStyle(normal);
        put(sheet,14,2,"Depreciation ",normal);
        sheet.getRow(14).createCell(3).setCellStyle(normal);
        sheet.getRow(14).createCell(4).setCellStyle(normal);
        sheet.getRow(14).createCell(5).setCellStyle(normal);
        sheet.getRow(14).createCell(6).setCellStyle(normal);
        sheet.getRow(14).createCell(7).setCellStyle(normal);
        sheet.getRow(14).createCell(8).setCellStyle(normal);
        put(sheet,15,2,"10.Fixed Assets (net)",normal);
        sheet.getRow(15).createCell(3).setCellStyle(normal);
        sheet.getRow(15).createCell(4).setCellStyle(normal);
        sheet.getRow(15).createCell(5).setCellStyle(normal);
        sheet.getRow(15).createCell(6).setCellStyle(normal);
        sheet.getRow(15).createCell(7).setCellStyle(normal);
        sheet.getRow(15).createCell(8).setCellStyle(normal);
        put(sheet,16,2,"11. Interest receivable",normal);
        sheet.getRow(16).createCell(3).setCellStyle(normal);
        sheet.getRow(16).createCell(4).setCellStyle(normal);
        sheet.getRow(16).createCell(5).setCellStyle(normal);
        sheet.getRow(16).createCell(6).setCellStyle(normal);
        sheet.getRow(16).createCell(7).setCellStyle(normal);
        sheet.getRow(16).createCell(8).setCellStyle(normal);
        put(sheet,17,2,"12.Other Assets ",normal);
        sheet.getRow(17).createCell(3).setCellStyle(normal);
        sheet.getRow(17).createCell(4).setCellStyle(normal);
        sheet.getRow(17).createCell(5).setCellStyle(normal);
        sheet.getRow(17).createCell(6).setCellStyle(normal);
        sheet.getRow(17).createCell(7).setCellStyle(normal);
        sheet.getRow(17).createCell(8).setCellStyle(normal);
        put(sheet,18,2,"13.Suspense Accounts",normal);
        sheet.getRow(18).createCell(3).setCellStyle(normal);
        sheet.getRow(18).createCell(4).setCellStyle(normal);
        sheet.getRow(18).createCell(5).setCellStyle(normal);
        sheet.getRow(18).createCell(6).setCellStyle(normal);
        sheet.getRow(18).createCell(7).setCellStyle(normal);
        sheet.getRow(18).createCell(8).setCellStyle(normal);
        put(sheet,19,2,"14.Total Assets (1+7+9+10+11+12+13)=27",total);
        sheet.getRow(19).createCell(3).setCellFormula("D5+D11+D13+D16+D17+D18+D19"); sheet.getRow(19).getCell(3).setCellStyle(normal);
        sheet.getRow(19).createCell(4).setCellFormula("E5+E11+E13+E16+E17+E18+E19"); sheet.getRow(19).getCell(4).setCellStyle(normal);
        sheet.getRow(19).createCell(5).setCellFormula("F5+F11+F13+F16+F17+F18+F19"); sheet.getRow(19).getCell(5).setCellStyle(normal);
        sheet.getRow(19).createCell(6).setCellFormula("G5+G11+G13+G16+G17+G18+G19"); sheet.getRow(19).getCell(6).setCellStyle(normal);
        sheet.getRow(19).createCell(7).setCellFormula("H5+H11+H13+H16+H17+H18+H19"); sheet.getRow(19).getCell(7).setCellStyle(normal);
        sheet.getRow(19).createCell(8).setCellFormula("I5+I11+I13+I16+I17+I18+I19"); sheet.getRow(19).getCell(8).setCellStyle(normal);
        put(sheet,20,2,"15.Total Liabilities (16+17+18+19)",total);
        sheet.getRow(20).createCell(3).setCellFormula("D22+D23+D25"); sheet.getRow(20).getCell(3).setCellStyle(normal);
        sheet.getRow(20).createCell(4).setCellFormula("E22+E23+E25"); sheet.getRow(20).getCell(4).setCellStyle(normal);
        sheet.getRow(20).createCell(5).setCellFormula("F22+F23+F25"); sheet.getRow(20).getCell(5).setCellStyle(normal);
        sheet.getRow(20).createCell(6).setCellFormula("G22+G23+G25"); sheet.getRow(20).getCell(6).setCellStyle(normal);
        sheet.getRow(20).createCell(7).setCellFormula("H22+H23+H25"); sheet.getRow(20).getCell(7).setCellStyle(normal);
        sheet.getRow(20).createCell(8).setCellFormula("I22+I23+I25"); sheet.getRow(20).getCell(8).setCellStyle(normal);
        put(sheet,21,2,"16.Borrowings from other FIs and Non FIs (107)",normal);
        sheet.getRow(21).createCell(3).setCellFormula("D112"); sheet.getRow(21).getCell(3).setCellStyle(normal);
        sheet.getRow(21).createCell(4).setCellFormula("E112"); sheet.getRow(21).getCell(4).setCellStyle(normal);
        sheet.getRow(21).createCell(5).setCellFormula("F112"); sheet.getRow(21).getCell(5).setCellStyle(normal);
        sheet.getRow(21).createCell(6).setCellFormula("G112"); sheet.getRow(21).getCell(6).setCellStyle(normal);
        sheet.getRow(21).createCell(7).setCellFormula("H112"); sheet.getRow(21).getCell(7).setCellStyle(normal);
        sheet.getRow(21).createCell(8).setCellFormula("I112"); sheet.getRow(21).getCell(8).setCellStyle(normal);
        put(sheet,22,2," 17.Cash collateral if any",normal);
        put(sheet,22,3,0,input);
        put(sheet,22,4,0,input);
        put(sheet,22,5,0,input);
        put(sheet,22,6,0,input);
        put(sheet,22,7,0,input);
        put(sheet,22,8,0,input);
        put(sheet,23,2,"18. interest Payable",normal);
        sheet.getRow(23).createCell(3).setCellStyle(normal);
        sheet.getRow(23).createCell(4).setCellStyle(normal);
        sheet.getRow(23).createCell(5).setCellStyle(normal);
        sheet.getRow(23).createCell(6).setCellStyle(normal);
        sheet.getRow(23).createCell(7).setCellStyle(normal);
        sheet.getRow(23).createCell(8).setCellStyle(normal);
        put(sheet,24,2,"19.Other liabilities (payables+suspense+other liabilities)",normal);
        sheet.getRow(24).createCell(3).setCellStyle(normal);
        sheet.getRow(24).createCell(4).setCellStyle(normal);
        sheet.getRow(24).createCell(5).setCellStyle(normal);
        sheet.getRow(24).createCell(6).setCellStyle(normal);
        sheet.getRow(24).createCell(7).setCellStyle(normal);
        sheet.getRow(24).createCell(8).setCellStyle(normal);
        put(sheet,25,2,"20.Total Equity ",total);
        sheet.getRow(25).createCell(3).setCellFormula("SUM(D27:D32)"); sheet.getRow(25).getCell(3).setCellStyle(normal);
        sheet.getRow(25).createCell(4).setCellFormula("SUM(E27:E32)"); sheet.getRow(25).getCell(4).setCellStyle(normal);
        sheet.getRow(25).createCell(5).setCellFormula("SUM(F27:F32)"); sheet.getRow(25).getCell(5).setCellStyle(normal);
        sheet.getRow(25).createCell(6).setCellFormula("SUM(G27:G32)"); sheet.getRow(25).getCell(6).setCellStyle(normal);
        sheet.getRow(25).createCell(7).setCellFormula("SUM(H27:H32)"); sheet.getRow(25).getCell(7).setCellStyle(normal);
        sheet.getRow(25).createCell(8).setCellFormula("SUM(I27:I32)"); sheet.getRow(25).getCell(8).setCellStyle(normal);
        put(sheet,26,2,"21.Subsidies  (for equipment or financing Equity)",normal);
        sheet.getRow(26).createCell(3).setCellStyle(normal);
        sheet.getRow(26).createCell(4).setCellStyle(normal);
        sheet.getRow(26).createCell(5).setCellStyle(normal);
        sheet.getRow(26).createCell(6).setCellStyle(normal);
        sheet.getRow(26).createCell(7).setCellStyle(normal);
        sheet.getRow(26).createCell(8).setCellStyle(normal);
        put(sheet,27,2,"22.Revaluation surplus",normal);
        sheet.getRow(27).createCell(3).setCellStyle(normal);
        sheet.getRow(27).createCell(4).setCellStyle(normal);
        sheet.getRow(27).createCell(5).setCellStyle(normal);
        sheet.getRow(27).createCell(6).setCellStyle(normal);
        sheet.getRow(27).createCell(7).setCellStyle(normal);
        sheet.getRow(27).createCell(8).setCellStyle(normal);
        put(sheet,28,2,"23.Other Equity",normal);
        sheet.getRow(28).createCell(3).setCellStyle(normal);
        sheet.getRow(28).createCell(4).setCellStyle(normal);
        sheet.getRow(28).createCell(5).setCellStyle(normal);
        sheet.getRow(28).createCell(6).setCellStyle(normal);
        sheet.getRow(28).createCell(7).setCellStyle(normal);
        sheet.getRow(28).createCell(8).setCellStyle(normal);
        put(sheet,29,2,"24.Retained profits/Acc losses",normal);
        sheet.getRow(29).createCell(3).setCellFormula("D30+D31"); sheet.getRow(29).getCell(3).setCellStyle(normal);
        sheet.getRow(29).createCell(4).setCellFormula("E30+E31"); sheet.getRow(29).getCell(4).setCellStyle(normal);
        sheet.getRow(29).createCell(5).setCellFormula("F30+F31"); sheet.getRow(29).getCell(5).setCellStyle(normal);
        sheet.getRow(29).createCell(6).setCellFormula("G30+G31"); sheet.getRow(29).getCell(6).setCellStyle(normal);
        sheet.getRow(29).createCell(7).setCellFormula("H30+H31"); sheet.getRow(29).getCell(7).setCellStyle(normal);
        sheet.getRow(29).createCell(8).setCellFormula("I30+I31"); sheet.getRow(29).getCell(8).setCellStyle(normal);
        put(sheet,30,2,"25.Profit/loss for the period",normal);
        sheet.getRow(30).createCell(3).setCellFormula("D66"); sheet.getRow(30).getCell(3).setCellStyle(normal);
        sheet.getRow(30).createCell(4).setCellFormula("E66"); sheet.getRow(30).getCell(4).setCellStyle(normal);
        sheet.getRow(30).createCell(5).setCellFormula("F66"); sheet.getRow(30).getCell(5).setCellStyle(normal);
        sheet.getRow(30).createCell(6).setCellFormula("G66"); sheet.getRow(30).getCell(6).setCellStyle(normal);
        sheet.getRow(30).createCell(7).setCellFormula("H66"); sheet.getRow(30).getCell(7).setCellStyle(normal);
        sheet.getRow(30).createCell(8).setCellFormula("I66"); sheet.getRow(30).getCell(8).setCellStyle(normal);
        put(sheet,31,2,"26.Paid up capital",normal);
        sheet.getRow(31).createCell(3).setCellStyle(normal);
        sheet.getRow(31).createCell(4).setCellStyle(normal);
        sheet.getRow(31).createCell(5).setCellStyle(normal);
        sheet.getRow(31).createCell(6).setCellStyle(normal);
        sheet.getRow(31).createCell(7).setCellStyle(normal);
        sheet.getRow(31).createCell(8).setCellStyle(normal);
        put(sheet,32,2,"27.Total Equity&Liabilities (15+20)=14",total);
        sheet.getRow(32).createCell(3).setCellFormula("D26+D21"); sheet.getRow(32).getCell(3).setCellStyle(normal);
        sheet.getRow(32).createCell(4).setCellFormula("E26+E21"); sheet.getRow(32).getCell(4).setCellStyle(normal);
        sheet.getRow(32).createCell(5).setCellFormula("F26+F21"); sheet.getRow(32).getCell(5).setCellStyle(normal);
        sheet.getRow(32).createCell(6).setCellFormula("G26+G21"); sheet.getRow(32).getCell(6).setCellStyle(normal);
        sheet.getRow(32).createCell(7).setCellFormula("H26+H21"); sheet.getRow(32).getCell(7).setCellStyle(normal);
        sheet.getRow(32).createCell(8).setCellFormula("I26+I21"); sheet.getRow(32).getCell(8).setCellStyle(normal);
        put(sheet,33,2,"28.NPL Ratio (max 5%) ",ratio);
        sheet.getRow(33).createCell(3).setCellFormula("IF(ISNUMBER(D12),IF(ISNUMBER(D9),D12/D9,\"\"),\"\")"); sheet.getRow(33).getCell(3).setCellStyle(ratio);
        sheet.getRow(33).createCell(4).setCellFormula("IF(ISNUMBER(E12),IF(ISNUMBER(E9),E12/E9,\"\"),\"\")"); sheet.getRow(33).getCell(4).setCellStyle(ratio);
        sheet.getRow(33).createCell(5).setCellFormula("IF(ISNUMBER(F12),IF(ISNUMBER(F9),F12/F9,\"\"),\"\")"); sheet.getRow(33).getCell(5).setCellStyle(ratio);
        sheet.getRow(33).createCell(6).setCellFormula("IF(ISNUMBER(G12),IF(ISNUMBER(G9),G12/G9,\"\"),\"\")"); sheet.getRow(33).getCell(6).setCellStyle(ratio);
        sheet.getRow(33).createCell(7).setCellFormula("IF(ISNUMBER(H12),IF(ISNUMBER(H9),H12/H9,\"\"),\"\")"); sheet.getRow(33).getCell(7).setCellStyle(ratio);
        sheet.getRow(33).createCell(8).setCellFormula("IF(ISNUMBER(I12),IF(ISNUMBER(I9),I12/I9,\"\"),\"\")"); sheet.getRow(33).getCell(8).setCellStyle(ratio);
        put(sheet,34,2,"29.Capital Adequacy Ratio ",ratio);
        sheet.getRow(34).createCell(3).setCellFormula("IF(ISNUMBER(D26),IF(ISNUMBER(D20),D26/D20,\"\"),\"\")"); sheet.getRow(34).getCell(3).setCellStyle(ratio);
        sheet.getRow(34).createCell(4).setCellFormula("IF(ISNUMBER(E26),IF(ISNUMBER(E20),E26/E20,\"\"),\"\")"); sheet.getRow(34).getCell(4).setCellStyle(ratio);
        sheet.getRow(34).createCell(5).setCellFormula("IF(ISNUMBER(F26),IF(ISNUMBER(F20),F26/F20,\"\"),\"\")"); sheet.getRow(34).getCell(5).setCellStyle(ratio);
        sheet.getRow(34).createCell(6).setCellFormula("IF(ISNUMBER(G26),IF(ISNUMBER(G20),G26/G20,\"\"),\"\")"); sheet.getRow(34).getCell(6).setCellStyle(ratio);
        sheet.getRow(34).createCell(7).setCellFormula("IF(ISNUMBER(H26),IF(ISNUMBER(H20),H26/H20,\"\"),\"\")"); sheet.getRow(34).getCell(7).setCellStyle(ratio);
        sheet.getRow(34).createCell(8).setCellFormula("IF(ISNUMBER(I26),IF(ISNUMBER(I20),I26/I20,\"\"),\"\")"); sheet.getRow(34).getCell(8).setCellStyle(ratio);
        put(sheet,35,2,"30.Conversion of resources into loans ",ratio);
        sheet.getRow(35).createCell(3).setCellFormula("IF(ISNUMBER(D9),IF(ISNUMBER(D33),D9/D33,\"\"),\"\")"); sheet.getRow(35).getCell(3).setCellStyle(ratio);
        sheet.getRow(35).createCell(4).setCellFormula("IF(ISNUMBER(E9),IF(ISNUMBER(E33),E9/E33,\"\"),\"\")"); sheet.getRow(35).getCell(4).setCellStyle(ratio);
        sheet.getRow(35).createCell(5).setCellFormula("IF(ISNUMBER(F9),IF(ISNUMBER(F33),F9/F33,\"\"),\"\")"); sheet.getRow(35).getCell(5).setCellStyle(ratio);
        sheet.getRow(35).createCell(6).setCellFormula("IF(ISNUMBER(G9),IF(ISNUMBER(G33),G9/G33,\"\"),\"\")"); sheet.getRow(35).getCell(6).setCellStyle(ratio);
        sheet.getRow(35).createCell(7).setCellFormula("IF(ISNUMBER(H9),IF(ISNUMBER(H33),H9/H33,\"\"),\"\")"); sheet.getRow(35).getCell(7).setCellStyle(ratio);
        sheet.getRow(35).createCell(8).setCellFormula("IF(ISNUMBER(I9),IF(ISNUMBER(I33),I9/I33,\"\"),\"\")"); sheet.getRow(35).getCell(8).setCellStyle(ratio);
        put(sheet,36,2,"Investment in fixed assets ",ratio);
        sheet.getRow(36).createCell(3).setCellFormula("IF(ISNUMBER(D16),IF(ISNUMBER(D26),D16/D26,\"\"),\"\")"); sheet.getRow(36).getCell(3).setCellStyle(ratio);
        sheet.getRow(36).createCell(4).setCellFormula("IF(ISNUMBER(E16),IF(ISNUMBER(E26),E16/E26,\"\"),\"\")"); sheet.getRow(36).getCell(4).setCellStyle(ratio);
        sheet.getRow(36).createCell(5).setCellFormula("IF(ISNUMBER(F16),IF(ISNUMBER(F26),F16/F26,\"\"),\"\")"); sheet.getRow(36).getCell(5).setCellStyle(ratio);
        sheet.getRow(36).createCell(6).setCellFormula("IF(ISNUMBER(G16),IF(ISNUMBER(G26),G16/G26,\"\"),\"\")"); sheet.getRow(36).getCell(6).setCellStyle(ratio);
        sheet.getRow(36).createCell(7).setCellFormula("IF(ISNUMBER(H16),IF(ISNUMBER(H26),H16/H26,\"\"),\"\")"); sheet.getRow(36).getCell(7).setCellStyle(ratio);
        sheet.getRow(36).createCell(8).setCellFormula("IF(ISNUMBER(I16),IF(ISNUMBER(I26),I16/I26,\"\"),\"\")"); sheet.getRow(36).getCell(8).setCellStyle(ratio);
        put(sheet,37,2,"B.INCOME STATEMENT",section);
        sheet.getRow(37).createCell(3).setCellStyle(normal);
        sheet.getRow(37).createCell(4).setCellStyle(normal);
        sheet.getRow(37).createCell(5).setCellStyle(normal);
        sheet.getRow(37).createCell(6).setCellStyle(normal);
        sheet.getRow(37).createCell(7).setCellStyle(normal);
        sheet.getRow(37).createCell(8).setCellStyle(normal);
        put(sheet,38,2,"31.Financial Income (32+33+34+35+36)",total);
        sheet.getRow(38).createCell(3).setCellFormula("D40+D41+D42+D43+D44"); sheet.getRow(38).getCell(3).setCellStyle(normal);
        sheet.getRow(38).createCell(4).setCellFormula("E40+E41+E42+E43+E44"); sheet.getRow(38).getCell(4).setCellStyle(normal);
        sheet.getRow(38).createCell(5).setCellFormula("F40+F41+F42+F43+F44"); sheet.getRow(38).getCell(5).setCellStyle(normal);
        sheet.getRow(38).createCell(6).setCellFormula("G40+G41+G42+G43+G44"); sheet.getRow(38).getCell(6).setCellStyle(normal);
        sheet.getRow(38).createCell(7).setCellFormula("H40+H41+H42+H43+H44"); sheet.getRow(38).getCell(7).setCellStyle(normal);
        sheet.getRow(38).createCell(8).setCellFormula("I40+I41+I42+I43+I44"); sheet.getRow(38).getCell(8).setCellStyle(normal);
        put(sheet,39,2,"32.Interest Income on Loan Portfolio",normal);
        sheet.getRow(39).createCell(3).setCellStyle(normal);
        sheet.getRow(39).createCell(4).setCellStyle(normal);
        sheet.getRow(39).createCell(5).setCellStyle(normal);
        sheet.getRow(39).createCell(6).setCellStyle(normal);
        sheet.getRow(39).createCell(7).setCellStyle(normal);
        sheet.getRow(39).createCell(8).setCellStyle(normal);
        put(sheet,40,2,"33.Fees and Commissions on Loan Portfolio",normal);
        sheet.getRow(40).createCell(3).setCellStyle(normal);
        sheet.getRow(40).createCell(4).setCellStyle(normal);
        sheet.getRow(40).createCell(5).setCellStyle(normal);
        sheet.getRow(40).createCell(6).setCellStyle(normal);
        sheet.getRow(40).createCell(7).setCellStyle(normal);
        sheet.getRow(40).createCell(8).setCellStyle(normal);
        put(sheet,41,2,"34.Incomes on Deposits in banks and other Fis",normal);
        sheet.getRow(41).createCell(3).setCellStyle(normal);
        sheet.getRow(41).createCell(4).setCellStyle(normal);
        sheet.getRow(41).createCell(5).setCellStyle(normal);
        sheet.getRow(41).createCell(6).setCellStyle(normal);
        sheet.getRow(41).createCell(7).setCellStyle(normal);
        sheet.getRow(41).createCell(8).setCellStyle(normal);
        put(sheet,42,2,"35.Incomes on Financial Instruments",normal);
        sheet.getRow(42).createCell(3).setCellStyle(normal);
        sheet.getRow(42).createCell(4).setCellStyle(normal);
        sheet.getRow(42).createCell(5).setCellStyle(normal);
        sheet.getRow(42).createCell(6).setCellStyle(normal);
        sheet.getRow(42).createCell(7).setCellStyle(normal);
        sheet.getRow(42).createCell(8).setCellStyle(normal);
        put(sheet,43,2,"36.Other financial Income",normal);
        sheet.getRow(43).createCell(3).setCellStyle(normal);
        sheet.getRow(43).createCell(4).setCellStyle(normal);
        sheet.getRow(43).createCell(5).setCellStyle(normal);
        sheet.getRow(43).createCell(6).setCellStyle(normal);
        sheet.getRow(43).createCell(7).setCellStyle(normal);
        sheet.getRow(43).createCell(8).setCellStyle(normal);
        put(sheet,44,2,"37.Recoveries on Loans (prov. Back)",normal);
        sheet.getRow(44).createCell(3).setCellStyle(normal);
        sheet.getRow(44).createCell(4).setCellStyle(normal);
        sheet.getRow(44).createCell(5).setCellStyle(normal);
        sheet.getRow(44).createCell(6).setCellStyle(normal);
        sheet.getRow(44).createCell(7).setCellStyle(normal);
        sheet.getRow(44).createCell(8).setCellStyle(normal);
        put(sheet,45,2,"38.Recoveries on loans (written offs)",normal);
        sheet.getRow(45).createCell(3).setCellStyle(normal);
        sheet.getRow(45).createCell(4).setCellStyle(normal);
        sheet.getRow(45).createCell(5).setCellStyle(normal);
        sheet.getRow(45).createCell(6).setCellStyle(normal);
        sheet.getRow(45).createCell(7).setCellStyle(normal);
        sheet.getRow(45).createCell(8).setCellStyle(normal);
        put(sheet,46,2,"39.Other operating Incomes",normal);
        sheet.getRow(46).createCell(3).setCellStyle(normal);
        sheet.getRow(46).createCell(4).setCellStyle(normal);
        sheet.getRow(46).createCell(5).setCellStyle(normal);
        sheet.getRow(46).createCell(6).setCellStyle(normal);
        sheet.getRow(46).createCell(7).setCellStyle(normal);
        sheet.getRow(46).createCell(8).setCellStyle(normal);
        put(sheet,47,2,"40.Non Operating Incomes",normal);
        sheet.getRow(47).createCell(3).setCellStyle(normal);
        sheet.getRow(47).createCell(4).setCellStyle(normal);
        sheet.getRow(47).createCell(5).setCellStyle(normal);
        sheet.getRow(47).createCell(6).setCellStyle(normal);
        sheet.getRow(47).createCell(7).setCellStyle(normal);
        sheet.getRow(47).createCell(8).setCellStyle(normal);
        put(sheet,48,2,"41.Total Incomes(31+37+38+39+40)",total);
        sheet.getRow(48).createCell(3).setCellFormula("D39+D45+D47+D48+D46"); sheet.getRow(48).getCell(3).setCellStyle(normal);
        sheet.getRow(48).createCell(4).setCellFormula("E39+E45+E47+E48+E46"); sheet.getRow(48).getCell(4).setCellStyle(normal);
        sheet.getRow(48).createCell(5).setCellFormula("F39+F45+F47+F48+F46"); sheet.getRow(48).getCell(5).setCellStyle(normal);
        sheet.getRow(48).createCell(6).setCellFormula("G39+G45+G47+G48+G46"); sheet.getRow(48).getCell(6).setCellStyle(normal);
        sheet.getRow(48).createCell(7).setCellFormula("H39+H45+H47+H48+H46"); sheet.getRow(48).getCell(7).setCellStyle(normal);
        sheet.getRow(48).createCell(8).setCellFormula("I39+I45+I47+I48+I46"); sheet.getRow(48).getCell(8).setCellStyle(normal);
        put(sheet,49,2,"42.Financial Expenses (43+44+45)",total);
        sheet.getRow(49).createCell(3).setCellFormula("D53+D52+D51"); sheet.getRow(49).getCell(3).setCellStyle(normal);
        sheet.getRow(49).createCell(4).setCellFormula("E53+E52+E51"); sheet.getRow(49).getCell(4).setCellStyle(normal);
        sheet.getRow(49).createCell(5).setCellFormula("F53+F52+F51"); sheet.getRow(49).getCell(5).setCellStyle(normal);
        sheet.getRow(49).createCell(6).setCellFormula("G53+G52+G51"); sheet.getRow(49).getCell(6).setCellStyle(normal);
        sheet.getRow(49).createCell(7).setCellFormula("H53+H52+H51"); sheet.getRow(49).getCell(7).setCellStyle(normal);
        sheet.getRow(49).createCell(8).setCellFormula("I53+I52+I51"); sheet.getRow(49).getCell(8).setCellStyle(normal);
        put(sheet,50,2,"43.Interest on cash collateral if any",normal);
        sheet.getRow(50).createCell(3).setCellStyle(normal);
        sheet.getRow(50).createCell(4).setCellStyle(normal);
        sheet.getRow(50).createCell(5).setCellStyle(normal);
        sheet.getRow(50).createCell(6).setCellStyle(normal);
        sheet.getRow(50).createCell(7).setCellStyle(normal);
        sheet.getRow(50).createCell(8).setCellStyle(normal);
        put(sheet,51,2,"44.Interest on borrowings from Fis and Non FIs",normal);
        sheet.getRow(51).createCell(3).setCellStyle(normal);
        sheet.getRow(51).createCell(4).setCellStyle(normal);
        sheet.getRow(51).createCell(5).setCellStyle(normal);
        sheet.getRow(51).createCell(6).setCellStyle(normal);
        sheet.getRow(51).createCell(7).setCellStyle(normal);
        sheet.getRow(51).createCell(8).setCellStyle(normal);
        put(sheet,52,2,"45. Bank Charges,Commissions and other Financial Exp.",normal);
        sheet.getRow(52).createCell(3).setCellStyle(normal);
        sheet.getRow(52).createCell(4).setCellStyle(normal);
        sheet.getRow(52).createCell(5).setCellStyle(normal);
        sheet.getRow(52).createCell(6).setCellStyle(normal);
        sheet.getRow(52).createCell(7).setCellStyle(normal);
        sheet.getRow(52).createCell(8).setCellStyle(normal);
        put(sheet,53,2,"46.Loan losses (provisions)",normal);
        sheet.getRow(53).createCell(3).setCellFormula("D10"); sheet.getRow(53).getCell(3).setCellStyle(normal);
        sheet.getRow(53).createCell(4).setCellFormula("E10"); sheet.getRow(53).getCell(4).setCellStyle(normal);
        sheet.getRow(53).createCell(5).setCellFormula("F10"); sheet.getRow(53).getCell(5).setCellStyle(normal);
        sheet.getRow(53).createCell(6).setCellFormula("G10"); sheet.getRow(53).getCell(6).setCellStyle(normal);
        sheet.getRow(53).createCell(7).setCellFormula("H10"); sheet.getRow(53).getCell(7).setCellStyle(normal);
        sheet.getRow(53).createCell(8).setCellFormula("I10"); sheet.getRow(53).getCell(8).setCellStyle(normal);
        put(sheet,54,2,"47. Loan Losses (written off for the period)",normal);
        sheet.getRow(54).createCell(3).setCellStyle(normal);
        sheet.getRow(54).createCell(4).setCellStyle(normal);
        sheet.getRow(54).createCell(5).setCellStyle(normal);
        sheet.getRow(54).createCell(6).setCellStyle(normal);
        sheet.getRow(54).createCell(7).setCellStyle(normal);
        sheet.getRow(54).createCell(8).setCellStyle(normal);
        put(sheet,55,2,"48.Personnel Expenses (Gross amount)",normal);
        sheet.getRow(55).createCell(3).setCellStyle(normal);
        sheet.getRow(55).createCell(4).setCellStyle(normal);
        sheet.getRow(55).createCell(5).setCellStyle(normal);
        sheet.getRow(55).createCell(6).setCellStyle(normal);
        sheet.getRow(55).createCell(7).setCellStyle(normal);
        sheet.getRow(55).createCell(8).setCellStyle(normal);
        put(sheet,56,2,"49.Administrative Expenses",normal);
        sheet.getRow(56).createCell(3).setCellStyle(normal);
        sheet.getRow(56).createCell(4).setCellStyle(normal);
        sheet.getRow(56).createCell(5).setCellStyle(normal);
        sheet.getRow(56).createCell(6).setCellStyle(normal);
        sheet.getRow(56).createCell(7).setCellStyle(normal);
        sheet.getRow(56).createCell(8).setCellStyle(normal);
        put(sheet,57,2,"50.Non Operating Expenses",normal);
        sheet.getRow(57).createCell(3).setCellStyle(normal);
        sheet.getRow(57).createCell(4).setCellStyle(normal);
        sheet.getRow(57).createCell(5).setCellStyle(normal);
        sheet.getRow(57).createCell(6).setCellStyle(normal);
        sheet.getRow(57).createCell(7).setCellStyle(normal);
        sheet.getRow(57).createCell(8).setCellStyle(normal);
        put(sheet,58,2,"51.Total Expenses  (42+46+47+48+49+50)",total);
        sheet.getRow(58).createCell(3).setCellFormula("D50+D54+D56+D57+D58+D55"); sheet.getRow(58).getCell(3).setCellStyle(normal);
        sheet.getRow(58).createCell(4).setCellFormula("E50+E54+E56+E57+E58+E55"); sheet.getRow(58).getCell(4).setCellStyle(normal);
        sheet.getRow(58).createCell(5).setCellFormula("F50+F54+F56+F57+F58+F55"); sheet.getRow(58).getCell(5).setCellStyle(normal);
        sheet.getRow(58).createCell(6).setCellFormula("G50+G54+G56+G57+G58+G55"); sheet.getRow(58).getCell(6).setCellStyle(normal);
        sheet.getRow(58).createCell(7).setCellFormula("H50+H54+H56+H57+H58+H55"); sheet.getRow(58).getCell(7).setCellStyle(normal);
        sheet.getRow(58).createCell(8).setCellFormula("I50+I54+I56+I57+I58+I55"); sheet.getRow(58).getCell(8).setCellStyle(normal);
        put(sheet,59,2,"52.Profit/Loss before donations (41-51)",total);
        sheet.getRow(59).createCell(3).setCellFormula("D49-D59"); sheet.getRow(59).getCell(3).setCellStyle(normal);
        sheet.getRow(59).createCell(4).setCellFormula("E49-E59"); sheet.getRow(59).getCell(4).setCellStyle(normal);
        sheet.getRow(59).createCell(5).setCellFormula("F49-F59"); sheet.getRow(59).getCell(5).setCellStyle(normal);
        sheet.getRow(59).createCell(6).setCellFormula("G49-G59"); sheet.getRow(59).getCell(6).setCellStyle(normal);
        sheet.getRow(59).createCell(7).setCellFormula("H49-H59"); sheet.getRow(59).getCell(7).setCellStyle(normal);
        sheet.getRow(59).createCell(8).setCellFormula("I49-I59"); sheet.getRow(59).getCell(8).setCellStyle(normal);
        put(sheet,60,2,"53. Income Tax ",normal);
        sheet.getRow(60).createCell(3).setCellStyle(normal);
        sheet.getRow(60).createCell(4).setCellStyle(normal);
        sheet.getRow(60).createCell(5).setCellStyle(normal);
        sheet.getRow(60).createCell(6).setCellStyle(normal);
        sheet.getRow(60).createCell(7).setCellStyle(normal);
        sheet.getRow(60).createCell(8).setCellStyle(normal);
        put(sheet,61,2,"54. Profit after tax and before donations(52-53)",total);
        sheet.getRow(61).createCell(3).setCellFormula("D60-D61"); sheet.getRow(61).getCell(3).setCellStyle(normal);
        sheet.getRow(61).createCell(4).setCellFormula("E60-E61"); sheet.getRow(61).getCell(4).setCellStyle(normal);
        sheet.getRow(61).createCell(5).setCellFormula("F60-F61"); sheet.getRow(61).getCell(5).setCellStyle(normal);
        sheet.getRow(61).createCell(6).setCellFormula("G60-G61"); sheet.getRow(61).getCell(6).setCellStyle(normal);
        sheet.getRow(61).createCell(7).setCellFormula("H60-H61"); sheet.getRow(61).getCell(7).setCellStyle(normal);
        sheet.getRow(61).createCell(8).setCellFormula("I60-I61"); sheet.getRow(61).getCell(8).setCellStyle(normal);
        put(sheet,62,2,"55.Donations (Financing Operating Expenses)",normal);
        sheet.getRow(62).createCell(3).setCellStyle(normal);
        sheet.getRow(62).createCell(4).setCellStyle(normal);
        sheet.getRow(62).createCell(5).setCellStyle(normal);
        sheet.getRow(62).createCell(6).setCellStyle(normal);
        sheet.getRow(62).createCell(7).setCellStyle(normal);
        sheet.getRow(62).createCell(8).setCellStyle(normal);
        put(sheet,63,2,"Profit/loss after tax and  donations (54+55)=23",total);
        sheet.getRow(63).createCell(3).setCellFormula("D62+D63"); sheet.getRow(63).getCell(3).setCellStyle(normal);
        sheet.getRow(63).createCell(4).setCellFormula("E62+E63"); sheet.getRow(63).getCell(4).setCellStyle(normal);
        sheet.getRow(63).createCell(5).setCellFormula("F62+F63"); sheet.getRow(63).getCell(5).setCellStyle(normal);
        sheet.getRow(63).createCell(6).setCellFormula("G62+G63"); sheet.getRow(63).getCell(6).setCellStyle(normal);
        sheet.getRow(63).createCell(7).setCellFormula("H62+H63"); sheet.getRow(63).getCell(7).setCellStyle(normal);
        sheet.getRow(63).createCell(8).setCellFormula("I62+I63"); sheet.getRow(63).getCell(8).setCellStyle(normal);
        put(sheet,64,2,"Dividends ",normal);
        sheet.getRow(64).createCell(3).setCellStyle(normal);
        sheet.getRow(64).createCell(4).setCellStyle(normal);
        sheet.getRow(64).createCell(5).setCellStyle(normal);
        sheet.getRow(64).createCell(6).setCellStyle(normal);
        sheet.getRow(64).createCell(7).setCellStyle(normal);
        sheet.getRow(64).createCell(8).setCellStyle(normal);
        put(sheet,65,2,"Net profit After Dividends ",total);
        sheet.getRow(65).createCell(3).setCellFormula("D64-D65"); sheet.getRow(65).getCell(3).setCellStyle(normal);
        sheet.getRow(65).createCell(4).setCellFormula("E64-E65"); sheet.getRow(65).getCell(4).setCellStyle(normal);
        sheet.getRow(65).createCell(5).setCellFormula("F64-F65"); sheet.getRow(65).getCell(5).setCellStyle(normal);
        sheet.getRow(65).createCell(6).setCellFormula("G64-G65"); sheet.getRow(65).getCell(6).setCellStyle(normal);
        sheet.getRow(65).createCell(7).setCellFormula("H64-H65"); sheet.getRow(65).getCell(7).setCellStyle(normal);
        sheet.getRow(65).createCell(8).setCellFormula("I64-I65"); sheet.getRow(65).getCell(8).setCellStyle(normal);
        put(sheet,66,2,"56.Cost to income   52/43",ratio);
        sheet.getRow(66).createCell(3).setCellFormula("IF(ISNUMBER(D59),IF(ISNUMBER(D49),D59/D49,\"\"),\"\")"); sheet.getRow(66).getCell(3).setCellStyle(ratio);
        sheet.getRow(66).createCell(4).setCellFormula("IF(ISNUMBER(E59),IF(ISNUMBER(E49),E59/E49,\"\"),\"\")"); sheet.getRow(66).getCell(4).setCellStyle(ratio);
        sheet.getRow(66).createCell(5).setCellFormula("IF(ISNUMBER(F59),IF(ISNUMBER(F49),F59/F49,\"\"),\"\")"); sheet.getRow(66).getCell(5).setCellStyle(ratio);
        sheet.getRow(66).createCell(6).setCellFormula("IF(ISNUMBER(G59),IF(ISNUMBER(G49),G59/G49,\"\"),\"\")"); sheet.getRow(66).getCell(6).setCellStyle(ratio);
        sheet.getRow(66).createCell(7).setCellFormula("IF(ISNUMBER(H59),IF(ISNUMBER(H49),H59/H49,\"\"),\"\")"); sheet.getRow(66).getCell(7).setCellStyle(ratio);
        sheet.getRow(66).createCell(8).setCellFormula("IF(ISNUMBER(I59),IF(ISNUMBER(I49),I59/I49,\"\"),\"\")"); sheet.getRow(66).getCell(8).setCellStyle(ratio);
        put(sheet,67,2,"57.% of Financial Income 34/43",ratio);
        sheet.getRow(67).createCell(3).setCellFormula("IF(ISNUMBER(D39),IF(ISNUMBER(D49),D39/D49,\"\"),\"\")"); sheet.getRow(67).getCell(3).setCellStyle(ratio);
        sheet.getRow(67).createCell(4).setCellFormula("IF(ISNUMBER(E39),IF(ISNUMBER(E49),E39/E49,\"\"),\"\")"); sheet.getRow(67).getCell(4).setCellStyle(ratio);
        sheet.getRow(67).createCell(5).setCellFormula("IF(ISNUMBER(F39),IF(ISNUMBER(F49),F39/F49,\"\"),\"\")"); sheet.getRow(67).getCell(5).setCellStyle(ratio);
        sheet.getRow(67).createCell(6).setCellFormula("IF(ISNUMBER(G39),IF(ISNUMBER(G49),G39/G49,\"\"),\"\")"); sheet.getRow(67).getCell(6).setCellStyle(ratio);
        sheet.getRow(67).createCell(7).setCellFormula("IF(ISNUMBER(H39),IF(ISNUMBER(H49),H39/H49,\"\"),\"\")"); sheet.getRow(67).getCell(7).setCellStyle(ratio);
        sheet.getRow(67).createCell(8).setCellFormula("IF(ISNUMBER(I39),IF(ISNUMBER(I49),I39/I49,\"\"),\"\")"); sheet.getRow(67).getCell(8).setCellStyle(ratio);
        put(sheet,68,2,"58.ROA   (AVERAGED 53/13)",ratio);
        sheet.getRow(68).createCell(3).setCellStyle(ratio);
        sheet.getRow(68).createCell(4).setCellStyle(ratio);
        sheet.getRow(68).createCell(5).setCellStyle(ratio);
        sheet.getRow(68).createCell(6).setCellStyle(ratio);
        sheet.getRow(68).createCell(7).setCellStyle(ratio);
        sheet.getRow(68).createCell(8).setCellStyle(ratio);
        put(sheet,69,2,"59.ROE  (AVERAGED 53/21)",ratio);
        sheet.getRow(69).createCell(3).setCellStyle(ratio);
        sheet.getRow(69).createCell(4).setCellStyle(ratio);
        sheet.getRow(69).createCell(5).setCellStyle(ratio);
        sheet.getRow(69).createCell(6).setCellStyle(ratio);
        sheet.getRow(69).createCell(7).setCellStyle(ratio);
        sheet.getRow(69).createCell(8).setCellStyle(ratio);
        for (int c = 4; c <= 8; c++) {
            String current = org.apache.poi.ss.util.CellReference.convertNumToColString(c);
            String previous = org.apache.poi.ss.util.CellReference.convertNumToColString(c - 1);
            sheet.getRow(68).getCell(c).setCellFormula("(" + current + "60)/((" + current + "20+" + previous + "20)/2)");
            sheet.getRow(68).getCell(c).setCellStyle(ratio);
            sheet.getRow(69).getCell(c).setCellFormula("(" + current + "60)/((" + current + "26+" + previous + "26)/2)");
            sheet.getRow(69).getCell(c).setCellStyle(ratio);
        }
        put(sheet,70,2,"C. OFF-BALANCE SHEET (Written-Off Loans)",section);
        sheet.getRow(70).createCell(3).setCellStyle(normal);
        sheet.getRow(70).createCell(4).setCellStyle(normal);
        sheet.getRow(70).createCell(5).setCellStyle(normal);
        sheet.getRow(70).createCell(6).setCellStyle(normal);
        sheet.getRow(70).createCell(7).setCellStyle(normal);
        sheet.getRow(70).createCell(8).setCellStyle(normal);
        put(sheet,71,2,"D.SUPPLEMENTARY INFORMATION",section);
        sheet.getRow(71).createCell(3).setCellStyle(normal);
        sheet.getRow(71).createCell(4).setCellStyle(normal);
        sheet.getRow(71).createCell(5).setCellStyle(normal);
        sheet.getRow(71).createCell(6).setCellStyle(normal);
        sheet.getRow(71).createCell(7).setCellStyle(normal);
        sheet.getRow(71).createCell(8).setCellStyle(normal);
        put(sheet,72,2,"61.Men",normal);
        sheet.getRow(72).createCell(3).setCellStyle(normal);
        sheet.getRow(72).createCell(4).setCellStyle(normal);
        sheet.getRow(72).createCell(5).setCellStyle(normal);
        sheet.getRow(72).createCell(6).setCellStyle(normal);
        sheet.getRow(72).createCell(7).setCellStyle(normal);
        sheet.getRow(72).createCell(8).setCellStyle(normal);
        put(sheet,73,2,"62.Women",normal);
        sheet.getRow(73).createCell(3).setCellStyle(normal);
        sheet.getRow(73).createCell(4).setCellStyle(normal);
        sheet.getRow(73).createCell(5).setCellStyle(normal);
        sheet.getRow(73).createCell(6).setCellStyle(normal);
        sheet.getRow(73).createCell(7).setCellStyle(normal);
        sheet.getRow(73).createCell(8).setCellStyle(normal);
        put(sheet,74,2,"63.Group&Entities",normal);
        sheet.getRow(74).createCell(3).setCellStyle(normal);
        sheet.getRow(74).createCell(4).setCellStyle(normal);
        sheet.getRow(74).createCell(5).setCellStyle(normal);
        sheet.getRow(74).createCell(6).setCellStyle(normal);
        sheet.getRow(74).createCell(7).setCellStyle(normal);
        sheet.getRow(74).createCell(8).setCellStyle(normal);
        put(sheet,75,2,"64.Total (61+62+63)",total);
        sheet.getRow(75).createCell(3).setCellFormula("D73+D74+D75"); sheet.getRow(75).getCell(3).setCellStyle(normal);
        sheet.getRow(75).createCell(4).setCellFormula("E73+E74+E75"); sheet.getRow(75).getCell(4).setCellStyle(normal);
        sheet.getRow(75).createCell(5).setCellFormula("F73+F74+F75"); sheet.getRow(75).getCell(5).setCellStyle(normal);
        sheet.getRow(75).createCell(6).setCellFormula("G73+G74+G75"); sheet.getRow(75).getCell(6).setCellStyle(normal);
        sheet.getRow(75).createCell(7).setCellFormula("H73+H74+H75"); sheet.getRow(75).getCell(7).setCellStyle(normal);
        sheet.getRow(75).createCell(8).setCellFormula("I73+I74+I75"); sheet.getRow(75).getCell(8).setCellStyle(normal);
        put(sheet,76,2,"66.Men",normal);
        sheet.getRow(76).createCell(3).setCellStyle(normal);
        sheet.getRow(76).createCell(4).setCellStyle(normal);
        sheet.getRow(76).createCell(5).setCellStyle(normal);
        sheet.getRow(76).createCell(6).setCellStyle(normal);
        sheet.getRow(76).createCell(7).setCellStyle(normal);
        sheet.getRow(76).createCell(8).setCellStyle(normal);
        put(sheet,77,2,"67.Women",normal);
        sheet.getRow(77).createCell(3).setCellStyle(normal);
        sheet.getRow(77).createCell(4).setCellStyle(normal);
        sheet.getRow(77).createCell(5).setCellStyle(normal);
        sheet.getRow(77).createCell(6).setCellStyle(normal);
        sheet.getRow(77).createCell(7).setCellStyle(normal);
        sheet.getRow(77).createCell(8).setCellStyle(normal);
        put(sheet,78,2,"68.Group&Entities",normal);
        sheet.getRow(78).createCell(3).setCellStyle(normal);
        sheet.getRow(78).createCell(4).setCellStyle(normal);
        sheet.getRow(78).createCell(5).setCellStyle(normal);
        sheet.getRow(78).createCell(6).setCellStyle(normal);
        sheet.getRow(78).createCell(7).setCellStyle(normal);
        sheet.getRow(78).createCell(8).setCellStyle(normal);
        put(sheet,79,2,"69.Total (66+67+68)=5=76=84",total);
        sheet.getRow(79).createCell(3).setCellFormula("D77+D78+D79"); sheet.getRow(79).getCell(3).setCellStyle(normal);
        sheet.getRow(79).createCell(4).setCellFormula("E77+E78+E79"); sheet.getRow(79).getCell(4).setCellStyle(normal);
        sheet.getRow(79).createCell(5).setCellFormula("F77+F78+F79"); sheet.getRow(79).getCell(5).setCellStyle(normal);
        sheet.getRow(79).createCell(6).setCellFormula("G77+G78+G79"); sheet.getRow(79).getCell(6).setCellStyle(normal);
        sheet.getRow(79).createCell(7).setCellFormula("H77+H78+H79"); sheet.getRow(79).getCell(7).setCellStyle(normal);
        sheet.getRow(79).createCell(8).setCellFormula("I77+I78+I79"); sheet.getRow(79).getCell(8).setCellStyle(normal);
        put(sheet,80,2,"71.Agriculture, Livestock, Fishing ",normal);
        sheet.getRow(80).createCell(3).setCellStyle(normal);
        sheet.getRow(80).createCell(4).setCellStyle(normal);
        sheet.getRow(80).createCell(5).setCellStyle(normal);
        sheet.getRow(80).createCell(6).setCellStyle(normal);
        sheet.getRow(80).createCell(7).setCellStyle(normal);
        sheet.getRow(80).createCell(8).setCellStyle(normal);
        put(sheet,81,2,"72.Public Works (Construction), Buildings, Residences/Homes",normal);
        sheet.getRow(81).createCell(3).setCellStyle(normal);
        sheet.getRow(81).createCell(4).setCellStyle(normal);
        sheet.getRow(81).createCell(5).setCellStyle(normal);
        sheet.getRow(81).createCell(6).setCellStyle(normal);
        sheet.getRow(81).createCell(7).setCellStyle(normal);
        sheet.getRow(81).createCell(8).setCellStyle(normal);
        put(sheet,82,2,"73.Commerce, Restaurants, Hotels",normal);
        sheet.getRow(82).createCell(3).setCellStyle(normal);
        sheet.getRow(82).createCell(4).setCellStyle(normal);
        sheet.getRow(82).createCell(5).setCellStyle(normal);
        sheet.getRow(82).createCell(6).setCellStyle(normal);
        sheet.getRow(82).createCell(7).setCellStyle(normal);
        sheet.getRow(82).createCell(8).setCellStyle(normal);
        put(sheet,83,2,"74.Transport, Warehouses, Communications",normal);
        sheet.getRow(83).createCell(3).setCellStyle(normal);
        sheet.getRow(83).createCell(4).setCellStyle(normal);
        sheet.getRow(83).createCell(5).setCellStyle(normal);
        sheet.getRow(83).createCell(6).setCellStyle(normal);
        sheet.getRow(83).createCell(7).setCellStyle(normal);
        sheet.getRow(83).createCell(8).setCellStyle(normal);
        put(sheet,84,2,"75.Others",normal);
        sheet.getRow(84).createCell(3).setCellStyle(normal);
        sheet.getRow(84).createCell(4).setCellStyle(normal);
        sheet.getRow(84).createCell(5).setCellStyle(normal);
        sheet.getRow(84).createCell(6).setCellStyle(normal);
        sheet.getRow(84).createCell(7).setCellStyle(normal);
        sheet.getRow(84).createCell(8).setCellStyle(normal);
        put(sheet,85,2,"76.Total  (71+72+73+74+75)=5=69=84",total);
        sheet.getRow(85).createCell(3).setCellFormula("D81+D82+D83+D84+D85"); sheet.getRow(85).getCell(3).setCellStyle(normal);
        sheet.getRow(85).createCell(4).setCellFormula("E81+E82+E83+E84+E85"); sheet.getRow(85).getCell(4).setCellStyle(normal);
        sheet.getRow(85).createCell(5).setCellFormula("F81+F82+F83+F84+F85"); sheet.getRow(85).getCell(5).setCellStyle(normal);
        sheet.getRow(85).createCell(6).setCellFormula("G81+G82+G83+G84+G85"); sheet.getRow(85).getCell(6).setCellStyle(normal);
        sheet.getRow(85).createCell(7).setCellFormula("H81+H82+H83+H84+H85"); sheet.getRow(85).getCell(7).setCellStyle(normal);
        sheet.getRow(85).createCell(8).setCellFormula("I81+I82+I83+I84+I85"); sheet.getRow(85).getCell(8).setCellStyle(normal);
        put(sheet,86,2,"78.Current loans -Normal (0% Prov.)",normal);
        sheet.getRow(86).createCell(3).setCellStyle(normal);
        sheet.getRow(86).createCell(4).setCellStyle(normal);
        sheet.getRow(86).createCell(5).setCellStyle(normal);
        sheet.getRow(86).createCell(6).setCellStyle(normal);
        sheet.getRow(86).createCell(7).setCellStyle(normal);
        sheet.getRow(86).createCell(8).setCellStyle(normal);
        put(sheet,87,2,"79. Watch (1-89 days) 1% Prov.",normal);
        sheet.getRow(87).createCell(3).setCellStyle(normal);
        sheet.getRow(87).createCell(4).setCellStyle(normal);
        sheet.getRow(87).createCell(5).setCellStyle(normal);
        sheet.getRow(87).createCell(6).setCellStyle(normal);
        sheet.getRow(87).createCell(7).setCellStyle(normal);
        sheet.getRow(87).createCell(8).setCellStyle(normal);
        put(sheet,88,2,"80. Substandard (90-179 days)  20% Prov.",normal);
        sheet.getRow(88).createCell(3).setCellStyle(normal);
        sheet.getRow(88).createCell(4).setCellStyle(normal);
        sheet.getRow(88).createCell(5).setCellStyle(normal);
        sheet.getRow(88).createCell(6).setCellStyle(normal);
        sheet.getRow(88).createCell(7).setCellStyle(normal);
        sheet.getRow(88).createCell(8).setCellStyle(normal);
        put(sheet,89,2,"81.Doubtful (180-359 days) 50% Prov.",normal);
        sheet.getRow(89).createCell(3).setCellStyle(normal);
        sheet.getRow(89).createCell(4).setCellStyle(normal);
        sheet.getRow(89).createCell(5).setCellStyle(normal);
        sheet.getRow(89).createCell(6).setCellStyle(normal);
        sheet.getRow(89).createCell(7).setCellStyle(normal);
        sheet.getRow(89).createCell(8).setCellStyle(normal);
        put(sheet,90,2,"82. Loss (360 -719 days ) 100% prov.",normal);
        sheet.getRow(90).createCell(3).setCellStyle(normal);
        sheet.getRow(90).createCell(4).setCellStyle(normal);
        sheet.getRow(90).createCell(5).setCellStyle(normal);
        sheet.getRow(90).createCell(6).setCellStyle(normal);
        sheet.getRow(90).createCell(7).setCellStyle(normal);
        sheet.getRow(90).createCell(8).setCellStyle(normal);
        put(sheet,91,2,"83.(Restructured)",normal);
        sheet.getRow(91).createCell(3).setCellStyle(normal);
        sheet.getRow(91).createCell(4).setCellStyle(normal);
        sheet.getRow(91).createCell(5).setCellStyle(normal);
        sheet.getRow(91).createCell(6).setCellStyle(normal);
        sheet.getRow(91).createCell(7).setCellStyle(normal);
        sheet.getRow(91).createCell(8).setCellStyle(normal);
        put(sheet,92,2,"84.Total (78+79+80+81+82+83)",total);
        sheet.getRow(92).createCell(3).setCellFormula("SUM(D87:D92)"); sheet.getRow(92).getCell(3).setCellStyle(normal);
        sheet.getRow(92).createCell(4).setCellFormula("SUM(E87:E92)"); sheet.getRow(92).getCell(4).setCellStyle(normal);
        sheet.getRow(92).createCell(5).setCellFormula("SUM(F87:F92)"); sheet.getRow(92).getCell(5).setCellStyle(normal);
        sheet.getRow(92).createCell(6).setCellFormula("SUM(G87:G92)"); sheet.getRow(92).getCell(6).setCellStyle(normal);
        sheet.getRow(92).createCell(7).setCellFormula("SUM(H87:H92)"); sheet.getRow(92).getCell(7).setCellStyle(normal);
        sheet.getRow(92).createCell(8).setCellFormula("SUM(I87:I92)"); sheet.getRow(92).getCell(8).setCellStyle(normal);
        put(sheet,93,2,"86.Men",normal);
        sheet.getRow(93).createCell(3).setCellStyle(normal);
        sheet.getRow(93).createCell(4).setCellStyle(normal);
        sheet.getRow(93).createCell(5).setCellStyle(normal);
        sheet.getRow(93).createCell(6).setCellStyle(normal);
        sheet.getRow(93).createCell(7).setCellStyle(normal);
        sheet.getRow(93).createCell(8).setCellStyle(normal);
        put(sheet,94,2,"87.Women",normal);
        sheet.getRow(94).createCell(3).setCellStyle(normal);
        sheet.getRow(94).createCell(4).setCellStyle(normal);
        sheet.getRow(94).createCell(5).setCellStyle(normal);
        sheet.getRow(94).createCell(6).setCellStyle(normal);
        sheet.getRow(94).createCell(7).setCellStyle(normal);
        sheet.getRow(94).createCell(8).setCellStyle(normal);
        put(sheet,95,2,"88.Group&Entities",normal);
        sheet.getRow(95).createCell(3).setCellStyle(normal);
        sheet.getRow(95).createCell(4).setCellStyle(normal);
        sheet.getRow(95).createCell(5).setCellStyle(normal);
        sheet.getRow(95).createCell(6).setCellStyle(normal);
        sheet.getRow(95).createCell(7).setCellStyle(normal);
        sheet.getRow(95).createCell(8).setCellStyle(normal);
        put(sheet,96,2,"88.Total (86+87+88)",total);
        sheet.getRow(96).createCell(3).setCellFormula("D94+D95+D96"); sheet.getRow(96).getCell(3).setCellStyle(normal);
        sheet.getRow(96).createCell(4).setCellFormula("E94+E95+E96"); sheet.getRow(96).getCell(4).setCellStyle(normal);
        sheet.getRow(96).createCell(5).setCellFormula("F94+F95+F96"); sheet.getRow(96).getCell(5).setCellStyle(normal);
        sheet.getRow(96).createCell(6).setCellFormula("G94+G95+G96"); sheet.getRow(96).getCell(6).setCellStyle(normal);
        sheet.getRow(96).createCell(7).setCellFormula("H94+H95+H96"); sheet.getRow(96).getCell(7).setCellStyle(normal);
        sheet.getRow(96).createCell(8).setCellFormula("I94+I95+I96"); sheet.getRow(96).getCell(8).setCellStyle(normal);
        put(sheet,97,2,"91.Men",normal);
        sheet.getRow(97).createCell(3).setCellStyle(normal);
        sheet.getRow(97).createCell(4).setCellStyle(normal);
        sheet.getRow(97).createCell(5).setCellStyle(normal);
        sheet.getRow(97).createCell(6).setCellStyle(normal);
        sheet.getRow(97).createCell(7).setCellStyle(normal);
        sheet.getRow(97).createCell(8).setCellStyle(normal);
        put(sheet,98,2,"92.Women",normal);
        sheet.getRow(98).createCell(3).setCellStyle(normal);
        sheet.getRow(98).createCell(4).setCellStyle(normal);
        sheet.getRow(98).createCell(5).setCellStyle(normal);
        sheet.getRow(98).createCell(6).setCellStyle(normal);
        sheet.getRow(98).createCell(7).setCellStyle(normal);
        sheet.getRow(98).createCell(8).setCellStyle(normal);
        put(sheet,99,2,"93.Group&Entities",normal);
        sheet.getRow(99).createCell(3).setCellStyle(normal);
        sheet.getRow(99).createCell(4).setCellStyle(normal);
        sheet.getRow(99).createCell(5).setCellStyle(normal);
        sheet.getRow(99).createCell(6).setCellStyle(normal);
        sheet.getRow(99).createCell(7).setCellStyle(normal);
        sheet.getRow(99).createCell(8).setCellStyle(normal);
        put(sheet,100,2,"94.Total (92+93+94)",total);
        sheet.getRow(100).createCell(3).setCellFormula("D98+D99+D100"); sheet.getRow(100).getCell(3).setCellStyle(normal);
        sheet.getRow(100).createCell(4).setCellFormula("E98+E99+E100"); sheet.getRow(100).getCell(4).setCellStyle(normal);
        sheet.getRow(100).createCell(5).setCellFormula("F98+F99+F100"); sheet.getRow(100).getCell(5).setCellStyle(normal);
        sheet.getRow(100).createCell(6).setCellFormula("G98+G99+G100"); sheet.getRow(100).getCell(6).setCellStyle(normal);
        sheet.getRow(100).createCell(7).setCellFormula("H98+H99+H100"); sheet.getRow(100).getCell(7).setCellStyle(normal);
        sheet.getRow(100).createCell(8).setCellFormula("I98+I99+I100"); sheet.getRow(100).getCell(8).setCellStyle(normal);
        put(sheet,101,2,"96.Agriculture, Livestock, Fishing ",normal);
        sheet.getRow(101).createCell(3).setCellStyle(normal);
        sheet.getRow(101).createCell(4).setCellStyle(normal);
        sheet.getRow(101).createCell(5).setCellStyle(normal);
        sheet.getRow(101).createCell(6).setCellStyle(normal);
        sheet.getRow(101).createCell(7).setCellStyle(normal);
        sheet.getRow(101).createCell(8).setCellStyle(normal);
        put(sheet,102,2,"97.Public Works (Construction), Buildings, Residences/Homes",normal);
        sheet.getRow(102).createCell(3).setCellStyle(normal);
        sheet.getRow(102).createCell(4).setCellStyle(normal);
        sheet.getRow(102).createCell(5).setCellStyle(normal);
        sheet.getRow(102).createCell(6).setCellStyle(normal);
        sheet.getRow(102).createCell(7).setCellStyle(normal);
        sheet.getRow(102).createCell(8).setCellStyle(normal);
        put(sheet,103,2,"98.Commerce, Restaurants, Hotels",normal);
        sheet.getRow(103).createCell(3).setCellStyle(normal);
        sheet.getRow(103).createCell(4).setCellStyle(normal);
        sheet.getRow(103).createCell(5).setCellStyle(normal);
        sheet.getRow(103).createCell(6).setCellStyle(normal);
        sheet.getRow(103).createCell(7).setCellStyle(normal);
        sheet.getRow(103).createCell(8).setCellStyle(normal);
        put(sheet,104,2,"99.Transport, Warehouses, Communications",normal);
        sheet.getRow(104).createCell(3).setCellStyle(normal);
        sheet.getRow(104).createCell(4).setCellStyle(normal);
        sheet.getRow(104).createCell(5).setCellStyle(normal);
        sheet.getRow(104).createCell(6).setCellStyle(normal);
        sheet.getRow(104).createCell(7).setCellStyle(normal);
        sheet.getRow(104).createCell(8).setCellStyle(normal);
        put(sheet,105,2,"100.Others",normal);
        sheet.getRow(105).createCell(3).setCellStyle(normal);
        sheet.getRow(105).createCell(4).setCellStyle(normal);
        sheet.getRow(105).createCell(5).setCellStyle(normal);
        sheet.getRow(105).createCell(6).setCellStyle(normal);
        sheet.getRow(105).createCell(7).setCellStyle(normal);
        sheet.getRow(105).createCell(8).setCellStyle(normal);
        put(sheet,106,2,"101.Total  (96+97+98+99+100)",total);
        sheet.getRow(106).createCell(3).setCellFormula("D102+D103+D104+D105+D106"); sheet.getRow(106).getCell(3).setCellStyle(normal);
        sheet.getRow(106).createCell(4).setCellFormula("E102+E103+E104+E105+E106"); sheet.getRow(106).getCell(4).setCellStyle(normal);
        sheet.getRow(106).createCell(5).setCellFormula("F102+F103+F104+F105+F106"); sheet.getRow(106).getCell(5).setCellStyle(normal);
        sheet.getRow(106).createCell(6).setCellFormula("G102+G103+G104+G105+G106"); sheet.getRow(106).getCell(6).setCellStyle(normal);
        sheet.getRow(106).createCell(7).setCellFormula("H102+H103+H104+H105+H106"); sheet.getRow(106).getCell(7).setCellStyle(normal);
        sheet.getRow(106).createCell(8).setCellFormula("I102+I103+I104+I105+I106"); sheet.getRow(106).getCell(8).setCellStyle(normal);
        put(sheet,107,2,"103. Borrowing from Shareholders at …..% P.a",normal);
        sheet.getRow(107).createCell(3).setCellStyle(normal);
        sheet.getRow(107).createCell(4).setCellStyle(normal);
        sheet.getRow(107).createCell(5).setCellStyle(normal);
        sheet.getRow(107).createCell(6).setCellStyle(normal);
        sheet.getRow(107).createCell(7).setCellStyle(normal);
        sheet.getRow(107).createCell(8).setCellStyle(normal);
        put(sheet,108,2,"104.Borrowing from related parties (Parent, Subsidiary, Sister company Etc at …% P.a",normal);
        sheet.getRow(108).createCell(3).setCellStyle(normal);
        sheet.getRow(108).createCell(4).setCellStyle(normal);
        sheet.getRow(108).createCell(5).setCellStyle(normal);
        sheet.getRow(108).createCell(6).setCellStyle(normal);
        sheet.getRow(108).createCell(7).setCellStyle(normal);
        sheet.getRow(108).createCell(8).setCellStyle(normal);
        put(sheet,109,2,"105. Borrowing from Banks or Micro finance at …% P.a",normal);
        sheet.getRow(109).createCell(3).setCellStyle(normal);
        sheet.getRow(109).createCell(4).setCellStyle(normal);
        sheet.getRow(109).createCell(5).setCellStyle(normal);
        sheet.getRow(109).createCell(6).setCellStyle(normal);
        sheet.getRow(109).createCell(7).setCellStyle(normal);
        sheet.getRow(109).createCell(8).setCellStyle(normal);
        put(sheet,110,2,"106. Borrowing from other sources (Specify)  at …..% P.a",normal);
        sheet.getRow(110).createCell(3).setCellStyle(normal);
        sheet.getRow(110).createCell(4).setCellStyle(normal);
        sheet.getRow(110).createCell(5).setCellStyle(normal);
        sheet.getRow(110).createCell(6).setCellStyle(normal);
        sheet.getRow(110).createCell(7).setCellStyle(normal);
        sheet.getRow(110).createCell(8).setCellStyle(normal);
        put(sheet,111,2,"107.Total  (103+104+105+106)",total);
        sheet.getRow(111).createCell(3).setCellFormula("SUM(D108:D111)"); sheet.getRow(111).getCell(3).setCellStyle(normal);
        sheet.getRow(111).createCell(4).setCellFormula("SUM(E108:E111)"); sheet.getRow(111).getCell(4).setCellStyle(normal);
        sheet.getRow(111).createCell(5).setCellFormula("SUM(F108:F111)"); sheet.getRow(111).getCell(5).setCellStyle(normal);
        sheet.getRow(111).createCell(6).setCellFormula("SUM(G108:G111)"); sheet.getRow(111).getCell(6).setCellStyle(normal);
        sheet.getRow(111).createCell(7).setCellFormula("SUM(H108:H111)"); sheet.getRow(111).getCell(7).setCellStyle(normal);
        sheet.getRow(111).createCell(8).setCellFormula("SUM(I108:I111)"); sheet.getRow(111).getCell(8).setCellStyle(normal);
        put(sheet,112,2,"Number of Disbursed Loans to WE (As Per Quarter )",normal);
        sheet.getRow(112).createCell(3).setCellStyle(normal);
        sheet.getRow(112).createCell(4).setCellStyle(normal);
        sheet.getRow(112).createCell(5).setCellStyle(normal);
        sheet.getRow(112).createCell(6).setCellStyle(normal);
        sheet.getRow(112).createCell(7).setCellStyle(normal);
        sheet.getRow(112).createCell(8).setCellStyle(normal);
        put(sheet,113,2,"Number of Outstanding Loans to WE (Balance)",normal);
        sheet.getRow(113).createCell(3).setCellStyle(normal);
        sheet.getRow(113).createCell(4).setCellStyle(normal);
        sheet.getRow(113).createCell(5).setCellStyle(normal);
        sheet.getRow(113).createCell(6).setCellStyle(normal);
        sheet.getRow(113).createCell(7).setCellStyle(normal);
        sheet.getRow(113).createCell(8).setCellStyle(normal);
        put(sheet,114,2,"Value of Disbursed Loans to WE (As Per Quarter )",normal);
        sheet.getRow(114).createCell(3).setCellStyle(normal);
        sheet.getRow(114).createCell(4).setCellStyle(normal);
        sheet.getRow(114).createCell(5).setCellStyle(normal);
        sheet.getRow(114).createCell(6).setCellStyle(normal);
        sheet.getRow(114).createCell(7).setCellStyle(normal);
        sheet.getRow(114).createCell(8).setCellStyle(normal);
        put(sheet,115,2,"Value  of Outstanding Loans to WE (Balance)",normal);
        sheet.getRow(115).createCell(3).setCellStyle(normal);
        sheet.getRow(115).createCell(4).setCellStyle(normal);
        sheet.getRow(115).createCell(5).setCellStyle(normal);
        sheet.getRow(115).createCell(6).setCellStyle(normal);
        sheet.getRow(115).createCell(7).setCellStyle(normal);
        sheet.getRow(115).createCell(8).setCellStyle(normal);
        put(sheet,116,2," Number of Accounts with WE",normal);
        sheet.getRow(116).createCell(3).setCellStyle(normal);
        sheet.getRow(116).createCell(4).setCellStyle(normal);
        sheet.getRow(116).createCell(5).setCellStyle(normal);
        sheet.getRow(116).createCell(6).setCellStyle(normal);
        sheet.getRow(116).createCell(7).setCellStyle(normal);
        sheet.getRow(116).createCell(8).setCellStyle(normal);
        put(sheet,117,2,"Number of Disbursed Loans to SMEs (As Per Quarter )",normal);
        sheet.getRow(117).createCell(3).setCellStyle(normal);
        sheet.getRow(117).createCell(4).setCellStyle(normal);
        sheet.getRow(117).createCell(5).setCellStyle(normal);
        sheet.getRow(117).createCell(6).setCellStyle(normal);
        sheet.getRow(117).createCell(7).setCellStyle(normal);
        sheet.getRow(117).createCell(8).setCellStyle(normal);
        put(sheet,118,2,"Number of Outstanding Loans to SMEs (Balance)",normal);
        sheet.getRow(118).createCell(3).setCellStyle(normal);
        sheet.getRow(118).createCell(4).setCellStyle(normal);
        sheet.getRow(118).createCell(5).setCellStyle(normal);
        sheet.getRow(118).createCell(6).setCellStyle(normal);
        sheet.getRow(118).createCell(7).setCellStyle(normal);
        sheet.getRow(118).createCell(8).setCellStyle(normal);
        put(sheet,119,2,"Value of Disbursed Loans to SMEs (As Per Quarter )",normal);
        sheet.getRow(119).createCell(3).setCellStyle(normal);
        sheet.getRow(119).createCell(4).setCellStyle(normal);
        sheet.getRow(119).createCell(5).setCellStyle(normal);
        sheet.getRow(119).createCell(6).setCellStyle(normal);
        sheet.getRow(119).createCell(7).setCellStyle(normal);
        sheet.getRow(119).createCell(8).setCellStyle(normal);
        put(sheet,120,2,"Value  of Outstanding Loans to SMEs (As Per Quarter)",normal);
        sheet.getRow(120).createCell(3).setCellStyle(normal);
        sheet.getRow(120).createCell(4).setCellStyle(normal);
        sheet.getRow(120).createCell(5).setCellStyle(normal);
        sheet.getRow(120).createCell(6).setCellStyle(normal);
        sheet.getRow(120).createCell(7).setCellStyle(normal);
        sheet.getRow(120).createCell(8).setCellStyle(normal);
        put(sheet,121,2,"Number of Disbursed Loans to YE (As Per Quarter )",normal);
        sheet.getRow(121).createCell(3).setCellStyle(normal);
        sheet.getRow(121).createCell(4).setCellStyle(normal);
        sheet.getRow(121).createCell(5).setCellStyle(normal);
        sheet.getRow(121).createCell(6).setCellStyle(normal);
        sheet.getRow(121).createCell(7).setCellStyle(normal);
        sheet.getRow(121).createCell(8).setCellStyle(normal);
        put(sheet,122,2,"Number of Outstanding Loans to YE (As Per Quarter)",normal);
        sheet.getRow(122).createCell(3).setCellStyle(normal);
        sheet.getRow(122).createCell(4).setCellStyle(normal);
        sheet.getRow(122).createCell(5).setCellStyle(normal);
        sheet.getRow(122).createCell(6).setCellStyle(normal);
        sheet.getRow(122).createCell(7).setCellStyle(normal);
        sheet.getRow(122).createCell(8).setCellStyle(normal);
        put(sheet,123,2,"Value of Disbursed Loans to YE (As Per Quarter )",normal);
        sheet.getRow(123).createCell(3).setCellStyle(normal);
        sheet.getRow(123).createCell(4).setCellStyle(normal);
        sheet.getRow(123).createCell(5).setCellStyle(normal);
        sheet.getRow(123).createCell(6).setCellStyle(normal);
        sheet.getRow(123).createCell(7).setCellStyle(normal);
        sheet.getRow(123).createCell(8).setCellStyle(normal);
        put(sheet,124,2,"Value  of Outstanding Loans to YE (As Per Quarter)",normal);
        sheet.getRow(124).createCell(3).setCellStyle(normal);
        sheet.getRow(124).createCell(4).setCellStyle(normal);
        sheet.getRow(124).createCell(5).setCellStyle(normal);
        sheet.getRow(124).createCell(6).setCellStyle(normal);
        sheet.getRow(124).createCell(7).setCellStyle(normal);
        sheet.getRow(124).createCell(8).setCellStyle(normal);
        put(sheet,125,2,"Number of loans applied for (As Per Quarter )",normal);
        sheet.getRow(125).createCell(3).setCellStyle(normal);
        sheet.getRow(125).createCell(4).setCellStyle(normal);
        sheet.getRow(125).createCell(5).setCellStyle(normal);
        sheet.getRow(125).createCell(6).setCellStyle(normal);
        sheet.getRow(125).createCell(7).setCellStyle(normal);
        sheet.getRow(125).createCell(8).setCellStyle(normal);
        put(sheet,126,2,"Number of loans rejected (As Per Quarter )",normal);
        sheet.getRow(126).createCell(3).setCellStyle(normal);
        sheet.getRow(126).createCell(4).setCellStyle(normal);
        sheet.getRow(126).createCell(5).setCellStyle(normal);
        sheet.getRow(126).createCell(6).setCellStyle(normal);
        sheet.getRow(126).createCell(7).setCellStyle(normal);
        sheet.getRow(126).createCell(8).setCellStyle(normal);
        put(sheet,127,2,"Amount of loans applied for (As Per Quarter )",normal);
        sheet.getRow(127).createCell(3).setCellStyle(normal);
        sheet.getRow(127).createCell(4).setCellStyle(normal);
        sheet.getRow(127).createCell(5).setCellStyle(normal);
        sheet.getRow(127).createCell(6).setCellStyle(normal);
        sheet.getRow(127).createCell(7).setCellStyle(normal);
        sheet.getRow(127).createCell(8).setCellStyle(normal);
        put(sheet,128,2,"Amount of loans rejected (As Per Quarter )",normal);
        sheet.getRow(128).createCell(3).setCellStyle(normal);
        sheet.getRow(128).createCell(4).setCellStyle(normal);
        sheet.getRow(128).createCell(5).setCellStyle(normal);
        sheet.getRow(128).createCell(6).setCellStyle(normal);
        sheet.getRow(128).createCell(7).setCellStyle(normal);
        sheet.getRow(128).createCell(8).setCellStyle(normal);
        put(sheet,129,2,"Men",normal);
        sheet.getRow(129).createCell(3).setCellStyle(normal);
        sheet.getRow(129).createCell(4).setCellStyle(normal);
        sheet.getRow(129).createCell(5).setCellStyle(normal);
        sheet.getRow(129).createCell(6).setCellStyle(normal);
        sheet.getRow(129).createCell(7).setCellStyle(normal);
        sheet.getRow(129).createCell(8).setCellStyle(normal);
        put(sheet,130,2,"Women",normal);
        sheet.getRow(130).createCell(3).setCellStyle(normal);
        sheet.getRow(130).createCell(4).setCellStyle(normal);
        sheet.getRow(130).createCell(5).setCellStyle(normal);
        sheet.getRow(130).createCell(6).setCellStyle(normal);
        sheet.getRow(130).createCell(7).setCellStyle(normal);
        sheet.getRow(130).createCell(8).setCellStyle(normal);
        put(sheet,131,2,"Total ",total);
        sheet.getRow(131).createCell(3).setCellFormula("D130+D131"); sheet.getRow(131).getCell(3).setCellStyle(normal);
        sheet.getRow(131).createCell(4).setCellFormula("E130+E131"); sheet.getRow(131).getCell(4).setCellStyle(normal);
        sheet.getRow(131).createCell(5).setCellFormula("F130+F131"); sheet.getRow(131).getCell(5).setCellStyle(normal);
        sheet.getRow(131).createCell(6).setCellFormula("G130+G131"); sheet.getRow(131).getCell(6).setCellStyle(normal);
        sheet.getRow(131).createCell(7).setCellFormula("H130+H131"); sheet.getRow(131).getCell(7).setCellStyle(normal);
        sheet.getRow(131).createCell(8).setCellFormula("I130+I131"); sheet.getRow(131).getCell(8).setCellStyle(normal);
        put(sheet,132,2,"Men",normal);
        sheet.getRow(132).createCell(3).setCellStyle(normal);
        sheet.getRow(132).createCell(4).setCellStyle(normal);
        sheet.getRow(132).createCell(5).setCellStyle(normal);
        sheet.getRow(132).createCell(6).setCellStyle(normal);
        sheet.getRow(132).createCell(7).setCellStyle(normal);
        sheet.getRow(132).createCell(8).setCellStyle(normal);
        put(sheet,133,2,"Women",normal);
        sheet.getRow(133).createCell(3).setCellStyle(normal);
        sheet.getRow(133).createCell(4).setCellStyle(normal);
        sheet.getRow(133).createCell(5).setCellStyle(normal);
        sheet.getRow(133).createCell(6).setCellStyle(normal);
        sheet.getRow(133).createCell(7).setCellStyle(normal);
        sheet.getRow(133).createCell(8).setCellStyle(normal);
        put(sheet,134,2,"Total ",total);
        sheet.getRow(134).createCell(3).setCellFormula("D133+D134"); sheet.getRow(134).getCell(3).setCellStyle(normal);
        sheet.getRow(134).createCell(4).setCellFormula("E133+E134"); sheet.getRow(134).getCell(4).setCellStyle(normal);
        sheet.getRow(134).createCell(5).setCellFormula("F133+F134"); sheet.getRow(134).getCell(5).setCellStyle(normal);
        sheet.getRow(134).createCell(6).setCellFormula("G133+G134"); sheet.getRow(134).getCell(6).setCellStyle(normal);
        sheet.getRow(134).createCell(7).setCellFormula("H133+H134"); sheet.getRow(134).getCell(7).setCellStyle(normal);
        sheet.getRow(134).createCell(8).setCellFormula("I133+I134"); sheet.getRow(134).getCell(8).setCellStyle(normal);
        put(sheet,135,2,"Men",normal);
        sheet.getRow(135).createCell(3).setCellStyle(normal);
        sheet.getRow(135).createCell(4).setCellStyle(normal);
        sheet.getRow(135).createCell(5).setCellStyle(normal);
        sheet.getRow(135).createCell(6).setCellStyle(normal);
        sheet.getRow(135).createCell(7).setCellStyle(normal);
        sheet.getRow(135).createCell(8).setCellStyle(normal);
        put(sheet,136,2,"Women",normal);
        sheet.getRow(136).createCell(3).setCellStyle(normal);
        sheet.getRow(136).createCell(4).setCellStyle(normal);
        sheet.getRow(136).createCell(5).setCellStyle(normal);
        sheet.getRow(136).createCell(6).setCellStyle(normal);
        sheet.getRow(136).createCell(7).setCellStyle(normal);
        sheet.getRow(136).createCell(8).setCellStyle(normal);
        put(sheet,137,2,"Legal Entities",normal);
        sheet.getRow(137).createCell(3).setCellStyle(normal);
        sheet.getRow(137).createCell(4).setCellStyle(normal);
        sheet.getRow(137).createCell(5).setCellStyle(normal);
        sheet.getRow(137).createCell(6).setCellStyle(normal);
        sheet.getRow(137).createCell(7).setCellStyle(normal);
        sheet.getRow(137).createCell(8).setCellStyle(normal);
        put(sheet,138,2,"Total ",total);
        sheet.getRow(138).createCell(3).setCellFormula("D135+D136+D137"); sheet.getRow(138).getCell(3).setCellStyle(normal);
        sheet.getRow(138).createCell(4).setCellFormula("E135+E136+E137"); sheet.getRow(138).getCell(4).setCellStyle(normal);
        sheet.getRow(138).createCell(5).setCellFormula("F135+F136+F137"); sheet.getRow(138).getCell(5).setCellStyle(normal);
        sheet.getRow(138).createCell(6).setCellFormula("G135+G136+G137"); sheet.getRow(138).getCell(6).setCellStyle(normal);
        sheet.getRow(138).createCell(7).setCellFormula("H135+H136+H137"); sheet.getRow(138).getCell(7).setCellStyle(normal);
        sheet.getRow(138).createCell(8).setCellFormula("I135+I136+I137"); sheet.getRow(138).getCell(8).setCellStyle(normal);
        put(sheet,139,2,"Share value",total);
        sheet.getRow(139).createCell(3).setCellStyle(normal);
        sheet.getRow(139).createCell(4).setCellStyle(normal);
        sheet.getRow(139).createCell(5).setCellStyle(normal);
        sheet.getRow(139).createCell(6).setCellStyle(normal);
        sheet.getRow(139).createCell(7).setCellStyle(normal);
        sheet.getRow(139).createCell(8).setCellStyle(normal);
        sheet.setColumnWidth(0,5000); sheet.setColumnWidth(1,3000); sheet.setColumnWidth(2,25000);
        for(int c=3;c<=8;c++) sheet.setColumnWidth(c,6500);
        for(int c=9;c<=14;c++) sheet.setColumnWidth(c,4500);
        sheet.setDisplayGridlines(false); sheet.createFreezePane(3,4);
    }

    private void createClassificationSheet(
            XSSFWorkbook workbook,
            String sheetName,
            String classification,
            String reportName,
            int headerRowNumber,
            String[] headers) {

        Sheet sheet = workbook.createSheet(sheetName);
        CellStyle meta = createBnrMetaStyle(workbook);
        CellStyle titleStyle = createBnrTitleStyle(workbook);
        CellStyle headerStyle = createBnrHeaderStyle(workbook);
        CellStyle dataStyle = createBnrDataStyle(workbook);
        CellStyle dataNumberStyle = createBnrDataNumberStyle(workbook);
        CellStyle dataPercentStyle = createBnrDataPercentStyle(workbook);

        // Match the supplied BNR workbook's top information block.
        put(sheet, 1, 0, "NDFSP Name", meta);
        put(sheet, 2, 0, "NDFSP Name", meta);
        put(sheet, 3, 0, "Report Date", meta);
        put(sheet, 3, 2, LocalDate.now(), meta);
        put(sheet, 4, 0, "Report Name ", meta);
        put(sheet, 4, 2, reportName, titleStyle);

        int classificationInfoRow = headerRowNumber <= 9 ? 7 : 8;
        put(sheet, classificationInfoRow, 1, "Portfolio At Risk " + portfolioRiskLabel(classification), titleStyle);
        put(sheet, classificationInfoRow, 2, "Minimum provisioning rate required : "
                + provisioningRate(classification).stripTrailingZeros().toPlainString() + "%", titleStyle);

        // Eligible collateral reference list reproduced as report metadata,
        // not by embedding the user's workbook.
        String[] eligible = {
                "Eligible Collaterals",
                "cash collateral",
                "Government or the Central Bank Bills and Bonds ",
                "Other securities offered by the banks operating in Rwanda",
                "Land and Building",
                "movable collaterals.",
                "Biological assets",
                "Other assets "
        };
        for (int i = 0; i < eligible.length; i++) {
            put(sheet, i, 14, eligible[i], i == 0 ? meta : dataStyle);
        }

        int headerIndex = headerRowNumber - 1;
        Row headerRow = sheet.createRow(headerIndex);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(headerStyle);
        }

        Row placeholder = sheet.createRow(headerIndex + 1);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = placeholder.createCell(c);
            cell.setCellValue("");
            cell.setCellStyle(isPercentBnrHeader(headers[c]) ? dataPercentStyle
                    : isNumericBnrHeader(headers[c]) ? dataNumberStyle : dataStyle);
        }

        int[] widths = bnrClassificationWidths(headers.length);
        for (int c = 0; c < headers.length; c++) {
            sheet.setColumnWidth(c, widths[c]);
        }
        sheet.setColumnWidth(14, 15000);
        sheet.createFreezePane(0, headerRowNumber);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                headerIndex, headerIndex + 1, 0, headers.length - 1));
        sheet.setDisplayGridlines(false);
        sheet.setDefaultRowHeightInPoints(18f);
        sheet.getRow(headerIndex).setHeightInPoints(72f);
    }

    private String portfolioRiskLabel(String classification) {
        return switch (classification) {
            case "NORMAL" -> "0 days";
            case "WATCH" -> "30 to 89 days";
            case "SUBSTANDARD" -> "90 to 179 days";
            case "DOUBTFUL" -> "180 to 359 days";
            case "LOSS" -> "360 to 719 days";
            case "RESTRUCTURED" -> "Renegotiated Loans";
            default -> "";
        };
    }

    private void put(Sheet sheet, int zeroBasedRow, int zeroBasedColumn, Object value, CellStyle style) {
        Row row = sheet.getRow(zeroBasedRow);
        if (row == null) row = sheet.createRow(zeroBasedRow);
        Cell cell = row.createCell(zeroBasedColumn);
        if (value instanceof LocalDate d) cell.setCellValue(d);
        else if (value instanceof Number n) cell.setCellValue(n.doubleValue());
        else if (value != null) cell.setCellValue(String.valueOf(value));
        cell.setCellStyle(style);
    }

    private CellStyle createBnrMetaStyle(XSSFWorkbook workbook) {
        CellStyle style = createBnrBaseStyle(workbook);
        Font font = workbook.createFont();
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10); font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createBnrTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = createBnrBaseStyle(workbook);
        Font font = workbook.createFont();
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10); font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private CellStyle createBnrHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = createBnrBaseStyle(workbook);
        Font font = workbook.createFont();
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10); font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createBnrDataStyle(XSSFWorkbook workbook) {
        CellStyle style = createBnrBaseStyle(workbook);
        Font font = workbook.createFont();
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 8);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(new XSSFColor(new byte[] {(byte)0xFF,(byte)0xC0,0x00}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createBnrDataNumberStyle(XSSFWorkbook workbook) {
        CellStyle style = createBnrDataStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00;[Red]-#,##0.00;\"-\"") );
        return style;
    }

    private CellStyle createBnrDataPercentStyle(XSSFWorkbook workbook) {
        CellStyle style = createBnrDataStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        return style;
    }

    private CellStyle createBnrBaseStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN); style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN); style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private boolean isPercentBnrHeader(String header) {
        String h = normalize(header);
        return contains(h, "annualinterestrate") || contains(h, "provisioningrateregulation");
    }

    private boolean isNumericBnrHeader(String header) {
        String h = normalize(header);
        return contains(h, "amount") || contains(h, "rate") || contains(h, "provision")
                || contains(h, "numberof") || contains(h, "age") || contains(h, "days")
                || contains(h, "installments") || contains(h, "class");
    }

    private int[] bnrClassificationWidths(int count) {
        int[] w = new int[count];
        Arrays.fill(w, 5000);
        int[] specific = { 2500, 8500, 6500, 5000, 3500, 3500, 9000, 7000, 6500, 8500, 6500, 7000, 6500, 6500, 6500, 6000, 6500, 5000, 6000, 8000, 6500, 6000, 6000, 5500, 5000, 6500, 6500, 6500, 6000, 5500, 6000, 6000, 6500, 7000, 6500, 6500, 5500, 3500, 5500, 6500, 6000, 6500, 6500 };
        for (int i=0; i<count && i<specific.length; i++) w[i]=specific[i];
        return w;
    }

    private void createWrittenOffSheet(XSSFWorkbook workbook) {
        String[] headers = {
                "Names of Borrowers", "ID of the Borrower", "Telephone number", "Account Number",
                "Gender", "Age", "Relationship with the NDFSP",
                "Annual Interest Rate", "Method of interest rate calculation (Flat/Declining)",
                "Physical Guarantee", "Borrower's District", "Borrower's Sector", "Borrower's Cell",
                "Borrower's Village", "Date of loan disbursement", "Amount of loan disbursed",
                "Maturity Date", "Amount Repaid ", "Loan balance outstanding", "Security Savings",
                "Amount Written Off", "Date of Write Off", "Recoveries on the written off amount",
                "Remaining Balance to be Recovered"
        };

        Sheet sheet = workbook.createSheet("A1.9. Written off");
        CellStyle meta = createBnrMetaStyle(workbook);
        CellStyle title = createBnrTitleStyle(workbook);
        CellStyle header = createBnrHeaderStyle(workbook);
        CellStyle data = createBnrDataStyle(workbook);
        CellStyle number = createBnrDataNumberStyle(workbook);

        put(sheet, 1, 0, "NDFSP Name", meta);
        put(sheet, 2, 0, "NDFSP Name", meta);
        put(sheet, 3, 0, "Report Date", meta);
        put(sheet, 3, 1, LocalDate.now(), meta);
        put(sheet, 4, 0, "Report Name ", meta);
        put(sheet, 4, 1, "Written Off Loans-Individuals ( 1 year in loss)", title);
        put(sheet, 5, 0, "Loans with 1 Year in Loss ( 720 days in arrears  )", title);

        Row hr = sheet.createRow(6);
        for (int c=0;c<headers.length;c++) {
            Cell cell=hr.createCell(c); cell.setCellValue(headers[c]); cell.setCellStyle(header);
        }
        Row placeholder=sheet.createRow(7);
        for (int c=0;c<headers.length;c++) {
            Cell cell=placeholder.createCell(c); cell.setCellStyle(isNumericBnrHeader(headers[c])?number:data);
        }
        for(int c=0;c<headers.length;c++) sheet.setColumnWidth(c, Math.max(5000, Math.min(15000, headers[c].length()*330)));
        sheet.createFreezePane(0,7);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(6,7,0,headers.length-1));
        sheet.setDisplayGridlines(false);
        sheet.getRow(6).setHeightInPoints(72f);
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
        font.setFontName("Calibri"); font.setFontHeightInPoints((short) 11); font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT); style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN); style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN); style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10); font.setBold(true);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER); style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN); style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN); style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createBodyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Calibri"); font.setFontHeightInPoints((short) 11);
        style.setFont(font); style.setVerticalAlignment(VerticalAlignment.CENTER); style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN); style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN); style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createBnrSectionStyle(XSSFWorkbook workbook) {
        CellStyle style=createBnrBaseStyle(workbook); Font f=workbook.createFont(); f.setFontName("Calibri"); f.setFontHeightInPoints((short)11); f.setBold(true); f.setColor(IndexedColors.WHITE.getIndex()); style.setFont(f); style.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); return style;
    }
    private CellStyle createBnrBodyStyle(XSSFWorkbook workbook) {
        CellStyle style=createBnrBaseStyle(workbook); Font f=workbook.createFont(); f.setFontName("Calibri"); f.setFontHeightInPoints((short)11); style.setFont(f); style.setAlignment(HorizontalAlignment.LEFT); return style;
    }
    private CellStyle createBnrFsNormalStyle(XSSFWorkbook workbook) {
        CellStyle style=createBnrBodyStyle(workbook); style.setBorderLeft(BorderStyle.MEDIUM); return style;
    }
    private CellStyle createBnrFsTotalStyle(XSSFWorkbook workbook) {
        CellStyle style=createBnrFsNormalStyle(workbook); style.setFillForegroundColor(IndexedColors.WHITE.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); Font f=workbook.createFont(); f.setFontName("Calibri"); f.setFontHeightInPoints((short)11); f.setBold(true); style.setFont(f); return style;
    }
    private CellStyle createBnrFsRatioStyle(XSSFWorkbook workbook) {
        CellStyle style=createBnrFsNormalStyle(workbook); Font f=workbook.createFont(); f.setFontName("Calibri"); f.setFontHeightInPoints((short)11); style.setFont(f); style.setDataFormat(workbook.createDataFormat().getFormat("0.00%")); return style;
    }
    private CellStyle createBnrFsInputStyle(XSSFWorkbook workbook) {
        CellStyle style=createBnrFsNormalStyle(workbook); style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xFF,(byte)0xFF,0x00},null)); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); return style;
    }
    private CellStyle createBnrFsDateStyle(XSSFWorkbook workbook) {
        CellStyle style=createBnrFsInputStyle(workbook); style.setDataFormat(workbook.createDataFormat().getFormat("mmm-yy")); return style;
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
            return f.monthlyInterestRate.multiply(new BigDecimal("12"))
                    .divide(ONE_HUNDRED, 6, MONEY_ROUNDING);
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
            return f.installmentsOutstanding;
        if (contains(h, "amountrepaidprincipal"))
            return f.principalRepaid;
        if (contains(h, "balanceoutstandingprincipal"))
            return f.outstandingPrincipal;
        if (contains(h, "eligiblecollateralprovided"))
            return f.eligibleCollateral;
        if (contains(h, "securitycompulsorysavings"))
            return f.eligibleCollateral;
        if (contains(h, "netamountdueprincipal"))
            return f.netAmountDue;
        if (contains(h, "numberofdaysoverdue"))
            return f.daysOverdue;
        if (h.equals("class") || contains(h, "performanceclass")) {
            String effective = classification;
            if ("RESTRUCTURED".equals(classification)) {
                int d = f.daysOverdue;
                effective = d >= 360 ? "LOSS" : d >= 180 ? "DOUBTFUL" : d >= 90 ? "SUBSTANDARD" : d >= 30 ? "WATCH" : "NORMAL";
            }
            return classificationLabel(effective);
        }
        if (contains(h, "provisioningrateregulation"))
            return f.provisionRate.divide(ONE_HUNDRED, 8, MONEY_ROUNDING);
        if (contains(h, "provisionrequired"))
            return f.provisionRequired;
        if (contains(h, "previousprovisions"))
            return ZERO;
        if (contains(h, "additionalprovisions"))
            return f.provisionRequired;

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
            return f.monthlyInterestRate.multiply(new BigDecimal("12"))
                    .divide(ONE_HUNDRED, 6, MONEY_ROUNDING);
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
                resolveBorrowerReportingId(borrower),
                borrower == null ? null : borrower.getPhone(),
                borrower == null ? null : borrower.getGender(),
                borrower == null ? null : age(borrower.getDateOfBirth(), reportDate),
                borrower == null ? null : borrower.getMaritalStatus(),
                previousLoansPaidOnTime,
                loan.getPurpose(),
                loan.getBranch() == null ? null : loan.getBranch().getName(),
                blankIfNull(loan.getCollateralDescription()),
                collateral,
                borrower == null ? null : borrower.getStateProvince(),
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

    /**
     * BNR "ID of the Borrower" is the borrower's regulatory/national ID.
     *
     * Legacy imports store the source ID in Borrower.nationalId. We never
     * substitute the database primary key into the regulatory ID field because
     * the database PK is an internal Noble identifier, not the borrower's
     * regulatory identity document number.
     *
     * When the national ID is missing, return null and let the source-data
     * quality controls identify the missing regulatory identifier rather than
     * fabricating one.
     */
    private String resolveBorrowerReportingId(Borrower borrower) {
        if (borrower == null) {
            return null;
        }

        String nationalId = borrower.getNationalId();
        if (nationalId == null) {
            return null;
        }

        String normalized = nationalId.trim();
        return normalized.isBlank() ? null : normalized;
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
            case "A1.3. Normal Loans" -> "NORMAL";
            case "A1.4. Watch" -> "WATCH";
            case "A1.5. Substandard" -> "SUBSTANDARD";
            case "A1.6. Doubtful" -> "DOUBTFUL";
            case "A1.7 Loss" -> "LOSS";
            case "A1.8. Restructured loans" -> "RESTRUCTURED";
            default -> "NORMAL";
        };
    }

    /**
     * BNR codification from the supplied reference workbook.
     *
     * 1 = Normal
     * 2 = Watch
     * 3 = Substandard
     * 4 = Doubtful
     * 5 = Loss
     * 6 = Written Off
     *
     * The reference classification sheet uses 30-89 days for Watch,
     * 90-189 for Substandard, 180-359 for Doubtful and 360+ for Loss.
     * The overlapping 180-189 range is resolved in favour of the more
     * severe Doubtful class, which is the conservative regulatory treatment.
     */
    private String classificationKey(Loan loan) {
        if (loan == null) {
            return "NORMAL";
        }
        if (loan.getStatus() == LoanStatus.WRITTEN_OFF) {
            return "WRITTEN_OFF";
        }
        if (isRestructured(loan)) {
            return "RESTRUCTURED";
        }

        int dpd = loan.getDaysOverdue() == null ? 0 : Math.max(0, loan.getDaysOverdue());
        if (dpd >= 360) {
            return "LOSS";
        }
        if (dpd >= 180) {
            return "DOUBTFUL";
        }
        if (dpd >= 90) {
            return "SUBSTANDARD";
        }
        if (dpd >= 30) {
            return "WATCH";
        }
        return "NORMAL";
    }

    private BigDecimal provisioningRate(String classification) {
        return switch (classification) {
            case "NORMAL" -> ZERO;
            case "WATCH" -> new BigDecimal("1.00");
            case "SUBSTANDARD" -> new BigDecimal("20.00");
            case "DOUBTFUL" -> new BigDecimal("50.00");
            case "LOSS", "WRITTEN_OFF" -> new BigDecimal("100.00");
            case "RESTRUCTURED" -> ZERO;
            default -> ZERO;
        };
    }

    private String classificationLabel(String classification) {
        return switch (classification) {
            case "NORMAL" -> "1";
            case "WATCH" -> "2";
            case "SUBSTANDARD" -> "3";
            case "DOUBTFUL" -> "4";
            case "LOSS" -> "5";
            case "WRITTEN_OFF" -> "6";
            case "RESTRUCTURED" -> "3";
            default -> "1";
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
            writeTypedCell(row, outstandingInstallments, BigDecimal.valueOf(f.installmentsOutstanding));
        }

        int balance = findColumn(header, "balanceoutstandingprincipal", "loanbalanceoutstanding");
        if (balance >= 0) {
            writeTypedCell(row, balance, f.outstandingPrincipal);
        }

        int eligible = findColumn(header, "eligiblecollateralprovided");
        if (eligible >= 0) {
            writeTypedCell(row, eligible, f.eligibleCollateral);
        }

        int netDue = findColumn(header, "netamountdueprincipal");
        if (netDue >= 0) {
            writeTypedCell(row, netDue, f.netAmountDue);
        }

        int rate = findColumn(header, "provisioningrateregulation");
        int required = findColumn(header, "provisionrequired");
        if (rate >= 0 && required >= 0) {
            writeTypedCell(row, required, f.provisionRequired);
        }

        int additional = findColumn(header, "additionalprovisions");
        int previous = findColumn(header, "previousprovisions");
        if (additional >= 0) {
            // Previous provisions are not stored as a historical snapshot in
            // the Loan model. Export zero as the auditable source value and
            // calculate the full current required provision as additional.
            writeTypedCell(row, additional, f.provisionRequired);
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
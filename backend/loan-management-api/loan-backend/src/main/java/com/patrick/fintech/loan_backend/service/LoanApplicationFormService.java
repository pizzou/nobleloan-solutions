package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Read-only snapshot of saved borrower and loan application data.
 * Does not create, approve, change or disburse a loan.
 * This form is not the signed loan agreement.
 */
@Service
public class LoanApplicationFormService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final String LOGO = "/branding/nls-logo.png";

    public byte[] render(Loan loan) {
        if (loan == null || loan.getBorrower() == null) {
            throw new IllegalArgumentException("Loan and borrower are required for the application form.");
        }
        Borrower borrower = loan.getBorrower();
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFHeader header = document.createHeader(HeaderFooterType.DEFAULT);
            XWPFParagraph brand = header.createParagraph();
            brand.setAlignment(ParagraphAlignment.CENTER);
            try (InputStream logo = LoanApplicationFormService.class.getResourceAsStream(LOGO)) {
                if (logo == null) {
                    throw new IllegalStateException("Approved NLS logo is missing: " + LOGO);
                }
                brand.createRun().addPicture(logo, Document.PICTURE_TYPE_PNG,
                        "nls-logo.png", Units.toEMU(165), Units.toEMU(55));
            }
            center(header.createParagraph(), "NOBLE LOAN SOLUTIONS LTD", 10, true);
            center(header.createParagraph(), "CONFIDENTIAL - LOAN APPLICATION", 8, true);

            // Reserve enough room for the repeating branded header on every page.
            var section = document.getDocument().getBody().isSetSectPr()
                    ? document.getDocument().getBody().getSectPr()
                    : document.getDocument().getBody().addNewSectPr();
            var margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
            margins.setTop(BigInteger.valueOf(2500));
            margins.setBottom(BigInteger.valueOf(1000));
            margins.setLeft(BigInteger.valueOf(1100));
            margins.setRight(BigInteger.valueOf(1100));
            margins.setHeader(BigInteger.valueOf(260));

            XWPFParagraph heading = document.createParagraph();
            heading.setAlignment(ParagraphAlignment.CENTER);
            heading.setSpacingBefore(450);
            XWPFRun headingRun = heading.createRun();
            headingRun.setBold(true);
            headingRun.setFontSize(15);
            headingRun.setText("LOAN APPLICATION FORM");

            paragraph(document, "Application reference: " + value(loan.getReferenceNumber()));
            paragraph(document, "Generated on: " + LocalDate.now().format(DATE)
                    + "    |    Current loan status: " + value(loan.getStatus()));
            paragraph(document, "This is a staff-generated copy of saved application data. "
                    + "It is not proof of approval, payment, acceptance, or a signed loan contract.");

            section(document, "1. APPLICANT INFORMATION");
            XWPFTable applicant = table(document);
            row(applicant, "Applicant full name", borrower.getFullName());
            row(applicant, "National ID", borrower.getNationalId());
            row(applicant, "Date of birth", date(borrower.getDateOfBirth()));
            row(applicant, "Gender", borrower.getGender());
            row(applicant, "Nationality", borrower.getNationality());
            row(applicant, "Marital status", borrower.getMaritalStatus());
            row(applicant, "Phone", borrower.getPhone());
            row(applicant, "Alternative phone", borrower.getAlternatePhone());
            row(applicant, "Email", borrower.getEmail());
            row(applicant, "Address", borrower.getAddress());
            row(applicant, "Province", borrower.getPhysicalAddressProvince());
            row(applicant, "District", borrower.getPhysicalAddressDistrict());
            row(applicant, "Sector", borrower.getPhysicalAddressSector());
            row(applicant, "Cell", borrower.getPhysicalAddressCell());
            row(applicant, "Village", borrower.getPhysicalAddressVillage());

            section(document, "2. EMPLOYMENT AND FINANCIAL DETAILS");
            XWPFTable income = table(document);
            row(income, "Employer / business", borrower.getEmployerName());
            row(income, "Employment type", borrower.getEmploymentType());
            row(income, "Job title", borrower.getJobTitle());
            row(income, "Monthly income (RWF)", money(borrower.getMonthlyIncomeDecimal()));
            row(income, "Monthly expenses (RWF)", money(borrower.getMonthlyExpensesDecimal()));
            row(income, "Bank", borrower.getBankName());
            row(income, "Bank account", borrower.getBankAccountNumber());
            row(income, "Bank branch", borrower.getBankBranch());

            section(document, "3. REQUESTED LOAN");
            XWPFTable request = table(document);
            row(request, "Loan reference", loan.getReferenceNumber());
            row(request, "Loan product / type", value(loan.getLoanType()));
            row(request, "Requested amount (" + value(loan.getCurrency()) + ")",
                    money(loan.getRequestedAmount() == null ? loan.getAmount() : loan.getRequestedAmount()));
            row(request, "Repayment period (months)", value(loan.getDurationMonths()));
            row(request, "Repayment frequency", value(loan.getRepaymentFrequency()));
            row(request, "Loan purpose", loan.getPurpose());
            row(request, "Proposed start date", date(loan.getStartDate()));

            section(document, "4. PROPOSED COLLATERAL");
            XWPFTable security = table(document);
            row(security, "Collateral type", loan.getCollateralType());
            row(security, "Description", loan.getCollateralDescription());
            row(security, "Declared valuation (" + value(loan.getCurrency()) + ")",
                    money(loan.getCollateralValue()));
            paragraph(document, "For immovable property, a separate expertise / valuation report "
                    + "must be submitted and verified as required by the system. "
                    + "The report's review status must be checked in the loan documents panel.");

            section(document, "5. MARITAL AND SPOUSAL INFORMATION");
            XWPFTable spouse = table(document);
            row(spouse, "Spouse full name", borrower.getSpouseFullName());
            row(spouse, "Spouse national ID", borrower.getSpouseNationalId());
            row(spouse, "Spouse phone", borrower.getSpousePhone());
            row(spouse, "Spouse consent recorded", value(borrower.getSpouseConsent()));
            row(spouse, "Single-status certificate number", borrower.getSingleCertificateNumber());

            section(document, "6. BORROWER DECLARATION AND STAFF REVIEW");
            paragraph(document, "I confirm that the information provided in this application is "
                    + "accurate and may be checked in accordance with the lender's applicable policies.");
            // Blank wet-signature areas for the borrower and the company's
            // authorized representative, matching the contract's two-party layout.
            // Never insert a stored signature or imply that the form was signed.
            XWPFTable signatures = document.createTable(4, 2);
            signatures.setWidth("100%");
            setCell(signatures.getRow(0).getCell(0), "BORROWER / APPLICANT", true);
            setCell(signatures.getRow(0).getCell(1), "FOR NOBLE LOAN SOLUTIONS LTD", true);
            setCell(signatures.getRow(1).getCell(0), "Full name: " + value(borrower.getFullName()), false);
            setCell(signatures.getRow(1).getCell(1), "Authorized representative: _____________________", false);
            setCell(signatures.getRow(2).getCell(0), "Signature: ______________________________", false);
            setCell(signatures.getRow(2).getCell(1), "Signature: ______________________________", false);
            setCell(signatures.getRow(3).getCell(0), "Date: _________________________________", false);
            setCell(signatures.getRow(3).getCell(1), "Date: ____________________   Company stamp: __________", false);
            paragraph(document, "Loan officer (prepared/reviewed by): __________________    Date: __________");
            paragraph(document, "Reviewer remarks: ______________________________________________");
            paragraph(document, "IMPORTANT: Signature spaces are blank. Downloading or printing this "
                    + "application does not establish that either party has signed, does not approve "
                    + "the loan, and does not replace the signed loan agreement. Verify all original "
                    + "evidence and required documents in the system before approval.");

            document.write(output);
            return output.toByteArray();
        } catch (IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException ex) {
            throw new IllegalStateException("Unable to generate loan application form.", ex);
        }
    }

    private static void center(XWPFParagraph paragraph, String text, int size, boolean bold) {
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Aptos");
        run.setFontSize(size);
        run.setBold(bold);
        run.setText(text);
    }

    private static void section(XWPFDocument doc, String title) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setSpacingBefore(220);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(11);
        run.setColor("17324D");
        run.setText(title);
    }

    private static void paragraph(XWPFDocument doc, String content) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setSpacingAfter(90);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Aptos");
        run.setFontSize(9);
        run.setText(content);
    }

    private static XWPFTable table(XWPFDocument doc) {
        XWPFTable table = doc.createTable();
        table.setWidth("100%");
        return table;
    }

    private static void row(XWPFTable table, String label, String data) {
        XWPFTableRow row = table.getNumberOfRows() == 1
                && table.getRow(0).getCell(0).getText().isEmpty()
                && table.getRow(0).getCell(1).getText().isEmpty()
                        ? table.getRow(0)
                        : table.createRow();
        setCell(row.getCell(0), label, true);
        setCell(row.getCell(1), value(data), false);
    }

    private static void setCell(XWPFTableCell cell, String data, boolean bold) {
        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Aptos");
        run.setFontSize(9);
        run.setBold(bold);
        run.setText(data);
    }

    private static String value(Object val) {
        return val == null || val.toString().isBlank() ? "Not recorded" : val.toString();
    }

    private static String date(LocalDate date) {
        return date == null ? "Not recorded" : date.format(DATE);
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "Not recorded" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

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
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Read-only bilingual application form based on the original NLS paper form.
 * A field-by-field document (no tables), using the approved contract logo.
 * Older loans may not contain every answer from the original online form.
 * Missing answers remain explicitly marked as not recorded.
 *
 * Downloading this form does not approve, amend, sign or disburse a loan.
 */
@Service
public class LoanApplicationFormService {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String LOGO = "/branding/nls-logo.png";
    private static final String MISSING = "____________________________ (Not recorded)";

    public byte[] render(Loan loan) {
        if (loan == null || loan.getBorrower() == null) {
            throw new IllegalArgumentException(
                    "Loan and borrower are required to generate the application form.");
        }

        Borrower b = loan.getBorrower();

        try (XWPFDocument doc = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addHeader(doc);
            setPageMargins(doc);

            heading(doc, "LOAN APPLICATION FORM", 16);
            centered(doc, "IFISHI YO GUSABA INGUZANYO", 11, true);
            centered(doc, "NOBLE LOAN SOLUTIONS LTD", 9, true);

            field(doc, "Application reference / Nimero y'ubusabe", loan.getReferenceNumber());
            field(doc, "Form printed on / Itariki yacapiweho",
                    LocalDate.now().format(DATE));
            field(doc, "Current loan status / Imiterere y'inguzanyo", loan.getStatus());
            note(doc, "STAFF COPY — Populated from the information currently saved in the system. "
                    + "Unrecorded fields are left blank for verification. "
                    + "This form is not a signed loan agreement or evidence of approval.");

            heading(doc, "1. Applicant Information (Amakuru y'usaba)", 12);
            field(doc, "Full name / Amazina yose", b.getFullName());
            field(doc, "First name / Izina", b.getFirstName());
            field(doc, "Last name / Izina ry'umuryango", b.getLastName());
            field(doc, "Date of birth / Itariki y'amavuko", date(b.getDateOfBirth()));
            field(doc, "Place of birth / Aho yavukiye", b.getPlaceOfBirth());
            field(doc, "National ID / Indangamuntu", b.getNationalId());
            field(doc, "Passport number / Pasiporo (if applicable)", b.getPassportNumber());
            field(doc, "Gender / Igitsina", b.getGender());
            field(doc, "Nationality / Ubwenegihugu", b.getNationality());
            field(doc, "Marital status / Irangamimerere", b.getMaritalStatus());
            field(doc, "Phone / Telefone", b.getPhone());
            field(doc, "Alternative phone / Indi telefone", b.getAlternatePhone());
            field(doc, "Email / Imeyili", b.getEmail());
            field(doc, "Home address / Aho atuye", b.getAddress());
            field(doc, "City / Umujyi", b.getCity());
            field(doc, "Province / Intara", first(b.getPhysicalAddressProvince(), b.getStateProvince()));
            field(doc, "District / Akarere", b.getPhysicalAddressDistrict());
            field(doc, "Sector / Umurenge", b.getPhysicalAddressSector());
            field(doc, "Cell / Akagari", b.getPhysicalAddressCell());
            field(doc, "Village / Umudugudu", b.getPhysicalAddressVillage());
            field(doc, "Country / Igihugu", b.getCountry());

            heading(doc, "2. Employment / Business Information (Akazi cyangwa Ubucuruzi)", 12);
            field(doc, "Employment type / Uburyo bw'akazi", b.getEmploymentType());
            field(doc, "Job / Occupation / Umurimo ukora", b.getJobTitle());
            field(doc, "Employer / Business name / Umukoresha cyangwa ubucuruzi",
                    b.getEmployerName());
            field(doc, "Nature of business / Ubwoko bw'ubucuruzi",
                    null); // The current Borrower record does not persist this distinct public-form answer.
            field(doc, "Other employment type / Ubundi bwoko bw'akazi",
                    null); // The public form may collect this but it has no distinct saved Borrower field.
            field(doc, "Workplace / Aho ukorera", null);
            field(doc, "Time in employment / Igihe amaze mu kazi", null);
            field(doc, "Monthly income / Umushahara ku kwezi (RWF)",
                    money(b.getMonthlyIncomeDecimal()));
            field(doc, "Monthly expenses / Amafaranga asohoka ku kwezi (RWF)",
                    money(b.getMonthlyExpensesDecimal()));
            field(doc, "Bank name / Banki", b.getBankName());
            field(doc, "Bank account number / Konti", b.getBankAccountNumber());
            field(doc, "Bank branch / Ishami rya banki", b.getBankBranch());

            heading(doc, "3. Loan Details (Amakuru ku nguzanyo)", 12);
            field(doc, "Loan type / Ubwoko bw'inguzanyo", loan.getLoanType());
            field(doc, "Amount requested / Amafaranga asaba (" + currency(loan) + ")",
                    money(loan.getRequestedAmount() == null
                            ? loan.getAmount() : loan.getRequestedAmount()));
            field(doc, "Purpose / Icyo azayifashisha", loan.getPurpose());
            field(doc, "Repayment period / Igihe cyo kwishyura (months)",
                    loan.getDurationMonths());
            field(doc, "Mode of repayment / Uburyo bwo kwishyura",
                    loan.getRepaymentFrequency());
            field(doc, "Proposed start date / Itariki iteganyijwe",
                    date(loan.getStartDate()));
            note(doc, "Repayment frequency: Monthly / Buri kwezi; Weekly / Buri cyumweru; "
                    + "Other / Ikindi. The saved repayment frequency above takes precedence.");

            heading(doc, "4. Collateral / Guarantee (Ingwate cyangwa Uhamya)", 12);
            field(doc, "Collateral type / Ubwoko bw'ingwate",
                    first(loan.getCollateralType(), "NONE"));
            field(doc, "Collateral description / Ibisobanuro by'ingwate",
                    loan.getCollateralDescription());
            field(doc, "Collateral value / Agaciro k'ingwate (" + currency(loan) + ")",
                    money(loan.getCollateralValue()));
            field(doc, "Guarantor / Umwishingizi (if any)", null);
            field(doc, "Guarantor full name / Amazina y'umwishingizi", null);
            field(doc, "Guarantor phone / Telefone y'umwishingizi", null);
            field(doc, "Guarantor national ID / Indangamuntu y'umwishingizi", null);
            note(doc, "Guarantor fields are blank because the current public application "
                    + "does not store a separate guarantor record on the loan. "
                    + "For immovable collateral, verify the expertise report in Documents.");

            heading(doc, "5. Marital / Spouse Information (Amakuru y'uwo bashakanye)", 12);
            field(doc, "Spouse full name / Amazina y'uwo bashakanye",
                    b.getSpouseFullName());
            field(doc, "Spouse national ID / Indangamuntu", b.getSpouseNationalId());
            field(doc, "Spouse phone / Telefone", b.getSpousePhone());
            field(doc, "Spouse consent recorded / Ubwumvikane", b.getSpouseConsent());
            field(doc, "Single certificate number / Icyemezo cy'ubuseribateri",
                    b.getSingleCertificateNumber());

            heading(doc, "6. Declaration (Isezerano)", 12);
            body(doc, "Nemeza ko amakuru ntanze ari ukuri kandi mpaye uburenganzira "
                    + "ikigo kuyagenzura ndetse no kureba amakuru yerekeranye "
                    + "n'imyenda mu kigo gishinzwe amakuru ku myenda (CRB).");
            body(doc, "I declare that the information in this application is true "
                    + "and authorize the lender to verify it and conduct lawful "
                    + "credit-reference checks.");
            body(doc, "This is a print-ready declaration for signature. "
                    + "Printing the form does not establish consent or signature.");

            heading(doc, "7. Signatures (Imikono)", 12);
            signature(doc, "BORROWER / APPLICANT (USABA INGUZANYO)");
            field(doc, "Borrower name / Amazina", b.getFullName());
            blank(doc, "Borrower signature / Umukono");
            blank(doc, "Date / Itariki");

            signature(doc, "FOR NOBLE LOAN SOLUTIONS LTD (UHAGARARIYE IKIGO)");
            blank(doc, "Representative full name / Amazina");
            blank(doc, "Position / Umwanya");
            blank(doc, "Representative signature / Umukono");
            blank(doc, "Date / Itariki");
            blank(doc, "Company stamp / Kashe");

            heading(doc, "8. Internal Review (Ku bakozi b'ikigo)", 11);
            blank(doc, "Prepared by / Loan officer");
            blank(doc, "Checked by / Reviewer");
            blank(doc, "Review date / Itariki");
            blank(doc, "Remarks / Ibyitonderwa");

            note(doc, "This is a copy of saved application information, not the executed "
                    + "loan agreement. No signature is automatically inserted. "
                    + "Required supporting documents and expertise must be reviewed separately.");

            doc.write(out);
            return out.toByteArray();
        } catch (IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IllegalStateException("Unable to generate loan application form.", e);
        }
    }

    private static void addHeader(XWPFDocument doc)
            throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph brand = header.createParagraph();
        brand.setAlignment(ParagraphAlignment.CENTER);
        brand.setSpacingAfter(10);
        try (InputStream logo = LoanApplicationFormService.class
                .getResourceAsStream(LOGO)) {
            if (logo == null) {
                throw new IllegalStateException(
                        "Approved Noble Loan Solutions logo is missing: " + LOGO);
            }
            brand.createRun().addPicture(logo, Document.PICTURE_TYPE_PNG,
                    "nls-logo.png", Units.toEMU(165), Units.toEMU(55));
        }
        centered(header.createParagraph(), "NOBLE LOAN SOLUTIONS LTD", 9, true);
        centered(header.createParagraph(), "CONFIDENTIAL — LOAN APPLICATION", 8, true);
    }

    private static void setPageMargins(XWPFDocument doc) {
        var section = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        var margins = section.isSetPgMar()
                ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(2450));
        margins.setBottom(BigInteger.valueOf(1000));
        margins.setLeft(BigInteger.valueOf(1150));
        margins.setRight(BigInteger.valueOf(1150));
        margins.setHeader(BigInteger.valueOf(260));
    }

    private static void heading(XWPFDocument doc, String title, int fontSize) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(225);
        p.setSpacingAfter(100);
        p.setKeepWithNext(true);
        XWPFRun r = p.createRun();
        r.setText(title);
        r.setBold(true);
        r.setFontSize(fontSize);
        r.setFontFamily("Aptos");
        r.setColor("17324D");
    }

    private static void centered(XWPFParagraph p, String value,
            int fontSize, boolean bold) {
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setText(value);
        r.setBold(bold);
        r.setFontSize(fontSize);
        r.setFontFamily("Aptos");
        r.setColor("17324D");
    }

    private static void field(XWPFDocument doc, String label, Object value) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(70);
        p.setKeepTogether(true);
        XWPFRun key = p.createRun();
        key.setBold(true);
        key.setFontFamily("Aptos");
        key.setFontSize(9);
        key.setText(label + ":  ");
        XWPFRun val = p.createRun();
        val.setFontFamily("Aptos");
        val.setFontSize(9);
        val.setText(asText(value));
    }

    private static void blank(XWPFDocument doc, String label) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(135);
        XWPFRun r = p.createRun();
        r.setFontSize(9);
        r.setText(label + ":  ____________________________________________");
    }

    private static void signature(XWPFDocument doc, String label) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(170);
        p.setSpacingAfter(90);
        XWPFRun r = p.createRun();
        r.setFontFamily("Aptos");
        r.setFontSize(10);
        r.setBold(true);
        r.setText(label);
    }

    private static void body(XWPFDocument doc, String value) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(110);
        XWPFRun r = p.createRun();
        r.setFontFamily("Aptos");
        r.setFontSize(9);
        r.setText(value);
    }

    private static void note(XWPFDocument doc, String value) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(120);
        XWPFRun r = p.createRun();
        r.setFontFamily("Aptos");
        r.setFontSize(8);
        r.setItalic(true);
        r.setColor("555555");
        r.setText(value);
    }

    private static String asText(Object value) {
        if (value == null) return MISSING;
        String text = value.toString().trim();
        return text.isEmpty() ? MISSING : text;
    }

    private static String first(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String date(LocalDate date) {
        return date == null ? null : date.format(DATE);
    }

    private static String money(BigDecimal amount) {
        return amount == null ? null :
                amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String currency(Loan loan) {
        return first(loan.getCurrency(), "RWF").toUpperCase(Locale.ROOT);
    }
}

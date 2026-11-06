
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanApplicationSnapshot;
import com.patrick.fintech.loan_backend.repository.LoanApplicationSnapshotRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only printable loan application, styled after the
 * Noble Loan Solutions bilingual paper form.
 *
 * Original public application answers are used when a
 * saved application snapshot exists.
 *
 * Older loans use existing saved borrower and loan data.
 *
 * The original loan and borrower are NEVER modified.
 * Unsaved original responses remain explicitly blank.
 *
 * This document is not evidence of signature, approval,
 * disbursement, or borrower consent.
 */
@Service
@RequiredArgsConstructor
public class LoanApplicationFormService {

    private final LoanApplicationSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String LOGO = "/branding/nls-logo.png";

    private static final String UNKNOWN = "________________________ (Not recorded)";

    /**
     * Generate a DOCX application form from an existing loan.
     *
     * This is a read-only operation.
     */
    public byte[] render(Loan loan) {

        if (loan == null || loan.getBorrower() == null) {
            throw new IllegalArgumentException(
                    "Loan and borrower are required for the application form.");
        }

        Borrower b = loan.getBorrower();

        Map<String, Object> answers = originalAnswers(loan);

        try (
                XWPFDocument doc = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            createBrandHeader(doc);
            setMargins(doc);

            center(
                    doc.createParagraph(),
                    "LOAN APPLICATION FORM",
                    16,
                    true);

            center(
                    doc.createParagraph(),
                    "IFISHI YO GUSABA INGUZANYO",
                    11,
                    true);

            info(
                    doc,
                    "Application reference / Nimero y'ubusabe",
                    loan.getReferenceNumber());

            info(
                    doc,
                    "Printed on / Itariki",
                    LocalDate.now().format(DATE));

            info(
                    doc,
                    "Loan status / Imiterere y'inguzanyo",
                    loan.getStatus());

            note(
                    doc,
                    "Staff copy of the original public application answers "
                            + "when retained; older records use saved loan data. "
                            + "This is not a signed agreement, proof of consent, "
                            + "or an approval decision. Details not stored "
                            + "in the database are left blank.");

            // ============================================================
            // 1. APPLICANT INFORMATION
            // ============================================================

            section(
                    doc,
                    "1. Applicant Information (Amakuru y'usaba)");

            info(
                    doc,
                    "Full name / Amazina yose",
                    original(answers, "fullName", b.getFullName()));

            info(
                    doc,
                    "First name / Izina",
                    original(answers, "firstName", b.getFirstName()));

            info(
                    doc,
                    "Last name / Izina ry'umuryango",
                    original(answers, "lastName", b.getLastName()));

            info(
                    doc,
                    "Date of birth / Itariki y'amavuko",
                    original(
                            answers,
                            "dateOfBirth",
                            date(b.getDateOfBirth())));

            info(
                    doc,
                    "Place of birth / Aho yavukiye",
                    original(
                            answers,
                            "placeOfBirth",
                            b.getPlaceOfBirth()));

            info(
                    doc,
                    "National ID / Indangamuntu",
                    original(
                            answers,
                            "nationalId",
                            b.getNationalId()));

            info(
                    doc,
                    "Passport / Pasiporo (if applicable)",
                    b.getPassportNumber());

            info(
                    doc,
                    "Gender / Igitsina",
                    original(
                            answers,
                            "gender",
                            b.getGender()));

            info(
                    doc,
                    "Nationality / Ubwenegihugu",
                    original(
                            answers,
                            "nationality",
                            b.getNationality()));

            info(
                    doc,
                    "Marital status / Irangamimerere",
                    original(
                            answers,
                            "maritalStatus",
                            b.getMaritalStatus()));

            info(
                    doc,
                    "Phone / Telefone",
                    original(
                            answers,
                            "phone",
                            b.getPhone()));

            info(
                    doc,
                    "Alternative phone / Indi telefone",
                    b.getAlternatePhone());

            info(
                    doc,
                    "Email / Imeyili",
                    original(
                            answers,
                            "email",
                            b.getEmail()));

            info(
                    doc,
                    "Home address / Aho atuye",
                    original(
                            answers,
                            "address",
                            b.getAddress()));

            info(
                    doc,
                    "City / Umujyi",
                    original(
                            answers,
                            "city",
                            b.getCity()));

            info(
                    doc,
                    "Province / Intara",
                    original(
                            answers,
                            "province",
                            first(
                                    b.getPhysicalAddressProvince(),
                                    b.getStateProvince())));

            info(
                    doc,
                    "District / Akarere",
                    original(
                            answers,
                            "district",
                            b.getPhysicalAddressDistrict()));

            info(
                    doc,
                    "Sector / Umurenge",
                    original(
                            answers,
                            "sector",
                            b.getPhysicalAddressSector()));

            info(
                    doc,
                    "Cell / Akagari",
                    original(
                            answers,
                            "cell",
                            b.getPhysicalAddressCell()));

            info(
                    doc,
                    "Village / Umudugudu",
                    original(
                            answers,
                            "village",
                            b.getPhysicalAddressVillage()));

            info(
                    doc,
                    "Country / Igihugu",
                    original(
                            answers,
                            "country",
                            b.getCountry()));

            // ============================================================
            // 2. EMPLOYMENT / BUSINESS INFORMATION
            // ============================================================

            section(
                    doc,
                    "2. Employment / Business Information "
                            + "(Akazi cyangwa Ubucuruzi)");

            info(
                    doc,
                    "Employment type / Uburyo bw'akazi",
                    original(
                            answers,
                            "employmentType",
                            b.getEmploymentType()));

            info(
                    doc,
                    "Job title / Umurimo ukora",
                    original(
                            answers,
                            "jobTitle",
                            b.getJobTitle()));

            info(
                    doc,
                    "Employer / Business name / Umukoresha cyangwa ubucuruzi",
                    original(
                            answers,
                            "employerName",
                            b.getEmployerName()));

            info(
                    doc,
                    "Nature of business / Ubwoko bw'ubucuruzi",
                    original(
                            answers,
                            "natureOfBusiness",
                            null));

            info(
                    doc,
                    "Other employment type / Ubundi bwoko bw'akazi",
                    original(
                            answers,
                            "employmentTypeOther",
                            null));

            info(
                    doc,
                    "Workplace / Aho ukorera",
                    original(
                            answers,
                            "workplace",
                            null));

            info(
                    doc,
                    "Time in employment / Igihe amaze mu kazi",
                    original(
                            answers,
                            "timeInEmployment",
                            null));

            info(
                    doc,
                    "Monthly income / Umushahara ku kwezi (RWF)",
                    original(
                            answers,
                            "monthlyIncome",
                            money(b.getMonthlyIncomeDecimal())));

            info(
                    doc,
                    "Monthly expenses / Amafaranga asohoka ku kwezi (RWF)",
                    original(
                            answers,
                            "monthlyExpenses",
                            money(b.getMonthlyExpensesDecimal())));

            info(
                    doc,
                    "Bank name / Banki",
                    b.getBankName());

            info(
                    doc,
                    "Bank account / Konti",
                    b.getBankAccountNumber());

            info(
                    doc,
                    "Bank branch / Ishami rya banki",
                    b.getBankBranch());

            // ============================================================
            // 3. LOAN DETAILS
            // ============================================================

            section(
                    doc,
                    "3. Loan Details (Amakuru ku nguzanyo)");

            info(
                    doc,
                    "Loan type / Ubwoko bw'inguzanyo",
                    original(
                            answers,
                            "loanType",
                            loan.getLoanType()));

            info(
                    doc,
                    "Amount requested / Amafaranga asaba ("
                            + currency(loan) + ")",
                    original(
                            answers,
                            "amount",
                            money(
                                    loan.getRequestedAmount() == null
                                            ? loan.getAmount()
                                            : loan.getRequestedAmount())));

            info(
                    doc,
                    "Purpose of loan / Icyo azayifashisha",
                    original(
                            answers,
                            "purpose",
                            loan.getPurpose()));

            info(
                    doc,
                    "Repayment period / Igihe cyo kwishyura (months)",
                    original(
                            answers,
                            "durationMonths",
                            loan.getDurationMonths()));

            info(
                    doc,
                    "Mode of repayment / Uburyo bwo kwishyura",
                    original(
                            answers,
                            "repaymentFrequency",
                            loan.getRepaymentFrequency()));

            info(
                    doc,
                    "Proposed start date / Itariki iteganyijwe",
                    date(loan.getStartDate()));

            // ============================================================
            // 4. COLLATERAL / GUARANTEE
            // ============================================================

            section(
                    doc,
                    "4. Collateral / Guarantee (Ingwate cyangwa Uhamya)");

            info(
                    doc,
                    "Collateral type / Ubwoko bw'ingwate",
                    original(
                            answers,
                            "collateralType",
                            first(
                                    loan.getCollateralType(),
                                    "NONE")));

            info(
                    doc,
                    "Collateral description / Ibisobanuro by'ingwate",
                    original(
                            answers,
                            "collateral",
                            loan.getCollateralDescription()));

            info(
                    doc,
                    "Collateral value / Agaciro k'ingwate ("
                            + currency(loan) + ")",
                    original(
                            answers,
                            "collateralValue",
                            money(loan.getCollateralValue())));

            info(
                    doc,
                    "Guarantor name / Amazina y'umwishingizi",
                    original(
                            answers,
                            "guarantorName",
                            null));

            info(
                    doc,
                    "Guarantor phone / Telefone y'umwishingizi",
                    original(
                            answers,
                            "guarantorPhone",
                            null));

            info(
                    doc,
                    "Guarantor ID / Indangamuntu y'umwishingizi",
                    original(
                            answers,
                            "guarantorNationalId",
                            null));

            note(
                    doc,
                    "Guarantor information is displayed only when stored. "
                            + "Immovable collateral expertise must be "
                            + "independently reviewed in Documents.");

            // ============================================================
            // 5. MARITAL / SPOUSE DETAILS
            // ============================================================

            section(
                    doc,
                    "5. Marital / Spouse Details "
                            + "(Amakuru y'uwo bashakanye)");

            info(
                    doc,
                    "Spouse full name / Amazina y'uwo bashakanye",
                    original(
                            answers,
                            "spouseFullName",
                            b.getSpouseFullName()));

            info(
                    doc,
                    "Spouse national ID / Indangamuntu",
                    original(
                            answers,
                            "spouseNationalId",
                            b.getSpouseNationalId()));

            info(
                    doc,
                    "Spouse phone / Telefone",
                    original(
                            answers,
                            "spousePhone",
                            b.getSpousePhone()));

            info(
                    doc,
                    "Spouse consent recorded / Ubwumvikane",
                    original(
                            answers,
                            "spouseConsent",
                            b.getSpouseConsent()));

            info(
                    doc,
                    "Single certificate number / Icyemezo cy'ubuseribateri",
                    original(
                            answers,
                            "singleCertificateNumber",
                            b.getSingleCertificateNumber()));

            // ============================================================
            // 6. DECLARATION
            // ============================================================

            section(
                    doc,
                    "6. Declaration (Isezerano)");

            info(
                    doc,
                    "Online terms accepted / Yemeye amabwiriza kuri interineti",
                    original(
                            answers,
                            "acceptedTerms",
                            loan.getTermsAcceptedAt() == null
                                    ? null
                                    : true));

            info(
                    doc,
                    "Date terms were recorded / Itariki yemerejweho",
                    loan.getTermsAcceptedAt());

            body(
                    doc,
                    "Nemeza ko amakuru ntanze ari ukuri "
                            + "kandi mpaye uburenganzira ikigo "
                            + "kuyagenzura ndetse no kureba amakuru "
                            + "yerekeranye n'imyenda mu kigo "
                            + "gishinzwe amakuru ku myenda (CRB).");

            body(
                    doc,
                    "I confirm that the information provided is true "
                            + "and authorize appropriate verification "
                            + "and lawful credit-reference checks. "
                            + "These words require a real signature below.");

            // ============================================================
            // 7. SIGNATURES
            // ============================================================

            section(
                    doc,
                    "7. Signatures (Imikono)");

            signatureHeading(
                    doc,
                    "BORROWER / APPLICANT — USABA INGUZANYO");

            info(
                    doc,
                    "Name / Amazina",
                    original(
                            answers,
                            "fullName",
                            b.getFullName()));

            signatureBlank(
                    doc,
                    "Signature / Umukono");

            signatureBlank(
                    doc,
                    "Date / Itariki");

            signatureHeading(
                    doc,
                    "NOBLE LOAN SOLUTIONS LTD — UHAGARARIYE IKIGO");

            signatureBlank(
                    doc,
                    "Representative name / Amazina");

            signatureBlank(
                    doc,
                    "Position / Umwanya");

            signatureBlank(
                    doc,
                    "Signature / Umukono");

            signatureBlank(
                    doc,
                    "Date / Itariki");

            signatureBlank(
                    doc,
                    "Company stamp / Kashe");

            // ============================================================
            // 8. INTERNAL REVIEW
            // ============================================================

            section(
                    doc,
                    "8. Internal Review (Ku bakozi b'ikigo)");

            signatureBlank(
                    doc,
                    "Prepared by / Loan officer");

            signatureBlank(
                    doc,
                    "Reviewed by / Uwagenzuye");

            signatureBlank(
                    doc,
                    "Review date / Itariki");

            signatureBlank(
                    doc,
                    "Remarks / Ibyitonderwa");

            note(
                    doc,
                    "This form is a copy of saved information, "
                            + "not a signed contract. "
                            + "Downloading it does not alter the loan, "
                            + "approve it, or disburse money.");

            doc.write(out);

            return out.toByteArray();

        } catch (
                IOException
                | org.apache.poi.openxml4j.exceptions.InvalidFormatException ex) {

            throw new IllegalStateException(
                    "Unable to generate loan application form.",
                    ex);
        }
    }

    /**
     * Retrieve the original responses recorded when the
     * public application was submitted.
     *
     * Never returns answers from another organization.
     */
    private Map<String, Object> originalAnswers(Loan loan) {

        if (loan.getId() == null
                || loan.getOrganization() == null) {
            return Collections.emptyMap();
        }

        return snapshotRepository
                .findByLoan_IdAndLoan_Organization_Id(
                        loan.getId(),
                        loan.getOrganization().getId())
                .map(
                        LoanApplicationSnapshot::getOriginalAnswersJson)
                .map(json -> {

                    try {

                        return objectMapper.readValue(
                                json,
                                new TypeReference<Map<String, Object>>() {
                                });

                    } catch (Exception e) {

                        throw new IllegalStateException(
                                "Saved loan application answers cannot be read. "
                                        + "Check encryption keys and stored "
                                        + "application data.",
                                e);
                    }

                })
                .orElseGet(Collections::emptyMap);
    }

    /**
     * Original applicant-provided information takes priority.
     *
     * If the original field was not stored, use existing
     * borrower or loan data.
     */
    private static Object original(
            Map<String, Object> answers,
            String key,
            Object fallback) {

        if (!answers.containsKey(key)) {
            return fallback;
        }

        Object value = answers.get(key);

        return value == null || value.toString().isBlank()
                ? null
                : value;
    }

    /**
     * Add the official Noble Loan Solutions logo.
     */
    private static void createBrandHeader(XWPFDocument doc)
            throws IOException,
            org.apache.poi.openxml4j.exceptions.InvalidFormatException {

        XWPFHeader header = doc.createHeader(
                HeaderFooterType.DEFAULT);

        XWPFParagraph logoParagraph = header.createParagraph();

        logoParagraph.setAlignment(
                ParagraphAlignment.CENTER);

        try (
                InputStream image = LoanApplicationFormService.class
                        .getResourceAsStream(LOGO)) {

            if (image == null) {

                throw new IllegalStateException(
                        "Approved NLS logo is missing: " + LOGO);
            }

            logoParagraph
                    .createRun()
                    .addPicture(
                            image,
                            Document.PICTURE_TYPE_PNG,
                            "nls-logo.png",
                            Units.toEMU(165),
                            Units.toEMU(55));
        }

        center(
                header.createParagraph(),
                "NOBLE LOAN SOLUTIONS LTD",
                10,
                true);

        center(
                header.createParagraph(),
                "CONFIDENTIAL — LOAN APPLICATION",
                8,
                true);
    }

    /**
     * Configure document page margins.
     */
    private static void setMargins(XWPFDocument doc) {

        var sect = doc.getDocument()
                .getBody()
                .isSetSectPr()
                        ? doc.getDocument()
                                .getBody()
                                .getSectPr()
                        : doc.getDocument()
                                .getBody()
                                .addNewSectPr();

        var mar = sect.isSetPgMar()
                ? sect.getPgMar()
                : sect.addNewPgMar();

        mar.setTop(BigInteger.valueOf(2450));
        mar.setBottom(BigInteger.valueOf(1000));
        mar.setLeft(BigInteger.valueOf(1150));
        mar.setRight(BigInteger.valueOf(1150));
        mar.setHeader(BigInteger.valueOf(260));
    }

    /**
     * Render a centered paragraph.
     */
    private static void center(
            XWPFParagraph paragraph,
            String text,
            int size,
            boolean bold) {

        paragraph.setAlignment(
                ParagraphAlignment.CENTER);

        XWPFRun run = paragraph.createRun();

        run.setText(text);
        run.setBold(bold);
        run.setFontSize(size);
        run.setFontFamily("Aptos");
        run.setColor("17324D");
    }

    /**
     * Render a section heading.
     *
     * Apache POI compatibility fix:
     * setKeepWithNext() is replaced with setKeepNext().
     */
    private static void section(
            XWPFDocument doc,
            String title) {

        XWPFParagraph p = doc.createParagraph();

        p.setSpacingBefore(225);
        p.setSpacingAfter(100);

        p.setKeepNext(true);

        XWPFRun r = p.createRun();

        r.setText(title);
        r.setBold(true);
        r.setFontSize(12);
        r.setColor("17324D");
        r.setFontFamily("Aptos");
    }

    /**
     * Render an application field without creating tables.
     *
     * Apache POI compatibility fix:
     * Use the underlying Word paragraph keepLines property
     * instead of unsupported setKeepTogether().
     */
    private static void info(
            XWPFDocument doc,
            String label,
            Object value) {

        XWPFParagraph p = doc.createParagraph();

        p.setSpacingAfter(75);

        var properties = p.getCTP().isSetPPr()
                ? p.getCTP().getPPr()
                : p.getCTP().addNewPPr();

        if (!properties.isSetKeepLines()) {
            properties.addNewKeepLines();
        }

        XWPFRun key = p.createRun();

        key.setBold(true);
        key.setFontSize(9);
        key.setFontFamily("Aptos");
        key.setText(label + ":  ");

        XWPFRun val = p.createRun();

        val.setFontSize(9);
        val.setFontFamily("Aptos");

        String displayValue = value == null || value.toString().isBlank()
                ? UNKNOWN
                : value.toString();

        val.setText(displayValue);
    }

    /**
     * Display a signature section heading.
     */
    private static void signatureHeading(
            XWPFDocument doc,
            String title) {

        XWPFParagraph p = doc.createParagraph();

        p.setSpacingBefore(145);
        p.setSpacingAfter(100);

        p.setKeepNext(true);

        XWPFRun r = p.createRun();

        r.setBold(true);
        r.setFontSize(10);
        r.setFontFamily("Aptos");
        r.setText(title);
    }

    /**
     * Leave a blank space for an actual handwritten signature.
     */
    private static void signatureBlank(
            XWPFDocument doc,
            String label) {

        XWPFParagraph p = doc.createParagraph();

        p.setSpacingAfter(130);

        XWPFRun r = p.createRun();

        r.setFontSize(9);

        r.setText(
                label
                        + ":  ________________________________________");
    }

    /**
     * Render explanatory text.
     */
    private static void body(
            XWPFDocument doc,
            String text) {

        XWPFParagraph p = doc.createParagraph();

        p.setSpacingAfter(100);

        XWPFRun r = p.createRun();

        r.setFontSize(9);
        r.setFontFamily("Aptos");
        r.setText(text);
    }

    /**
     * Render a small italic disclaimer.
     */
    private static void note(
            XWPFDocument doc,
            String text) {

        XWPFParagraph p = doc.createParagraph();

        p.setSpacingAfter(120);

        XWPFRun r = p.createRun();

        r.setFontSize(8);
        r.setFontFamily("Aptos");
        r.setItalic(true);
        r.setColor("555555");
        r.setText(text);
    }

    /**
     * Return the first non-empty value.
     */
    private static String first(
            String a,
            String b) {

        return a == null || a.isBlank()
                ? b
                : a;
    }

    /**
     * Format a date.
     */
    private static String date(
            LocalDate date) {

        return date == null
                ? null
                : date.format(DATE);
    }

    /**
     * Format a monetary amount.
     */
    private static String money(
            BigDecimal amount) {

        return amount == null
                ? null
                : amount
                        .setScale(
                                2,
                                RoundingMode.HALF_UP)
                        .toPlainString();
    }

    /**
     * Determine the loan currency.
     */
    private static String currency(
            Loan loan) {

        return first(
                loan.getCurrency(),
                "RWF").toUpperCase(Locale.ROOT);
    }
}

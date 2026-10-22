
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;

final class LoanContractTemplateRenderer {

    private LoanContractTemplateRenderer() {
    }

    private static final String LOGO_RESOURCE = "/branding/nls-logo.png";

    private static final String NAVY = "17324D";
    private static final String GOLD = "926D25";
    private static final String BODY = "253346";
    private static final String MUTED = "687384";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static byte[] render(
            Loan loan,
            List<PaymentSchedule> schedules) {
        if (loan == null || loan.getBorrower() == null) {
            throw new IllegalArgumentException(
                    "Loan and borrower are required.");
        }

        Borrower borrower = loan.getBorrower();

        BigDecimal principal = nz(loan.getAmountDecimal());
        BigDecimal interest = nz(loan.getInterestRateDecimal());
        BigDecimal management = nz(loan.getManagementFeeRateDecimal());
        BigDecimal application = nz(loan.getApplicationFeeRateDecimal());

        boolean annual = "ANNUAL".equalsIgnoreCase(
                String.valueOf(loan.getInterestRateType()));

        BigDecimal monthlyInterest = principal.multiply(interest)
                .divide(
                        annual ? new BigDecimal("1200")
                                : new BigDecimal("100"),
                        2,
                        RoundingMode.HALF_UP);

        BigDecimal monthlyManagement = principal.multiply(management)
                .divide(
                        new BigDecimal("100"),
                        2,
                        RoundingMode.HALF_UP);

        BigDecimal applicationFee = loan.getApplicationFeeDecimal() != null
                ? loan.getApplicationFeeDecimal()
                : principal.multiply(application)
                        .divide(
                                new BigDecimal("100"),
                                2,
                                RoundingMode.HALF_UP);

        String currency = loan.getCurrency();
        String reference = str(loan.getReferenceNumber());

        try (
                XWPFDocument doc = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            configurePage(doc);
            createHeader(doc);
            createFooter(doc, reference);

            title(doc, "AMASEZERANO Y’INGUZANYO");

            centered(
                    doc,
                    "NOBLE LOAN SOLUTIONS LTD",
                    12,
                    NAVY,
                    true);

            centered(
                    doc,
                    "UMUSHINGA W’AMASEZERANO — DRAFT / NOT SIGNED",
                    9,
                    GOLD,
                    true);

            centered(
                    doc,
                    "Nomero: " + reference
                            + "     |     Itariki: "
                            + LocalDate.now().format(DATE),
                    9,
                    MUTED,
                    false);

            paragraph(
                    doc,
                    "Aya masezerano ni umushinga ugomba "
                            + "gusuzumwa no kwemezwa mbere yo gusinywa.");

            section(doc, "1. ABAGIRANYE AMASEZERANO");

            paragraph(
                    doc,
                    "Uguriza: Noble Loan Solutions Ltd (NLS), "
                            + "ihagarariwe n’umuyobozi cyangwa undi "
                            + "mukozi ubifitiye ububasha.");

            paragraph(
                    doc,
                    "Ugurizwa: "
                            + str(borrower.getFirstName()) + " "
                            + str(borrower.getLastName()));

            paragraph(
                    doc,
                    "Indangamuntu/Pasiporo: "
                            + str(borrower.getNationalId()));

            paragraph(
                    doc,
                    "Telefone: " + str(borrower.getPhone())
                            + " | Email: " + str(borrower.getEmail()));

            paragraph(
                    doc,
                    "Uwo bashakanye: "
                            + str(borrower.getSpouseFullName())
                            + " | Indangamuntu: "
                            + str(borrower.getSpouseNationalId()));

            paragraph(doc, "Aderesi: " + address(borrower));

            section(doc, "2. INGANO N’IMPAMVU Y’INGUZANYO");

            paragraph(
                    doc,
                    "NLS yemeye guha ugurizwa inguzanyo ingana na "
                            + money(principal, currency)
                            + ", igenewe: "
                            + str(loan.getPurpose()) + ".");

            paragraph(
                    doc,
                    "Igihe cy’inguzanyo: "
                            + str(loan.getDurationMonths())
                            + " amezi.");

            paragraph(
                    doc,
                    "Amafaranga yose ateganyijwe kwishyurwa: "
                            + money(
                                    loan.getTotalRepayableDecimal(),
                                    currency)
                            + ".");

            section(
                    doc,
                    "3. INYUNGU N’AMAFARANGA AJYANYE N’INGUZANYO");

            paragraph(
                    doc,
                    "Igipimo cy’inyungu: "
                            + pct(interest)
                            + (annual ? " ku mwaka." : " ku kwezi.")
                            + " Inyungu zibarwa ku mushoro usigaye.");

            paragraph(
                    doc,
                    "Ikigereranyo cy’inyungu z’ukwezi kwa mbere "
                            + "ku mushoro w’ibanze: "
                            + money(monthlyInterest, currency) + ".");

            paragraph(
                    doc,
                    "Amafaranga yo kwiga dosiye: "
                            + pct(application)
                            + ", angana na "
                            + money(applicationFee, currency)
                            + ", akatwa ku mafaranga ahabwa ugurizwa "
                            + "igihe inguzanyo itanzwe.");

            paragraph(
                    doc,
                    "Amafaranga yo gukurikirana inguzanyo: "
                            + pct(management)
                            + " ku kwezi ku mushoro usigaye. "
                            + "Ikigereranyo ku mushoro w’ibanze: "
                            + money(monthlyManagement, currency) + ".");

            paragraph(
                    doc,
                    "Igipimo cy’igihano cy’ubukererwe "
                            + "kibitswe kuri iyi nguzanyo: "
                            + pct(loan.getPenaltyRateDecimal())
                            + ". Igihano gikoreshwa hakurikijwe "
                            + "amasezerano yemejwe, politiki "
                            + "n’amategeko akurikizwa.");

            section(doc, "4. GAHUNDA YO KWISHYURA");

            paragraph(
                    doc,
                    "Ugurizwa azishyura inguzanyo hakurikijwe "
                            + "gahunda yemejwe.");

            addSchedule(doc, schedules, currency);

            paragraph(
                    doc,
                    "Konti yo kwishyuriraho igomba kugenzurwa "
                            + "n’umukozi ubifitiye ububasha mbere "
                            + "yo kohereza amasezerano.");

            section(doc, "5. INSHINGANO Z’UGURIZWA");

            paragraph(
                    doc,
                    "Ugurizwa yiyemeza kwishyura ku matariki "
                            + "yateganyijwe, gutanga amakuru y’ukuri, "
                            + "no kumenyesha NLS impinduka zikomeye "
                            + "ku bushobozi bwo kwishyura cyangwa "
                            + "ku makuru ye.");

            section(doc, "6. UBUTINDE N’INGARUKA ZABWO");

            paragraph(
                    doc,
                    "Iyo ubwishyu bukerewe, ibihano n’izindi "
                            + "ngamba zikoreshwa gusa hakurikijwe "
                            + "ibipimo n’uburyo byemejwe mu "
                            + "masezerano n’amategeko akurikizwa.");

            section(doc, "7. AMAKURU Y’INGUZANYO");

            paragraph(
                    doc,
                    "Amakuru ajyanye n’inguzanyo ashobora "
                            + "gutangwa ku nzego zibifitiye "
                            + "ububasha hakurikijwe amategeko "
                            + "agenga amakuru n’inguzanyo.");

            section(doc, "8. GUHINDURA AMASEZERANO");

            paragraph(
                    doc,
                    "Impinduka ku mubare w’inguzanyo, ibipimo, "
                            + "amafaranga cyangwa gahunda yo "
                            + "kwishyura zigomba kwandikwa no "
                            + "kwemezwa n’impande bireba.");

            section(doc, "9. GUKEMURA AMAKIMBIRANE");

            paragraph(
                    doc,
                    "Impande zizabanza kugerageza gukemura "
                            + "amakimbirane mu bwumvikane. "
                            + "Iyo bidashobotse, hakurikizwa "
                            + "amategeko y’u Rwanda.");

            section(doc, "10. KWEMERA NO GUSINYA");

            paragraph(
                    doc,
                    "Impande zemeza ko zasomye kandi "
                            + "zasobanukiwe ibikubiye muri aya "
                            + "masezerano mbere yo kuyasinya.");

            paragraph(
                    doc,
                    "Bikorewe Kigali, kuwa "
                            + LocalDate.now().format(DATE) + ".");

            signatureTable(
                    doc,
                    str(borrower.getFirstName()) + " "
                            + str(borrower.getLastName()));

            paragraph(
                    doc,
                    "ICYITONDERWA: Iyi nyandiko ni umushinga "
                            + "utarasinywa kandi ntabwo yemeza "
                            + "ko amafaranga y’inguzanyo yatanzwe.");

            doc.getProperties()
                    .getCoreProperties()
                    .setTitle(
                            "NLS Loan Agreement - " + reference);

            doc.write(out);
            return out.toByteArray();

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to generate branded NLS contract.",
                    ex);
        }
    }

    private static void configurePage(XWPFDocument doc) {
        var section = doc.getDocument()
                .getBody()
                .isSetSectPr()
                        ? doc.getDocument().getBody().getSectPr()
                        : doc.getDocument().getBody().addNewSectPr();

        var margins = section.isSetPgMar()
                ? section.getPgMar()
                : section.addNewPgMar();

        margins.setTop(BigInteger.valueOf(1900));
        margins.setBottom(BigInteger.valueOf(1100));
        margins.setLeft(BigInteger.valueOf(1100));
        margins.setRight(BigInteger.valueOf(1100));
        margins.setHeader(BigInteger.valueOf(300));
        margins.setFooter(BigInteger.valueOf(400));
    }

    private static void createHeader(XWPFDocument doc)
            throws Exception {

        XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);

        XWPFParagraph logoParagraph = header.createParagraph();

        logoParagraph.setAlignment(
                ParagraphAlignment.CENTER);

        try (
                InputStream logo = LoanContractTemplateRenderer.class
                        .getResourceAsStream(LOGO_RESOURCE)) {
            if (logo == null) {
                throw new IllegalStateException(
                        "Missing required NLS logo: "
                                + LOGO_RESOURCE);
            }

            XWPFRun run = logoParagraph.createRun();

            run.addPicture(
                    logo,
                    Document.PICTURE_TYPE_PNG,
                    "nls-logo.png",
                    Units.toEMU(175),
                    Units.toEMU(59));
        }

        XWPFParagraph brand = header.createParagraph();
        brand.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun brandRun = brand.createRun();
        brandRun.setText("NOBLE LOAN SOLUTIONS LTD");
        brandRun.setBold(true);
        brandRun.setFontSize(11);
        brandRun.setFontFamily("Aptos");
        brandRun.setColor(NAVY);

        XWPFParagraph classification = header.createParagraph();

        classification.setAlignment(
                ParagraphAlignment.CENTER);

        XWPFRun classificationRun = classification.createRun();

        classificationRun.setText(
                "IBANGA • CONFIDENTIAL");
        classificationRun.setBold(true);
        classificationRun.setFontSize(8);
        classificationRun.setColor(GOLD);
    }

    private static void createFooter(
            XWPFDocument doc,
            String reference) {
        XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);

        XWPFParagraph paragraph = footer.createParagraph();

        paragraph.setAlignment(
                ParagraphAlignment.CENTER);

        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Aptos");
        run.setFontSize(8);
        run.setColor(MUTED);

        run.setText(
                "NOBLE LOAN SOLUTIONS LTD"
                        + "  •  CONFIDENTIAL"
                        + "  •  " + reference
                        + "  •  DRAFT / NOT SIGNED");
    }

    private static void title(
            XWPFDocument doc,
            String text) {
        XWPFParagraph paragraph = doc.createParagraph();

        paragraph.setAlignment(
                ParagraphAlignment.CENTER);
        paragraph.setSpacingBefore(150);
        paragraph.setSpacingAfter(150);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontFamily("Aptos Display");
        run.setFontSize(16);
        run.setColor(NAVY);
    }

    private static void centered(
            XWPFDocument doc,
            String text,
            int size,
            String color,
            boolean bold) {
        XWPFParagraph paragraph = doc.createParagraph();

        paragraph.setAlignment(
                ParagraphAlignment.CENTER);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily("Aptos");
        run.setFontSize(size);
        run.setColor(color);
        run.setBold(bold);
    }

    private static void section(
            XWPFDocument doc,
            String text) {
        XWPFParagraph paragraph = doc.createParagraph();

        paragraph.setSpacingBefore(230);
        paragraph.setSpacingAfter(90);

        var properties = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr()
                : paragraph.getCTP().addNewPPr();

        if (!properties.isSetKeepNext()) {
            properties.addNewKeepNext();
        }

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(11);
        run.setFontFamily("Aptos");
        run.setColor(NAVY);
    }

    private static void paragraph(
            XWPFDocument doc,
            String text) {
        XWPFParagraph paragraph = doc.createParagraph();

        paragraph.setAlignment(
                ParagraphAlignment.BOTH);
        paragraph.setSpacingAfter(110);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily("Aptos");
        run.setFontSize(10);
        run.setColor(BODY);
    }

    private static void addSchedule(
            XWPFDocument doc,
            List<PaymentSchedule> schedules,
            String currency) {
        if (schedules == null || schedules.isEmpty()) {
            paragraph(
                    doc,
                    "[GAHUNDA YO KWISHYURA NTIRABONEKA "
                            + "— NTIMWOHEREZE AMASEZERANO]");
            return;
        }

        XWPFTable table = doc.createTable(schedules.size() + 1, 3);

        setCell(table, 0, 0, "Igice", true);
        setCell(table, 0, 1, "Itariki", true);
        setCell(table, 0, 2, "Amafaranga", true);

        int row = 1;

        for (PaymentSchedule schedule : schedules) {
            setCell(
                    table,
                    row,
                    0,
                    str(schedule.getInstallmentNumber()),
                    false);

            setCell(
                    table,
                    row,
                    1,
                    schedule.getDueDate() == null
                            ? "[ITARIKI IBURA]"
                            : schedule.getDueDate().format(DATE),
                    false);

            setCell(
                    table,
                    row,
                    2,
                    numericMoney(
                            schedule.getInstallmentAmount(),
                            currency),
                    false);

            row++;
        }

        doc.createParagraph();
    }

    private static void setCell(
            XWPFTable table,
            int row,
            int column,
            String value,
            boolean heading) {
        XWPFTableCell cell = table.getRow(row).getCell(column);

        XWPFParagraph paragraph = cell.getParagraphs().get(0);

        XWPFRun run = paragraph.createRun();
        run.setText(value);
        run.setFontFamily("Aptos");
        run.setFontSize(9);
        run.setBold(heading);
        run.setColor(heading ? NAVY : BODY);
    }

    private static void signatureTable(
            XWPFDocument doc,
            String borrowerName) {
        XWPFTable table = doc.createTable(4, 2);

        setCell(table, 0, 0, "UGURIJWE", true);
        setCell(table, 0, 1, "UGURIJE", true);

        setCell(table, 1, 0, borrowerName, false);
        setCell(
                table,
                1,
                1,
                "Noble Loan Solutions Ltd",
                false);

        setCell(
                table,
                2,
                0,
                "Umukono: __________________",
                false);

        setCell(
                table,
                2,
                1,
                "Umukono: __________________",
                false);

        setCell(
                table,
                3,
                0,
                "Itariki: __________________",
                false);

        setCell(
                table,
                3,
                1,
                "Itariki: __________________",
                false);
    }

    private static String money(
            BigDecimal amount,
            String currency) {
        if (amount == null) {
            return "[KUZUZA]";
        }

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Negative contract amount.");
        }

        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);

        if (normalized.stripTrailingZeros().scale() > 0) {
            return numericMoney(normalized, currency);
        }

        return rwfWords(normalized.toBigIntegerExact())
                + " ("
                + str(currency)
                + " "
                + normalized.toBigIntegerExact()
                + ")";
    }

    private static String numericMoney(
            BigDecimal amount,
            String currency) {
        if (amount == null) {
            return "[KUZUZA]";
        }

        return str(currency)
                + " "
                + amount.setScale(
                        2,
                        RoundingMode.HALF_UP).toPlainString();
    }

    private static String rwfWords(BigInteger amount) {
        if (amount.signum() == 0) {
            return "zeru y’amafaranga y’u Rwanda";
        }

        if (amount.equals(
                BigInteger.valueOf(10_000_000L))) {
            return "miliyoni icumi z’amafaranga y’u Rwanda";
        }

        if (amount.equals(
                BigInteger.valueOf(1_000_000L))) {
            return "miliyoni imwe y’amafaranga y’u Rwanda";
        }

        return "amafaranga y’u Rwanda "
                + amount.toString();
    }

    private static String pct(BigDecimal rate) {
        return rate == null
                ? "[KUZUZA]"
                : rate.stripTrailingZeros()
                        .toPlainString() + "%";
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }

    private static String str(Object value) {
        return value == null
                || value.toString().isBlank()
                        ? "[KUZUZA]"
                        : value.toString().trim();
    }

    private static String address(Borrower borrower) {
        StringJoiner joiner = new StringJoiner(", ");

        Object[] parts = {
                borrower.getPhysicalAddressVillage(),
                borrower.getPhysicalAddressCell(),
                borrower.getPhysicalAddressSector(),
                borrower.getPhysicalAddressDistrict(),
                borrower.getCity(),
                borrower.getCountry()
        };

        for (Object part : parts) {
            if (part != null
                    && !part.toString().isBlank()) {
                joiner.add(part.toString().trim());
            }
        }

        return joiner.length() == 0
                ? str(borrower.getAddress())
                : joiner.toString();
    }
}

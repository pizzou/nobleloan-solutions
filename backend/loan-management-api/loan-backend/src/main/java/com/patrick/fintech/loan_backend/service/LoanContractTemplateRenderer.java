
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Noble Loan Solutions loan-contract generator.
 *
 * The contract is generated entirely from Java.
 * No external DOCX template is required.
 *
 * Kinyarwanda currency wording and grammatical agreement
 * are handled in this same class.
 */
final class LoanContractTemplateRenderer {

    private LoanContractTemplateRenderer() {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String LOGO_RESOURCE = "/branding/nls-logo.png";

    /*
     * =========================================================
     * CONTRACT GENERATION
     * =========================================================
     */

    static byte[] render(
            Loan loan,
            List<PaymentSchedule> schedules) {
        if (loan == null) {
            throw new IllegalArgumentException("Loan is required");
        }

        Borrower b = loan.getBorrower();

        if (b == null) {
            throw new IllegalArgumentException("Borrower is required");
        }

        BigDecimal principal = loan.getAmountDecimal();

        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Loan principal must be positive.");
        }

        BigDecimal interest = loan.getInterestRateDecimal();
        BigDecimal management = loan.getManagementFeeRateDecimal();
        BigDecimal application = loan.getApplicationFeeRateDecimal();

        if (interest == null ||
                management == null ||
                application == null) {

            throw new IllegalStateException(
                    "Loan interest, management fee and application "
                            + "fee rates must be saved before drafting "
                            + "its agreement.");
        }

        boolean annual = "ANNUAL".equalsIgnoreCase(
                String.valueOf(loan.getInterestRateType()));

        BigDecimal monthlyInterest = principal
                .multiply(interest)
                .divide(
                        annual
                                ? new BigDecimal("1200")
                                : new BigDecimal("100"),
                        2,
                        RoundingMode.HALF_UP);

        BigDecimal monthlyManagement = principal
                .multiply(management)
                .divide(
                        new BigDecimal("100"),
                        2,
                        RoundingMode.HALF_UP);

        // Show the agreed application fee even when the loan
        // has not yet been disbursed.
        BigDecimal applicationFee = principal
                .multiply(application)
                .divide(
                        new BigDecimal("100"),
                        2,
                        RoundingMode.HALF_UP);

        BigDecimal assessedFee = loan.getApplicationFeeDecimal();

        if (assessedFee != null &&
                assessedFee.signum() > 0 &&
                assessedFee.setScale(
                        2,
                        RoundingMode.HALF_UP).compareTo(applicationFee) != 0) {

            throw new IllegalStateException(
                    "Application fee assessed on this loan does "
                            + "not match its saved rate. Review the "
                            + "loan fee terms before issuing a contract.");
        }

        Map<String, String> values = new LinkedHashMap<>();

        values.put(
                "BORROWER_NAME",
                str(b.getFirstName()) + " " + str(b.getLastName()));

        values.put("NATIONAL_ID", str(b.getNationalId()));
        values.put("PHONE", str(b.getPhone()));
        values.put("EMAIL", str(b.getEmail()));
        values.put("SPOUSE_NAME", optional(b.getSpouseFullName()));
        values.put("SPOUSE_ID", optional(b.getSpouseNationalId()));
        values.put("ADDRESS", address(b));

        values.put(
                "PRINCIPAL",
                money(principal, loan.getCurrency()));

        values.put(
                "PURPOSE",
                purpose(loan.getPurpose()));

        values.put(
                "REFERENCE",
                str(loan.getReferenceNumber()));

        values.put("INTEREST_RATE", pct(interest));
        values.put(
                "INTEREST_PERIOD",
                annual ? "ku mwaka" : "ku kwezi");

        values.put(
                "MONTHLY_INTEREST",
                money(monthlyInterest, loan.getCurrency()));

        values.put("APPLICATION_RATE", pct(application));

        values.put(
                "APPLICATION_FEE",
                money(applicationFee, loan.getCurrency()));

        values.put("MANAGEMENT_RATE", pct(management));

        values.put(
                "MONTHLY_MANAGEMENT",
                money(monthlyManagement, loan.getCurrency()));

        values.put(
                "PENALTY_RATE",
                pct(loan.getPenaltyRateDecimal()));

        values.put(
                "DURATION",
                str(loan.getDurationMonths()));

        values.put(
                "DURATION_TEXT",
                durationText(loan.getDurationMonths()));

        values.put(
                "TOTAL_REPAYABLE",
                money(
                        loan.getTotalRepayableDecimal(),
                        loan.getCurrency()));

        values.put(
                "DATE",
                LocalDate.now().format(DATE));

        try (
                XWPFDocument doc = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            /*
             * =============================================
             * REPEATING COMPANY HEADER
             * =============================================
             */

            XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);

            XWPFParagraph brand = header.createParagraph();

            brand.setAlignment(ParagraphAlignment.CENTER);
            brand.setSpacingAfter(10);

            try (
                    java.io.InputStream logoStream = LoanContractTemplateRenderer.class
                            .getResourceAsStream(LOGO_RESOURCE)) {
                if (logoStream == null) {
                    throw new IllegalStateException(
                            "Original NLS logo is not packaged: "
                                    + LOGO_RESOURCE);
                }

                brand.createRun().addPicture(
                        logoStream,
                        Document.PICTURE_TYPE_PNG,
                        "nls-original-logo.png",
                        Units.toEMU(165),
                        Units.toEMU(55));
            }

            XWPFParagraph brandName = header.createParagraph();
            brandName.setAlignment(ParagraphAlignment.CENTER);
            brandName.setSpacingAfter(25);

            XWPFRun brandNameRun = brandName.createRun();

            brandNameRun.setText(
                    "AMASEZERANO Y’INGUZANYO  •  RWANDA");
            brandNameRun.setBold(true);
            brandNameRun.setFontFamily("Aptos");
            brandNameRun.setFontSize(8);
            brandNameRun.setColor("17324D");

            XWPFParagraph classification = header.createParagraph();

            classification.setAlignment(
                    ParagraphAlignment.CENTER);

            XWPFRun classificationRun = classification.createRun();

            classificationRun.setText(
                    "IBANGA  •  CONFIDENTIAL  •  NLS");
            classificationRun.setFontFamily("Aptos");
            classificationRun.setFontSize(8);
            classificationRun.setBold(true);
            classificationRun.setColor("926D25");

            /*
             * =============================================
             * DOCUMENT MARGINS
             * =============================================
             */

            var sect = doc.getDocument()
                    .getBody()
                    .isSetSectPr()
                            ? doc.getDocument().getBody().getSectPr()
                            : doc.getDocument().getBody().addNewSectPr();

            var margins = sect.isSetPgMar()
                    ? sect.getPgMar()
                    : sect.addNewPgMar();

            margins.setTop(BigInteger.valueOf(2400));
            margins.setBottom(BigInteger.valueOf(1050));
            margins.setLeft(BigInteger.valueOf(1150));
            margins.setRight(BigInteger.valueOf(1150));
            margins.setHeader(BigInteger.valueOf(260));
            margins.setFooter(BigInteger.valueOf(400));

            addIdentityCard(doc, loan);

            /*
             * =============================================
             * CONTRACT INTRODUCTION
             * =============================================
             */

            add(
                    doc,
                    "AMASEZERANO Y’IGURIZA NA NOBLE LOAN SOLUTIONS LTD",
                    true);

            add(
                    doc,
                    "NOBLE LOAN SOLUTIONS LTD, ikigo cyemerewe "
                            + "gutanga inguzanyo, gifite icyemezo cya "
                            + "Banki Nkuru y’u Rwanda (BNR) "
                            + "No 7420/2025-00211/7000 BNR, "
                            + "gifite icyicaro i Gisozi, "
                            + "gihagarariwe na Madamu TUYISENGE Annuarite, "
                            + "Umuyobozi Mukuru wa NLS, kikaba cyitwa "
                            + "“Ugurije” muri aya masezerano, "
                            + "ku ruhande rumwe;",
                    false);

            add(
                    doc,
                    replace(
                            "Bwana/Madamu {{BORROWER_NAME}}, "
                                    + "ufite indangamuntu No: {{NATIONAL_ID}}, "
                                    + "telefoni: {{PHONE}}, email: {{EMAIL}}. "
                                    + "Uwo bashakanye (niba ahari): "
                                    + "{{SPOUSE_NAME}}, indangamuntu No: "
                                    + "{{SPOUSE_ID}}. Aho atuye: {{ADDRESS}}.",
                            values),
                    false);

            add(
                    doc,
                    "Muri aya masezerano akaba yitwa "
                            + "“Ugurijwe”, ku rundi ruhande.",
                    false);

            add(
                    doc,
                    "HEMERANYIJWE IBI BIKURIKIRA:",
                    true);

            /*
             * =============================================
             * INGINGO YA 1
             * =============================================
             */

            add(doc, "INGINGO YA 1", true);
            add(doc, "Ubusobanuro", true);

            add(
                    doc,
                    "Muri aya masezerano, amagambo akurikira "
                            + "afite ibisobanuro bikurikira, keretse "
                            + "aho aya masezerano ateganya ukundi:",
                    false);

            add(
                    doc,
                    "1.1 “Noble Loan Solutions Ltd” ni "
                            + "ikigo cyemerewe gutanga inguzanyo, "
                            + "kikaba cyitwa “Ugurije” muri aya masezerano.",
                    false);

            add(
                    doc,
                    "1.2 “Ugurijwe” ni umuntu cyangwa ikigo "
                            + "cyahawe inguzanyo hakurikijwe aya masezerano.",
                    false);

            add(
                    doc,
                    "1.3 “NLS” ni impine ya Noble Loan Solutions Ltd.",
                    false);

            /*
             * =============================================
             * INGINGO YA 2
             * =============================================
             */

            add(doc, "INGINGO YA 2", true);

            add(
                    doc,
                    replace(
                            "NLS ihaye Ugurijwe inguzanyo ingana na "
                                    + "{{PRINCIPAL}}, hakurikijwe politiki "
                                    + "y’inguzanyo n’aya masezerano. "
                                    + "Iyo nguzanyo izakoreshwa mu rwego "
                                    + "rwa {{PURPOSE}}. "
                                    + "Inomero y’inguzanyo: {{REFERENCE}}.",
                            values),
                    false);

            addFinancialOverview(
                    doc,
                    loan,
                    monthlyInterest,
                    monthlyManagement,
                    applicationFee);

            /*
             * =============================================
             * INGINGO YA 3
             * =============================================
             */

            add(
                    doc,
                    "INGINGO YA 3: Inyungu ku nguzanyo, "
                            + "amafaranga yo gukurikirana inguzanyo "
                            + "n’umufuragiro w’inguzanyo",
                    true);

            add(
                    doc,
                    replace(
                            "Iyi nguzanyo ibarirwa inyungu ku gipimo "
                                    + "cya {{INTEREST_RATE}} "
                                    + "{{INTEREST_PERIOD}}. "
                                    + "Inyungu z’ukwezi kwa mbere, "
                                    + "zibariwe ku gishoro cy’ibanze, "
                                    + "zingana na {{MONTHLY_INTEREST}}. "
                                    + "Inyungu zibarwa ku gishoro "
                                    + "cy’inguzanyo gisigaye kwishyurwa.",
                            values),
                    false);

            add(
                    doc,
                    replace(
                            "Mu gihe cyo gutanga inguzanyo, "
                                    + "hakatwa amafaranga yo kwiga dosiye, "
                                    + "ari yo mufuragiro w’inguzanyo, "
                                    + "angana na {{APPLICATION_RATE}} "
                                    + "by’igishoro cy’inguzanyo, ni ukuvuga "
                                    + "{{APPLICATION_FEE}}. "
                                    + "Ayo mafaranga akatwa ku mafaranga "
                                    + "ahabwa Ugurijwe igihe cyo gutanga "
                                    + "inguzanyo.",
                            values),
                    false);

            add(
                    doc,
                    replace(
                            "Ugurijwe yishyura buri kwezi amafaranga "
                                    + "yo gukurikirana inguzanyo angana na "
                                    + "{{MANAGEMENT_RATE}} ku kwezi, "
                                    + "abarwa ku gishoro cy’inguzanyo "
                                    + "gisigaye kwishyurwa. "
                                    + "Ku gishoro cy’ibanze, ayo mafaranga "
                                    + "angana na {{MONTHLY_MANAGEMENT}}.",
                            values),
                    false);

            add(
                    doc,
                    replace(
                            "Iyo Ugurijwe atishyuye inguzanyo ye "
                                    + "mu gihe cyagenwe nk’uko biteganywa "
                                    + "n’aya masezerano, yishyura "
                                    + "igihano cy’ubukererwe ku gipimo cya "
                                    + "{{PENALTY_RATE}} ku kwezi "
                                    + "ku mafaranga yose ari mu bukererwe.",
                            values),
                    false);

            /*
             * =============================================
             * INGINGO YA 4
             * =============================================
             */

            add(doc, "INGINGO YA 4", true);

            add(
                    doc,
                    replace(
                            "Umwenda wose (igishoro, inyungu "
                                    + "n’amafaranga yo gukurikirana "
                                    + "inguzanyo) uzishyurwa mu gihe "
                                    + "cy’{{DURATION_TEXT}}. "
                                    + "Amafaranga yose ateganyijwe "
                                    + "kwishyurwa ni {{TOTAL_REPAYABLE}}. "
                                    + "Gahunda yo kwishyura ni iyi:",
                            values),
                    false);

            addSchedule(
                    doc,
                    schedules,
                    loan.getCurrency(),
                    loan.getTotalRepayableDecimal());

            add(
                    doc,
                    "NB: Amafaranga yose yishyurwa kuri konti "
                            + "No 100236776071 RWF ya "
                            + "NOBLE LOAN SOLUTIONS LTD iri muri "
                            + "BANK OF KIGALI.",
                    false);

            add(
                    doc,
                    "Abiherewe uburenganzira na NLS, "
                            + "Ugurijwe ashobora kwishyura umwenda "
                            + "wose cyangwa igice cyawo mbere y’igihe "
                            + "cyateganyijwe mu masezerano, agasigara "
                            + "yishyura inyungu zisigaye kwishyurwa, "
                            + "kandi hagakorwa amasezerano mashya "
                            + "aho bibaye ngombwa.",
                    false);

            add(
                    doc,
                    "Mu gihe habayeho kugura umwenda bikozwe "
                            + "n’ikigo cy’imari cyangwa ikindi kigo "
                            + "gikora ibikorwa bisa n’ibya NLS "
                            + "mbere y’uko igihe cyateganyijwe kirangira, "
                            + "NLS yishyurwa inyungu zose zari "
                            + "ziteganyijwe.",
                    false);

            /*
             * =============================================
             * INGINGO YA 5
             * =============================================
             */

            add(doc, "INGINGO YA 5", true);

            add(
                    doc,
                    "NLS ifite uburenganzira bwo gusesa aya "
                            + "masezerano nta nteguza no guhita "
                            + "yishyuza amafaranga yose ako kanya, "
                            + "ibinyujije mu ibaruwa ishinganye "
                            + "cyangwa, igihe bidashobotse, mu ibaruwa "
                            + "ishyikirizwa Ugurijwe aho atuye, "
                            + "iyo bigaragaye ko:",
                    false);

            addArticleFiveConditionsTable(doc);

            add(
                    doc,
                    "Hashingiwe ku byavuzwe haruguru, kuba "
                            + "NLS yaba izi impamvu ishobora gutuma "
                            + "isesa aya masezerano ariko igakomeza "
                            + "kuyubahiriza, ntibivuga ko yatakaje "
                            + "uburenganzira bwo kuyasesa nta nteguza "
                            + "nk’uko byavuzwe haruguru.",
                    false);

            /*
             * =============================================
             * INGINGO YA 6
             * =============================================
             */

            add(doc, "INGINGO YA 6", true);

            add(
                    doc,
                    "Ugurijwe yemeye kubahiriza gahunda yo "
                            + "kwishyura nk’uko yateganyijwe. "
                            + "Iyo akerewe iminsi itatu, ashobora "
                            + "guhabwa umuburo wa nyuma kandi "
                            + "akirengera ingaruka zose ziteganywa "
                            + "n’amategeko ndetse n’aya masezerano.",
                    false);

            add(
                    doc,
                    "Uretse uburenganzira NLS ifite ku ngwate "
                            + "zose Ugurijwe yatanze no kuba zagurishwa "
                            + "mu cyamunara hakurikijwe amategeko "
                            + "abigenga mu rwego rwo kugaruza umwenda "
                            + "usigaye n’ibiwukomokaho, NLS ifite "
                            + "kandi uburenganzira bwo gukurikirana "
                            + "indi mitungo y’Ugurijwe cyangwa "
                            + "iy’umwishingizi we, yaba iyimukanwa "
                            + "cyangwa itimukanwa, ndetse n’amafaranga "
                            + "abitse mu bigo by’imari, mu gihe "
                            + "Ugurijwe yananiwe kwishyura cyangwa "
                            + "ingwate yagurishijwe ntihaboneke "
                            + "amafaranga ahagije yo kurangiza umwenda "
                            + "wose. Ku ngwate zatanzwe, NLS iza "
                            + "imbere y’abandi Ugurijwe abereyemo "
                            + "imyenda iyo ingwate yanditswe ku "
                            + "mwanya wa mbere mu nyungu za NLS.",
                    false);

            /*
             * =============================================
             * INGINGO YA 7
             * =============================================
             */

            add(doc, "INGINGO YA 7", true);

            add(
                    doc,
                    "Ugurijwe ahaye NLS uburenganzira bwo "
                            + "gutanga amakuru arebana n’imyenda ye "
                            + "mu kigo gishinzwe amakuru ku myenda "
                            + "“Credit Reference Bureau Africa Ltd "
                            + "(CRB Africa)”. Iryo menyesha "
                            + "ntirifatwa nko kumena ibanga kwa NLS. "
                            + "Ugurijwe afite uburenganzira bwo kubaza, "
                            + "igihe icyo ari cyo cyose, icyo kigo "
                            + "uko amakuru y’inguzanyo ye "
                            + "yamenyeshejwe na NLS ameze. "
                            + "Iyo Ugurijwe abonye ikosa mu makuru "
                            + "yamenyeshejwe na NLS, abimenyesha "
                            + "NLS kugira ngo hakorwe ikosora.",
                    false);

            /*
             * =============================================
             * INGINGO YA 8
             * =============================================
             */

            add(doc, "INGINGO YA 8", true);

            add(
                    doc,
                    "Amafaranga yose n’ibihembo bizakoreshwa "
                            + "mu kubahiriza aya masezerano "
                            + "n’ingaruka zizayakomokaho, kimwe "
                            + "n’amafaranga azakoreshwa ku ngwate "
                            + "cyangwa mu kugaruza inguzanyo, "
                            + "yishyurwa n’Ugurijwe hakurikijwe "
                            + "ibiteganywa n’aya masezerano.",
                    false);

            add(
                    doc,
                    "Ugurijwe agomba kumenyesha NLS mu nyandiko "
                            + "igihe ahinduye aho atuye mu gihe "
                            + "aya masezerano agikurikizwa. "
                            + "NLS yemerewe koherereza Ugurijwe "
                            + "inyandiko aho atuye cyangwa ku "
                            + "murongo wa interineti.",
                    false);

            /*
             * =============================================
             * INGINGO YA 9
             * =============================================
             */

            add(doc, "INGINGO YA 9", true);

            add(
                    doc,
                    "Ugurijwe yemeye ko yasomye, yasobanukiwe "
                            + "kandi yemeye ibikubiye muri aya "
                            + "masezerano, abihamya ashyiraho umukono.",
                    false);

            /*
             * =============================================
             * INGINGO YA 10
             * =============================================
             */

            add(doc, "INGINGO YA 10", true);

            add(
                    doc,
                    "Impaka zose zaturuka kuri aya masezerano "
                            + "zizakemurwa n’inkiko zibifitiye "
                            + "ububasha zo mu Rwanda.",
                    false);

            add(
                    doc,
                    replace(
                            "Bikorewe i Kigali, ku wa {{DATE}}.",
                            values),
                    false);

            /*
             * =============================================
             * SIGNATURES AND NOTARY
             * =============================================
             */

            addOpenSignaturesForNotary(doc, b);

            /*
             * =============================================
             * REPEATING FOOTER
             * =============================================
             */

            XWPFFooter footer = doc.createFooter(
                    HeaderFooterType.DEFAULT);

            XWPFParagraph footerParagraph = footer.createParagraph();

            footerParagraph.setAlignment(
                    ParagraphAlignment.CENTER);

            XWPFRun footerRun = footerParagraph.createRun();

            footerRun.setFontFamily("Aptos");
            footerRun.setFontSize(8);
            footerRun.setColor("687384");

            footerRun.setText(
                    "NOBLE LOAN SOLUTIONS LTD  •  CONFIDENTIAL  •  "
                            + str(loan.getReferenceNumber())
                            + "  •  DRAFT / NOT SIGNED  •  PAGE ");

            footerParagraph.getCTP()
                    .addNewFldSimple()
                    .setInstr("PAGE");

            doc.getProperties()
                    .getCoreProperties()
                    .setTitle(
                            "Amasezerano y'iguriza - "
                                    + str(loan.getReferenceNumber()));

            doc.write(out);

            return out.toByteArray();

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to generate Noble Loan Solutions "
                            + "contract from Java",
                    ex);
        }
    }

    /*
     * =========================================================
     * WORD PARAGRAPH RENDERING
     * =========================================================
     */

    private static void add(
            XWPFDocument doc,
            String text,
            boolean heading) {
        XWPFParagraph p = doc.createParagraph();

        boolean section = text.startsWith("INGINGO YA ");
        boolean title = text.startsWith(
                "AMASEZERANO Y’IGURIZA");

        p.setSpacingBefore(
                section ? 245 : (title ? 120 : 0));

        p.setSpacingAfter(
                title ? 215 : (heading ? 120 : 112));

        p.setAlignment(
                title
                        ? ParagraphAlignment.CENTER
                        : heading
                                ? ParagraphAlignment.LEFT
                                : ParagraphAlignment.BOTH);

        if (heading) {
            var properties = p.getCTP().isSetPPr()
                    ? p.getCTP().getPPr()
                    : p.getCTP().addNewPPr();

            if (!properties.isSetKeepNext()) {
                properties.addNewKeepNext();
            }
        }

        XWPFRun r = p.createRun();

        r.setFontFamily("Carlito");
        r.setFontSize(title ? 15 : section ? 11 : 10);
        r.setColor(
                title ? "17324D" : section ? "926D25" : "253346");
        r.setBold(heading);

        String[] parts = text.split("\n", -1);

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                r.addBreak();
            }

            r.setText(parts[i]);
        }
    }

    /*
     * =========================================================
     * LOAN IDENTITY CARD
     * =========================================================
     */

    private static void addIdentityCard(
            XWPFDocument doc,
            Loan loan) {
        XWPFTable table = doc.createTable(2, 3);

        String[] labels = {
                "INOMERO Y’INGUZANYO",
                "ITARIKI",
                "IMITERERE"
        };

        String[] values = {
                str(loan.getReferenceNumber()),
                LocalDate.now().format(DATE),
                "DRAFT / NTIRASINYWA"
        };

        for (int c = 0; c < 3; c++) {
            setCell(table, 0, c, labels[c], true);
            setCell(table, 1, c, values[c], false);
        }

        doc.createParagraph().setSpacingAfter(60);
    }

    /*
     * =========================================================
     * FINANCIAL SUMMARY
     * =========================================================
     */

    private static void addFinancialOverview(
            XWPFDocument doc,
            Loan loan,
            BigDecimal monthlyInterest,
            BigDecimal monthlyManagement,
            BigDecimal applicationFee) {
        XWPFParagraph p = doc.createParagraph();

        p.setSpacingBefore(110);
        p.setSpacingAfter(75);

        XWPFRun heading = p.createRun();

        heading.setText(
                "INCAMAKE Y’INGUZANYO  /  LOAN SUMMARY");

        heading.setBold(true);
        heading.setFontFamily("Carlito");
        heading.setFontSize(10);
        heading.setColor("926D25");

        XWPFTable table = doc.createTable(2, 3);

        String[] labels = {
                "IGISHORO (RWF)",
                "IGIHE",
                "AYO KWISHYURA YOSE"
        };

        String[] values = {
                shortMoney(
                        loan.getAmountDecimal(),
                        loan.getCurrency()),
                durationText(loan.getDurationMonths()),
                shortMoney(
                        loan.getTotalRepayableDecimal(),
                        loan.getCurrency())
        };

        for (int c = 0; c < 3; c++) {
            setCell(table, 0, c, labels[c], true);
            setCell(table, 1, c, values[c], false);
        }

        doc.createParagraph().setSpacingAfter(75);
    }

    /*
     * =========================================================
     * ARTICLE 5 TABLE
     * =========================================================
     */

    private static void addArticleFiveConditionsTable(
            XWPFDocument doc) {
        String[][] conditions = {
                {
                        "a",
                        "Iyo inguzanyo yakoreshejwe mu bintu "
                                + "bitandukanye n’ibyo yasabiwe"
                },
                {
                        "b",
                        "Ubukererwe mu kwishyura burenze "
                                + "iminsi itatu"
                },
                {
                        "c",
                        "Habayeho kubeshya mu byo Ugurijwe "
                                + "yabwiye NLS cyangwa kutubahiriza "
                                + "zimwe mu nshingano Ugurijwe "
                                + "afitanye na NLS"
                },
                {
                        "d",
                        "Ibibazo bituruka ku kwanga kwandikisha "
                                + "ingwate ku Mwanditsi Mukuru, "
                                + "kutajya kwa noteri, gutakaza "
                                + "agaciro kose cyangwa k’igice "
                                + "cy’ingwate, cyangwa umwishingizi "
                                + "watanzwe ku mwenda n’ibindi "
                                + "byose yasabwa na NLS"
                },
                {
                        "e",
                        "Guhomba, iburira ry’igihombo, "
                                + "imenyekanisha ry’igihombo "
                                + "n’ikurikirana mu nkiko bigamije "
                                + "ihungabana ry’umutungo "
                                + "cyangwa ry’Ugurijwe"
                }
        };

        XWPFTable table = doc.createTable(conditions.length, 2);

        table.setWidth("100%");

        var grid = table.getCTTbl().getTblGrid();

        if (grid == null) {
            grid = table.getCTTbl().addNewTblGrid();
        }

        while (grid.sizeOfGridColArray() < 2) {
            grid.addNewGridCol();
        }

        grid.getGridColArray(0).setW(
                BigInteger.valueOf(950));

        grid.getGridColArray(1).setW(
                BigInteger.valueOf(8550));

        for (int i = 0; i < conditions.length; i++) {
            setCell(
                    table,
                    i,
                    0,
                    conditions[i][0] + ")",
                    true);

            setCell(
                    table,
                    i,
                    1,
                    conditions[i][1],
                    false);

            table.getRow(i)
                    .getCell(0)
                    .setWidth("10%");

            table.getRow(i)
                    .getCell(1)
                    .setWidth("90%");

            table.getRow(i)
                    .getCell(0)
                    .setColor("F3EBD7");

            if (i % 2 == 1) {
                table.getRow(i)
                        .getCell(1)
                        .setColor("F8F9FB");
            }
        }

        doc.createParagraph().setSpacingAfter(90);
    }

    /*
     * =========================================================
     * SIGNATURE AND NOTARY AREA
     * =========================================================
     */

    private static void addOpenSignaturesForNotary(
            XWPFDocument doc,
            Borrower borrower) {
        add(
                doc,
                "IMIKONO Y’ABAGIRANYE AMASEZERANO",
                true);

        addSignaturePair(
                doc,
                "UGURIJWE / BORROWER",
                "UGURIJE / LENDER",
                true,
                105);

        addSignaturePair(
                doc,
                str(borrower.getFirstName())
                        + " "
                        + str(borrower.getLastName()),
                "TUYISENGE Annuarite",
                false,
                45);

        addSignaturePair(
                doc,
                "",
                "Umuyobozi Mukuru — Noble Loan Solutions Ltd",
                false,
                140);

        addSignaturePair(
                doc,
                "Umukono: ______________________",
                "Umukono: ______________________",
                false,
                200);

        addSignaturePair(
                doc,
                "Itariki: ________________________",
                "Itariki: ________________________",
                false,
                180);

        XWPFParagraph notary = doc.createParagraph();

        notary.setSpacingBefore(170);
        notary.setSpacingAfter(65);

        XWPFRun label = notary.createRun();

        label.setText(
                "AHA HAGENEWE KASHE N’UMUKONO BYA NOTERI");

        label.setFontFamily("Carlito");
        label.setFontSize(10);
        label.setBold(true);
        label.setColor("17324D");

        // Blank space for real notarial signatures and stamp.
        // Not a table.
        XWPFParagraph stampSpace = doc.createParagraph();

        stampSpace.setSpacingAfter(1900);
    }

    private static void addSignaturePair(
            XWPFDocument doc,
            String left,
            String right,
            boolean emphasis,
            int spacingAfter) {
        XWPFParagraph p = doc.createParagraph();

        p.setSpacingAfter(spacingAfter);

        var properties = p.getCTP().isSetPPr()
                ? p.getCTP().getPPr()
                : p.getCTP().addNewPPr();

        var tabs = properties.isSetTabs()
                ? properties.getTabs()
                : properties.addNewTabs();

        var tab = tabs.addNewTab();

        tab.setVal(
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.LEFT);

        tab.setPos(BigInteger.valueOf(4900));

        XWPFRun r = p.createRun();

        r.setFontFamily("Carlito");
        r.setFontSize(9);
        r.setColor(
                emphasis ? "17324D" : "253346");
        r.setBold(emphasis);

        r.setText(left);
        r.addTab();
        r.setText(right);
    }

    /*
     * =========================================================
     * TEMPLATE SUBSTITUTION
     * =========================================================
     */

    private static String replace(
            String text,
            Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {

            text = text.replace(
                    "{{" + entry.getKey() + "}}",
                    entry.getValue());
        }

        if (text.contains("{{")) {
            throw new IllegalStateException(
                    "Unresolved contract variable: " + text);
        }

        return text;
    }

    private static String str(Object value) {
        return value == null ||
                value.toString().isBlank()
                        ? "[KUZUZA]"
                        : value.toString().trim();
    }

    private static String optional(Object value) {
        return value == null ||
                value.toString().isBlank()
                        ? "ntabwo byatanzwe"
                        : value.toString().trim();
    }

    private static String pct(BigDecimal value) {
        return value == null
                ? "[KUZUZA]"
                : value.stripTrailingZeros().toPlainString() + "%";
    }

    /*
     * =========================================================
     * KINYARWANDA MONEY FORMATTER
     * FIXED INSIDE THIS SAME CLASS
     * =========================================================
     */

    private static String money(
            BigDecimal value,
            String currency) {
        if (value == null) {
            return "[KUZUZA]";
        }

        if (value.signum() < 0) {
            throw new IllegalArgumentException(
                    "Negative contract amount: " + value);
        }

        BigDecimal rounded = value.setScale(
                2,
                RoundingMode.HALF_UP);

        String figure = String.format(
                Locale.US,
                "%,.2f",
                rounded);

        if (!"RWF".equalsIgnoreCase(currency)) {
            return str(currency) + " " + figure;
        }

        // Fractional amounts stay numerical.
        // Do not create incorrect fractional wording.
        if (rounded.stripTrailingZeros().scale() > 0) {
            return "RWF " + figure;
        }

        BigInteger whole = rounded.toBigIntegerExact();

        String wholeFigure = String.format(
                Locale.US,
                "%,.0f",
                rounded);

        if (whole.equals(BigInteger.ONE)) {
            return "ifaranga rimwe ry’u Rwanda "
                    + "(RWF 1)";
        }

        // IMPORTANT:
        // The currency description comes BEFORE the words.
        // Never append "z'amafaranga y'u Rwanda".
        return "amafaranga y’u Rwanda "
                + rwfWords(whole)
                + " (RWF "
                + wholeFigure
                + ")";
    }

    /**
     * Converts whole amounts to Kinyarwanda words.
     *
     * The method returns number words only.
     * It does not add a currency suffix.
     */
    private static String rwfWords(BigInteger value) {

        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    "Invalid monetary amount");
        }

        if (value.signum() == 0) {
            return "zeru";
        }

        if (value.compareTo(
                new BigInteger("999999999999999")) > 0) {
            throw new IllegalArgumentException(
                    "Amount too large for Kinyarwanda conversion");
        }

        String[] scales = {
                "",
                "ibihumbi",
                "miliyoni",
                "miliyari",
                "tiriyoni"
        };

        BigInteger thousand = BigInteger.valueOf(1000);

        List<String> pieces = new ArrayList<>();

        int scale = 0;

        while (value.signum() > 0) {

            int group = value.mod(thousand).intValue();

            if (group > 0) {
                String part;

                if (scale == 0) {
                    // Amounts below one thousand or the
                    // remainder after the larger number groups.
                    String units = underThousand(group, 0);

                    if (pieces.isEmpty()) {
                        part = units;
                    } else if (group == 1) {
                        part = "ifaranga rimwe";
                    } else {
                        part = "amafaranga " + units;
                    }

                } else if (scale == 1) {

                    if (group == 1) {
                        part = "igihumbi kimwe";
                    } else {
                        part = "ibihumbi "
                                + underThousand(group, 1);
                    }

                } else {

                    if (group == 1) {
                        part = scales[scale] + " imwe";
                    } else {
                        part = scales[scale]
                                + " "
                                + underThousand(group, 2);
                    }
                }

                pieces.add(0, part);
            }

            value = value.divide(thousand);
            scale++;
        }

        String result = pieces.get(0);

        for (int i = 1; i < pieces.size(); i++) {
            result = joinKinyarwanda(
                    result,
                    pieces.get(i));
        }

        return result;
    }

    /**
     * 0 = basic units
     * 1 = thousands (ibihumbi)
     * 2 = millions / billions / trillions
     */
    private static String underThousand(
            int value,
            int category) {
        if (value < 1 || value > 999) {
            throw new IllegalArgumentException(
                    "Number must be between 1 and 999");
        }

        String[] basicUnits = {
                "",
                "rimwe",
                "abiri",
                "atatu",
                "ane",
                "atanu",
                "atandatu",
                "arindwi",
                "umunani",
                "icyenda"
        };

        String[] thousandUnits = {
                "",
                "kimwe",
                "bibiri",
                "bitatu",
                "bine",
                "bitanu",
                "bitandatu",
                "birindwi",
                "umunani",
                "icyenda"
        };

        String[] millionUnits = {
                "",
                "imwe",
                "ebyiri",
                "eshatu",
                "enye",
                "eshanu",
                "esheshatu",
                "zirindwi",
                "umunani",
                "icyenda"
        };

        String[] units = category == 1
                ? thousandUnits
                : category == 2
                        ? millionUnits
                        : basicUnits;

        String[] tens = {
                "",
                "icumi",
                "makumyabiri",
                "mirongo itatu",
                "mirongo ine",
                "mirongo itanu",
                "mirongo itandatu",
                "mirongo irindwi",
                "mirongo inani",
                "mirongo icyenda"
        };

        String[] hundreds = {
                "",
                "ijana",
                "magana abiri",
                "magana atatu",
                "magana ane",
                "magana atanu",
                "magana atandatu",
                "magana arindwi",
                "magana inani",
                "magana icyenda"
        };

        List<String> parts = new ArrayList<>();

        if (value >= 100) {
            parts.add(hundreds[value / 100]);
        }

        int remainder = value % 100;

        if (remainder >= 20) {
            String phrase = tens[remainder / 10];

            int ones = remainder % 10;

            if (ones > 0) {
                phrase = joinKinyarwanda(
                        phrase,
                        units[ones]);
            }

            parts.add(phrase);

        } else if (remainder >= 10) {

            String phrase;

            if (remainder == 10) {
                phrase = "icumi";
            } else {
                phrase = joinKinyarwanda(
                        "cumi",
                        units[remainder - 10]);
            }

            parts.add(phrase);

        } else if (remainder > 0) {

            parts.add(units[remainder]);
        }

        String result = parts.get(0);

        for (int i = 1; i < parts.size(); i++) {
            result = joinKinyarwanda(
                    result,
                    parts.get(i));
        }

        return result;
    }

    /**
     * Correct Kinyarwanda conjunction:
     *
     * cumi + eshatu = cumi n’eshatu
     * miliyoni + ibihumbi = miliyoni n’ibihumbi
     *
     * Consonants use "na".
     */
    private static String joinKinyarwanda(
            String first,
            String second) {
        if (first == null || first.isBlank()) {
            return second;
        }

        if (second == null || second.isBlank()) {
            return first;
        }

        char firstLetter = Character.toLowerCase(
                second.charAt(0));

        boolean vowel = firstLetter == 'a' ||
                firstLetter == 'e' ||
                firstLetter == 'i' ||
                firstLetter == 'o' ||
                firstLetter == 'u';

        return first
                + (vowel ? " n’" : " na ")
                + second;
    }

    /*
     * =========================================================
     * DURATION IN KINYARWANDA
     * =========================================================
     */

    private static String durationText(Object value) {

        if (value == null) {
            return "[KUZUZA]";
        }

        int months;

        try {
            months = Integer.parseInt(
                    value.toString().trim());
        } catch (NumberFormatException ex) {
            return "amezi " + str(value);
        }

        if (months <= 0) {
            throw new IllegalArgumentException(
                    "Loan duration must be positive");
        }

        String[] names = {
                "",
                "ukwezi kumwe",
                "amezi abiri",
                "amezi atatu",
                "amezi ane",
                "amezi atanu",
                "amezi atandatu",
                "amezi arindwi",
                "amezi umunani",
                "amezi icyenda",
                "amezi icumi",
                "amezi cumi n’imwe",
                "amezi cumi n’abiri"
        };

        if (months < names.length) {
            return names[months]
                    + " ("
                    + months
                    + ")";
        }

        return "amezi " + months;
    }

    /*
     * =========================================================
     * LOAN PURPOSE LANGUAGE
     * =========================================================
     */

    private static String purpose(Object value) {

        String text = str(value);

        String normalized = text
                .toLowerCase(Locale.ROOT)
                .trim();

        // Explicit, safe translation of the known loan purpose.
        // Unknown or free-text purposes are preserved.
        if (normalized.equals("buy a car")
                || normalized.equals("car purchase")
                || normalized.equals("purchase a car")
                || normalized.equals("buy car")
                || normalized.equals("vehicle purchase")) {

            return "kugura imodoka";
        }

        return text;
    }

    /*
     * =========================================================
     * SAVED REPAYMENT SCHEDULE
     * =========================================================
     */

    private static void addSchedule(
            XWPFDocument doc,
            List<PaymentSchedule> schedules,
            String currency,
            BigDecimal totalRepayable) {
        if (schedules == null || schedules.isEmpty()) {
            add(
                    doc,
                    "[GAHUNDA YO KWISHYURA NTIRABONEKA "
                            + "— NTIMWOHEREZE AMASEZERANO]",
                    false);
            return;
        }

        XWPFTable table = doc.createTable(
                schedules.size() + 2,
                4);

        String[] headers = {
                "IGICE",
                "ITARIKI",
                "IGISHORO",
                "YOSE HAMWE"
        };

        for (int c = 0; c < headers.length; c++) {
            setCell(
                    table,
                    0,
                    c,
                    headers[c],
                    true);
        }

        table.getRow(0)
                .getCtRow()
                .addNewTrPr()
                .addNewTblHeader();

        BigDecimal sum = BigDecimal.ZERO;
        boolean incomplete = false;

        for (int i = 0; i < schedules.size(); i++) {

            PaymentSchedule item = schedules.get(i);
            int row = i + 1;

            setCell(
                    table,
                    row,
                    0,
                    str(item.getInstallmentNumber()),
                    false);

            setCell(
                    table,
                    row,
                    1,
                    item.getDueDate() == null
                            ? "[ITARIKI IBURA]"
                            : item.getDueDate().format(DATE),
                    false);

            setCell(
                    table,
                    row,
                    2,
                    shortMoney(
                            item.getPrincipalAmount(),
                            currency),
                    false);

            setCell(
                    table,
                    row,
                    3,
                    shortMoney(
                            item.getInstallmentAmount(),
                            currency),
                    false);

            if (item.getDueDate() == null ||
                    item.getInstallmentAmount() == null ||
                    item.getInstallmentAmount().signum() <= 0 ||
                    item.getPrincipalAmount() == null) {

                incomplete = true;
            }

            if (item.getInstallmentAmount() != null) {
                sum = sum.add(
                        item.getInstallmentAmount());
            }
        }

        int last = schedules.size() + 1;

        setCell(
                table,
                last,
                0,
                "IGITERANYO",
                true);

        setCell(table, last, 1, "", true);
        setCell(table, last, 2, "", true);

        setCell(
                table,
                last,
                3,
                shortMoney(sum, currency),
                true);

        if (totalRepayable == null ||
                sum.setScale(
                        2,
                        RoundingMode.HALF_UP).compareTo(
                                totalRepayable.setScale(
                                        2,
                                        RoundingMode.HALF_UP)) != 0) {

            incomplete = true;
        }

        if (incomplete) {
            add(
                    doc,
                    "[KUZUZA] Gahunda yo kwishyura ntihuye "
                            + "n’amafaranga yose ateganyijwe "
                            + "cyangwa ifite amakuru atuzuye. "
                            + "Ntimuwohereze.",
                    false);
        }

        doc.createParagraph();
    }

    private static String shortMoney(
            BigDecimal amount,
            String currency) {
        if (amount == null) {
            return "[KUZUZA]";
        }

        return str(currency)
                + " "
                + String.format(
                        Locale.US,
                        "%,.2f",
                        amount);
    }

    /*
     * =========================================================
     * WORD TABLE CELLS
     * =========================================================
     */

    private static void setCell(
            XWPFTable table,
            int row,
            int col,
            String text,
            boolean header) {
        XWPFTableCell cell = table.getRow(row).getCell(col);

        if (header) {
            cell.setColor("F3EBD7");
        }

        XWPFRun r = cell.getParagraphs()
                .get(0)
                .createRun();

        r.setText(text);
        r.setFontFamily("Carlito");
        r.setFontSize(header ? 8 : 9);
        r.setBold(header);
        r.setColor(
                header ? "17324D" : "253346");
    }

    /*
     * =========================================================
     * BORROWER ADDRESS
     * =========================================================
     */

    private static String address(Borrower b) {

        StringJoiner sj = new StringJoiner(", ");

        Object[] parts = {
                b.getPhysicalAddressVillage(),
                b.getPhysicalAddressCell(),
                b.getPhysicalAddressSector(),
                b.getPhysicalAddressDistrict(),
                b.getCity(),
                b.getCountry()
        };

        for (Object part : parts) {
            if (part != null &&
                    !part.toString().isBlank()) {

                sj.add(
                        part.toString().trim());
            }
        }

        return sj.length() == 0
                ? str(b.getAddress())
                : sj.toString();
    }
}

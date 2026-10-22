
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;

import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;

import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Noble Loan Solutions Ltd loan agreement.
 *
 * The entire legal agreement is authored in Java.
 * No DOCX template is read at runtime.
 *
 * The approved NLS logo is supplied as a static PNG resource.
 *
 * The source agreement's 10 articles are retained.
 * Variable loan terms come from the saved Loan entity and
 * PaymentSchedule records.
 *
 * IMPORTANT:
 * Legal provisions and the Kinyarwanda number wording should
 * be reviewed and approved before first production use.
 */
final class LoanContractTemplateRenderer {

    private LoanContractTemplateRenderer() {
    }

    private static final String LOGO_RESOURCE = "/branding/nls-logo.png";

    private static final String NAVY = "17324D";
    private static final String GOLD = "A78329";
    private static final String TEXT = "27374A";
    private static final String MUTED = "667085";
    private static final String LIGHT = "F5F7FA";

    private static final String FONT = "Aptos";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static byte[] render(
            Loan loan,
            List<PaymentSchedule> schedules) {
        if (loan == null || loan.getBorrower() == null) {
            throw new IllegalArgumentException(
                    "A saved loan and borrower are required.");
        }

        if (loan.getAmountDecimal() == null
                || loan.getAmountDecimal().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Loan principal must be greater than zero.");
        }

        Borrower borrower = loan.getBorrower();

        BigDecimal principal = loan.getAmountDecimal();
        BigDecimal interestRate = loan.getInterestRateDecimal();
        BigDecimal managementRate = loan.getManagementFeeRateDecimal();
        BigDecimal applicationRate = loan.getApplicationFeeRateDecimal();
        BigDecimal penaltyRate = loan.getPenaltyRateDecimal();

        String currency = value(loan.getCurrency());

        boolean annual = "ANNUAL".equalsIgnoreCase(
                String.valueOf(loan.getInterestRateType()));

        BigDecimal firstMonthInterest = calculateRate(
                principal,
                interestRate,
                annual ? new BigDecimal("1200")
                        : new BigDecimal("100"));

        BigDecimal firstMonthManagement = calculateRate(
                principal,
                managementRate,
                new BigDecimal("100"));

        BigDecimal applicationFee = loan.getApplicationFeeDecimal();

        if (applicationFee == null) {
            applicationFee = calculateRate(
                    principal,
                    applicationRate,
                    new BigDecimal("100"));
        }

        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            configurePage(document);
            createHeader(document);
            createFooter(document, loan);

            // CONTRACT TITLE
            title(
                    document,
                    "AMASEZERANO Y’IGURIZA NA "
                            + "NOBLE LOAN SOLUTIONS LTD");

            contractMetadata(document, loan);

            // PARTIES
            paragraph(
                    document,
                    "NOBLE LOAN SOLUTIONS LTD, yemerewe "
                            + "kugurizanya ifite icyemezo cya Banki "
                            + "Nkuru y’u Rwanda (BNR) "
                            + "No 7420/2025-00211/7000 BNR, "
                            + "ifite icyicaro Gisozi, "
                            + "ihagarariwe na Madamu Annuarite Tuyisenge, "
                            + "Umuyobozi Mukuru wa NLS, "
                            + "yitwa “Noble Loan Solutions Ltd” "
                            + "muri aya masezerano ku ruhande rumwe.");

            paragraph(
                    document,
                    "Bwana/Madamu "
                            + value(borrower.getFirstName()) + " "
                            + value(borrower.getLastName())
                            + ", ufite indangamuntu No: "
                            + value(borrower.getNationalId())
                            + ", telefone: "
                            + value(borrower.getPhone())
                            + ", email: "
                            + optional(borrower.getEmail())
                            + ". Uwo bashakanye: "
                            + optional(borrower.getSpouseFullName())
                            + ", indangamuntu No: "
                            + optional(borrower.getSpouseNationalId())
                            + ". Aho atuye: "
                            + address(borrower)
                            + ".");

            paragraph(
                    document,
                    "Muri aya masezerano akaba yitwa "
                            + "“Ugurijwe”, ku rundi ruhande.");

            prominent(
                    document,
                    "HEMERANYIJWE IBI BIKURIKIRA:");

            // ARTICLE 1
            article(document, "INGINGO YA 1", "Ubusobanuro");

            paragraph(
                    document,
                    "Muri aya masezerano, aya magambo "
                            + "asobanuwe mu buryo bukurikira, "
                            + "uretse aho aya masezerano "
                            + "abiteganya ukundi:");

            paragraph(
                    document,
                    "1.1 “Noble Loan Solutions Ltd” "
                            + "isobanura “Ugurije”, "
                            + "ikigo cyemerewe kuguriza.");

            paragraph(
                    document,
                    "1.2 “Ugurijwe” ni umuntu cyangwa "
                            + "company bigaragara muri aya "
                            + "masezerano ahabanza.");

            paragraph(
                    document,
                    "1.3 NLS ni impine isobanura "
                            + "Noble Loan Solutions Ltd.");

            // ARTICLE 2
            article(document, "INGINGO YA 2", null);

            paragraph(
                    document,
                    "NLS ihaye inguzanyo ugurijwe, "
                            + "ikurikije amategeko-ngenga yayo, "
                            + "Politiki y’inguzanyo, ndetse "
                            + "n’aya masezerano, inguzanyo "
                            + "y’amafaranga angana na "
                            + moneyInWords(principal, currency)
                            + " yo gukoresha mu rwego rwa "
                            + value(loan.getPurpose())
                            + ". Inomero y’inguzanyo ni "
                            + value(loan.getReferenceNumber())
                            + ".");

            // ARTICLE 3
            article(
                    document,
                    "INGINGO YA 3",
                    "Inyungu ku nguzanyo, amafaranga yo "
                            + "gukurikirana inguzanyo "
                            + "n’umufuragiro ku nguzanyo");

            bullet(
                    document,
                    "Iyi nguzanyo ibarirwa ku nyungu "
                            + "fatizo ya "
                            + percent(interestRate)
                            + (annual ? " ku mwaka" : " ku kwezi")
                            + ". Ku mushoro w’ibanze, "
                            + "ikigereranyo cy’inyungu "
                            + "z’ukwezi kwa mbere ni "
                            + moneyInWords(
                                    firstMonthInterest,
                                    currency)
                            + ". Inyungu zibarwa "
                            + "ku mushoro usigaye.");

            bullet(
                    document,
                    "Mu gihe cyo gutanga inguzanyo "
                            + "hishyurwa amafaranga yo kwiga "
                            + "dosiye, umufuragiro w’inguzanyo "
                            + "hakurikijwe ijanisha rya "
                            + percent(applicationRate)
                            + ", ungana na "
                            + moneyInWords(
                                    applicationFee,
                                    currency)
                            + ". Ayo mafaranga akatwa "
                            + "ku nguzanyo igihe cyo "
                            + "kuyitanga.");

            bullet(
                    document,
                    "Ugurijwe yishyura buri kwezi "
                            + "NLS Ltd amafaranga yo "
                            + "gukurikirana inguzanyo "
                            + "abarirwa kuri "
                            + percent(managementRate)
                            + " ku kwezi ku mushoro "
                            + "usigaye. Ku mushoro w’ibanze, "
                            + "ikigereranyo cy’ukwezi "
                            + "kwa mbere ni "
                            + moneyInWords(
                                    firstMonthManagement,
                                    currency)
                            + ".");

            bullet(
                    document,
                    "Iyo ugurijwe atishyuye inguzanyo "
                            + "ye mu gihe cyagenwe nk’uko "
                            + "biteganywa n’aya masezerano, "
                            + "yishyura igihano cy’ubukererwe "
                            + "ku gipimo cya "
                            + percent(penaltyRate)
                            + " ku kwezi ku mafaranga "
                            + "ari mu bukererwe, "
                            + "hakurikijwe iminsi y’inyongera "
                            + "n’uburyo bwo kubara ibihano "
                            + "byemejwe kuri iyo nguzanyo.");

            // ARTICLE 4
            article(
                    document,
                    "INGINGO YA 4",
                    "Igihe cy’inguzanyo n’uburyo bwo kwishyura");

            paragraph(
                    document,
                    "Umwenda wose, harimo umushoro, "
                            + "inyungu n’amafaranga yo "
                            + "gukurikirana inguzanyo, "
                            + "uzishyurwa mu gihe cy’amezi "
                            + value(loan.getDurationMonths())
                            + ". Amafaranga yose "
                            + "ateganyijwe kwishyurwa ni "
                            + moneyInWords(
                                    loan.getTotalRepayableDecimal(),
                                    currency)
                            + ".");

            subsection(
                    document,
                    "Gahunda yo kwishyura");

            repaymentSchedule(
                    document,
                    schedules,
                    loan);

            importantParagraph(
                    document,
                    "NB: Amafaranga yose yishyurwa kuri "
                            + "Konti NIMERO 100236776071 RWF "
                            + "YA NOBLE LOAN SOLUTIONS LTD "
                            + "iri muri BANK OF KIGALI.");

            paragraph(
                    document,
                    "Abiherewe uburenganzira na NLS, "
                            + "Ugurijwe ashobora kwishyura "
                            + "umwenda wose cyangwa igice "
                            + "cyawo mbere y’igihe "
                            + "cyateganyijwe mu masezerano, "
                            + "agasigara yishyura inyungu "
                            + "zayo azigaje kwishyura "
                            + "ndetse hagakorwa amasezerano "
                            + "mashya.");

            paragraph(
                    document,
                    "Mu gihe habayeho kugura umwenda "
                            + "bikozwe n’ikigo cy’imari "
                            + "cyangwa indi company ikora "
                            + "imirimo ijya gusa n’iya NLS, "
                            + "mbere y’uko itariki ya nyuma "
                            + "igera, NLS yishyurwa inyungu "
                            + "zose zari ziteganyijwe.");

            // ARTICLE 5
            article(
                    document,
                    "INGINGO YA 5",
                    "Gusesa amasezerano");

            paragraph(
                    document,
                    "NLS ifite uburenganzira bwo gusesa "
                            + "aya masezerano nta nteguza "
                            + "cyangwa kandi igahita yishyuza "
                            + "amafaranga yose ako kanya "
                            + "ibinyujije mu ibaruwa ishinganye "
                            + "cyangwa, mu gihe bidashobotse, "
                            + "ibaruwa igashyikirizwa ugurijwe "
                            + "aho atuye iyo bigaragaye ko:");

            lettered(
                    document,
                    "a",
                    "Iyo inguzanyo yakoreshejwe mu bintu "
                            + "bitandukanye n’ibyo yasabiwe.");

            lettered(
                    document,
                    "b",
                    "Ubukererwe mu kwishyura burenze "
                            + "iminsi itatu.");

            lettered(
                    document,
                    "c",
                    "Habayeho kubeshya mu byo Ugurijwe "
                            + "yabwiye NLS cyangwa "
                            + "kutubahiriza zimwe "
                            + "mu nshingano Ugurijwe "
                            + "afitanye na NLS.");

            lettered(
                    document,
                    "d",
                    "Ibibazo bituruka ku kwanga "
                            + "kwandikisha ingwate "
                            + "k’Umwanditsi Mukuru, "
                            + "kutajya kwa noteri, "
                            + "gutakaza agaciro kose "
                            + "cyangwa k’igice k’ingwate, "
                            + "cyangwa umwishingizi "
                            + "watanzwe ku mwenda "
                            + "n’ibindi byose yasabwa na NLS.");

            lettered(
                    document,
                    "e",
                    "Guhomba, iburira ry’igihombo, "
                            + "imenyekanisha ry’igihombo "
                            + "n’ikurikirana mu Nkiko "
                            + "bigamije ihungabana "
                            + "ry’umutungo cyangwa "
                            + "ry’Ugurijwe.");

            paragraph(
                    document,
                    "Hashingiwe ku byavuzwe mu gika "
                            + "cyo hejuru, kuba NLS yaba "
                            + "izi impamvu yagatumye isesa "
                            + "aya masezerano maze "
                            + "igakomeza ikagira igikorwa "
                            + "ikora kirebana no kubahiriza "
                            + "aya masezerano, ntabwo "
                            + "bivuga ko itafata icyemezo "
                            + "cyo kuyasesa nta nteguza "
                            + "nk’uko byavuzwe haruguru.");

            // ARTICLE 6
            article(
                    document,
                    "INGINGO YA 6",
                    "Inshingano zo kwishyura n’ingwate");

            paragraph(
                    document,
                    "Uwagurijwe yemeye kubahiriza "
                            + "gahunda yo kwishyura "
                            + "nk’uko yakabaye. Iyo "
                            + "akererewe iminsi itatu "
                            + "birahagije ko aburiwe "
                            + "bwa nyuma kandi yirengera "
                            + "ingaruka zose ziteganywa "
                            + "n’amategeko ndetse "
                            + "n’aya masezerano.");

            paragraph(
                    document,
                    "Uretse uburenganzira “UGURIJE” "
                            + "ari we NLS afite ku ngwate "
                            + "zose Ugurijwe atanze no kuba "
                            + "yagurishwa mu cyamunara "
                            + "hakurikijwe amategeko "
                            + "abigenga mu rwego rwo "
                            + "kugaruza umwenda waba "
                            + "usigaye ndetse "
                            + "n’ibiwukomokaho, NLS "
                            + "inafite kandi uburenganzira "
                            + "bwo kuregera iyindi mitungo "
                            + "ye cyangwa iy’umwishingizi "
                            + "we yaba iyimukanwa ndetse "
                            + "n’itimukanwa, amafaranga "
                            + "ye abikije mu bigo by’imari "
                            + "byose, byishingira inguzanyo "
                            + "yose mu kwishyura NLS "
                            + "mu gihe Ugurijwe azaba "
                            + "yananiwe kwishyura cyangwa "
                            + "ingwate yagurishijwe "
                            + "ntihaboneke amafaranga "
                            + "yo kurangiza umwenda "
                            + "wose waba usigaye, "
                            + "kandi ku ngwate zatanzwe, "
                            + "NLS iza imbere ku bandi "
                            + "ugurijwe abereyemo imyenda "
                            + "iyo ingwate yatanzwe "
                            + "ku mwanya wa mbere muri NLS.");

            // ARTICLE 7
            article(
                    document,
                    "INGINGO YA 7",
                    "Gutanga amakuru ku nguzanyo");

            paragraph(
                    document,
                    "Uwagurijwe ahaye NLS uburenganzira "
                            + "bwo gutanga amakuru arebana "
                            + "n’imyenda ye mu Kigo "
                            + "gishinzwe ibijyanye n’imyenda "
                            + "«Credit Reference Bureau "
                            + "Africa Ltd (CRB Africa)». "
                            + "Iryo menyekanisha "
                            + "ntirishobora gufatwa "
                            + "nko kumena amabanga kuri NLS. "
                            + "Ugurijwe afite uburenganzira "
                            + "bwo kubaza, igihe icyo ari cyo "
                            + "cyose, mu Kigo gishinzwe "
                            + "ibijyanye n’imyenda "
                            + "«Credit Reference Bureau "
                            + "Africa Limited» uko umwenda "
                            + "we wamenyekanishijwe na NLS "
                            + "uteye. Igihe Ugurijwe abonye "
                            + "mu imenyekanisha ryakozwe "
                            + "na NLS harabayemo ikosa, "
                            + "ahita abimenyesha NLS "
                            + "hagakorwa ikosora.");

            // ARTICLE 8
            article(
                    document,
                    "INGINGO YA 8",
                    "Amafaranga n’imenyesha");

            paragraph(
                    document,
                    "Amafaranga yose n’ibihembo "
                            + "bizakoreshwa mu kubahiriza "
                            + "aya masezerano n’ingaruka "
                            + "zizayakomokaho kimwe "
                            + "n’azagenda ku ngwate ndetse "
                            + "n’ayo NLS yakoresha kugira ngo "
                            + "igaruze inguzanyo cyangwa "
                            + "ingwate zatanzwe, yishyurwa "
                            + "n’Ugurijwe kandi akayatanga "
                            + "akiyabazwa nta yandi mananiza.");

            paragraph(
                    document,
                    "Ugurijwe agomba kumenyesha NLS, "
                            + "mu nyandiko, igihe bibaye "
                            + "ngombwa ko ahinduye aho "
                            + "yari atuye mu gihe aya "
                            + "masezerano acyubahirijwe. "
                            + "NLS yemerewe koherereza "
                            + "inyandiko Ugurijwe aho "
                            + "atuye cyangwa ku murongo "
                            + "wa interineti.");

            // ARTICLE 9
            article(
                    document,
                    "INGINGO YA 9",
                    "Kwemera amasezerano");

            paragraph(
                    document,
                    "Uwagurijwe yemeye ko "
                            + "asobanukiwe akanemera "
                            + "ibikubiye muri aya "
                            + "masezerano ashyiraho umukono.");

            // ARTICLE 10
            article(
                    document,
                    "INGINGO YA 10",
                    "Gukemura impaka");

            paragraph(
                    document,
                    "Impaka zose zaturuka kuri aya "
                            + "masezerano zizakemurwa "
                            + "n’Inkiko zibifitiye "
                            + "ububasha zo mu Rwanda.");

            prominent(
                    document,
                    "Bikorewe Kigali, kuwa "
                            + LocalDate.now().format(DATE)
                            + ".");

            signatures(document, borrower);

            document.getProperties()
                    .getCoreProperties()
                    .setTitle(
                            "Amasezerano y'iguriza - "
                                    + value(
                                            loan.getReferenceNumber()));

            document.write(output);
            return output.toByteArray();

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to generate the Noble Loan "
                            + "Solutions contract from Java.",
                    ex);
        }
    }

    // =========================================================
    // PAGE DESIGN
    // =========================================================

    private static void configurePage(XWPFDocument document) {
        CTSectPr section = document.getDocument()
                .getBody()
                .isSetSectPr()
                        ? document.getDocument().getBody().getSectPr()
                        : document.getDocument().getBody().addNewSectPr();

        CTPageMar margins = section.isSetPgMar()
                ? section.getPgMar()
                : section.addNewPgMar();

        margins.setTop(BigInteger.valueOf(1950));
        margins.setBottom(BigInteger.valueOf(1100));
        margins.setLeft(BigInteger.valueOf(1100));
        margins.setRight(BigInteger.valueOf(1100));
        margins.setHeader(BigInteger.valueOf(300));
        margins.setFooter(BigInteger.valueOf(400));
    }

    private static void createHeader(
            XWPFDocument document) throws Exception {

        XWPFHeader header = document.createHeader(HeaderFooterType.DEFAULT);

        XWPFParagraph logoParagraph = header.createParagraph();

        logoParagraph.setAlignment(
                ParagraphAlignment.CENTER);
        logoParagraph.setSpacingAfter(40);

        try (InputStream logo = LoanContractTemplateRenderer.class
                .getResourceAsStream(LOGO_RESOURCE)) {

            if (logo == null) {
                throw new IllegalStateException(
                        "Original company logo is missing: "
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
        brand.setSpacingAfter(25);

        XWPFRun brandRun = brand.createRun();
        brandRun.setText(
                "NOBLE LOAN SOLUTIONS LTD");
        brandRun.setBold(true);
        brandRun.setFontFamily(FONT);
        brandRun.setFontSize(10);
        brandRun.setColor(NAVY);

        XWPFParagraph confidential = header.createParagraph();

        confidential.setAlignment(
                ParagraphAlignment.CENTER);

        XWPFRun confidentialRun = confidential.createRun();

        confidentialRun.setText(
                "IBANGA  •  CONFIDENTIAL");
        confidentialRun.setFontFamily(FONT);
        confidentialRun.setFontSize(8);
        confidentialRun.setBold(true);
        confidentialRun.setColor(GOLD);
    }

    private static void createFooter(
            XWPFDocument document,
            Loan loan) {
        XWPFFooter footer = document.createFooter(HeaderFooterType.DEFAULT);

        XWPFParagraph paragraph = footer.createParagraph();

        paragraph.setAlignment(
                ParagraphAlignment.CENTER);

        XWPFRun run = paragraph.createRun();
        run.setFontFamily(FONT);
        run.setFontSize(8);
        run.setColor(MUTED);

        run.setText(
                "NLS  •  "
                        + value(loan.getReferenceNumber())
                        + "  •  CONFIDENTIAL"
                        + "  •  DRAFT / NOT SIGNED"
                        + "  •  Page ");

        // Word automatically calculates the current page.
        paragraph.getCTP()
                .addNewFldSimple()
                .setInstr("PAGE");
    }

    private static void title(
            XWPFDocument document,
            String text) {
        XWPFParagraph paragraph = document.createParagraph();

        paragraph.setAlignment(
                ParagraphAlignment.CENTER);
        paragraph.setSpacingBefore(180);
        paragraph.setSpacingAfter(190);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily(FONT);
        run.setFontSize(15);
        run.setBold(true);
        run.setColor(NAVY);
    }

    private static void contractMetadata(
            XWPFDocument document,
            Loan loan) {
        XWPFTable table = document.createTable(2, 2);

        cell(
                table, 0, 0,
                "INOMERO Y’INGUZANYO",
                true);
        cell(
                table, 0, 1,
                "ITARIKI Y’AMASEZERANO",
                true);
        cell(
                table, 1, 0,
                value(loan.getReferenceNumber()),
                false);
        cell(
                table, 1, 1,
                LocalDate.now().format(DATE),
                false);

        document.createParagraph();
    }

    private static void article(
            XWPFDocument document,
            String number,
            String subtitle) {
        XWPFParagraph heading = document.createParagraph();

        heading.setSpacingBefore(250);
        heading.setSpacingAfter(70);

        keepWithNext(heading);

        XWPFRun run = heading.createRun();
        run.setText(number);
        run.setFontFamily(FONT);
        run.setFontSize(12);
        run.setBold(true);
        run.setColor(GOLD);

        if (subtitle != null && !subtitle.isBlank()) {
            XWPFParagraph p = document.createParagraph();

            p.setSpacingAfter(120);
            keepWithNext(p);

            XWPFRun subtitleRun = p.createRun();
            subtitleRun.setText(subtitle);
            subtitleRun.setFontFamily(FONT);
            subtitleRun.setFontSize(11);
            subtitleRun.setBold(true);
            subtitleRun.setColor(NAVY);
        }
    }

    private static void subsection(
            XWPFDocument document,
            String text) {
        XWPFParagraph p = document.createParagraph();
        p.setSpacingBefore(140);
        p.setSpacingAfter(90);
        keepWithNext(p);

        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontFamily(FONT);
        run.setFontSize(10);
        run.setColor(NAVY);
    }

    private static void paragraph(
            XWPFDocument document,
            String text) {
        XWPFParagraph p = document.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setSpacingAfter(115);

        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontFamily(FONT);
        run.setFontSize(10);
        run.setColor(TEXT);
    }

    private static void prominent(
            XWPFDocument document,
            String text) {
        XWPFParagraph p = document.createParagraph();
        p.setSpacingBefore(160);
        p.setSpacingAfter(140);
        keepWithNext(p);

        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontFamily(FONT);
        run.setFontSize(11);
        run.setBold(true);
        run.setColor(NAVY);
    }

    private static void importantParagraph(
            XWPFDocument document,
            String text) {
        XWPFParagraph p = document.createParagraph();

        p.setSpacingBefore(140);
        p.setSpacingAfter(140);

        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontFamily(FONT);
        run.setFontSize(10);
        run.setBold(true);
        run.setColor(NAVY);
    }

    private static void bullet(
            XWPFDocument document,
            String text) {
        XWPFParagraph p = document.createParagraph();
        p.setIndentationLeft(360);
        p.setIndentationHanging(180);
        p.setSpacingAfter(110);
        p.setAlignment(ParagraphAlignment.BOTH);

        XWPFRun run = p.createRun();
        run.setText("•  " + text);
        run.setFontFamily(FONT);
        run.setFontSize(10);
        run.setColor(TEXT);
    }

    private static void lettered(
            XWPFDocument document,
            String letter,
            String text) {
        XWPFParagraph p = document.createParagraph();
        p.setIndentationLeft(360);
        p.setIndentationHanging(180);
        p.setSpacingAfter(80);

        XWPFRun run = p.createRun();
        run.setText(letter + ")  " + text);
        run.setFontFamily(FONT);
        run.setFontSize(10);
        run.setColor(TEXT);
    }

    private static void keepWithNext(XWPFParagraph paragraph) {
        var properties = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr()
                : paragraph.getCTP().addNewPPr();

        if (!properties.isSetKeepNext()) {
            properties.addNewKeepNext();
        }
    }

    // =========================================================
    // REPAYMENT SCHEDULE
    // =========================================================

    private static void repaymentSchedule(
            XWPFDocument document,
            List<PaymentSchedule> schedules,
            Loan loan) {
        if (schedules == null || schedules.isEmpty()) {
            importantParagraph(
                    document,
                    "[GAHUNDA YO KWISHYURA NTIRABONEKA "
                            + "— NTIMWOHEREZE AMASEZERANO]");
            return;
        }

        XWPFTable table = document.createTable(
                schedules.size() + 2,
                3);

        cell(table, 0, 0, "IGICE", true);
        cell(table, 0, 1, "ITARIKI", true);
        cell(table, 0, 2, "AMAFARANGA", true);

        BigDecimal total = BigDecimal.ZERO;
        boolean incomplete = false;

        for (int index = 0; index < schedules.size(); index++) {
            PaymentSchedule schedule = schedules.get(index);
            int row = index + 1;

            cell(
                    table,
                    row,
                    0,
                    value(schedule.getInstallmentNumber()),
                    false);

            if (schedule.getDueDate() == null) {
                incomplete = true;
            }

            cell(
                    table,
                    row,
                    1,
                    schedule.getDueDate() == null
                            ? "[ITARIKI IBURA]"
                            : schedule.getDueDate().format(DATE),
                    false);

            BigDecimal amount = schedule.getInstallmentAmount();

            if (amount == null || amount.signum() <= 0) {
                incomplete = true;
            } else {
                total = total.add(amount);
            }

            cell(
                    table,
                    row,
                    2,
                    numericMoney(amount, loan.getCurrency()),
                    false);
        }

        int lastRow = schedules.size() + 1;

        cell(
                table,
                lastRow,
                0,
                "IGITERANYO",
                true);

        cell(
                table,
                lastRow,
                1,
                "",
                true);

        cell(
                table,
                lastRow,
                2,
                numericMoney(total, loan.getCurrency()),
                true);

        BigDecimal repayable = loan.getTotalRepayableDecimal();

        if (repayable == null
                || total.setScale(2, RoundingMode.HALF_UP)
                        .compareTo(repayable.setScale(
                                2,
                                RoundingMode.HALF_UP)) != 0) {
            incomplete = true;
        }

        if (incomplete) {
            importantParagraph(
                    document,
                    "[KUZUZA] Gahunda yo kwishyura "
                            + "ifite amakuru atuzuye "
                            + "cyangwa igiteranyo cyayo "
                            + "ntigihuye n’amafaranga "
                            + "yose ateganyijwe kwishyurwa. "
                            + "Ntimuwohereze.");
        }

        paragraph(
                document,
                "Amafaranga ya buri gice "
                        + "agaragajwe muri iyi gahunda "
                        + "agomba guhuza n’amakuru "
                        + "yemejwe kandi abitswe "
                        + "muri sisitemu.");
    }

    private static void cell(
            XWPFTable table,
            int row,
            int column,
            String text,
            boolean heading) {
        XWPFTableCell cell = table.getRow(row).getCell(column);

        if (heading) {
            cell.setColor(LIGHT);
        }

        XWPFParagraph p = cell.getParagraphs().get(0);

        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontFamily(FONT);
        run.setFontSize(9);
        run.setBold(heading);
        run.setColor(heading ? NAVY : TEXT);
    }

    // =========================================================
    // SIGNATURES
    // =========================================================

    private static void signatures(
            XWPFDocument document,
            Borrower borrower) {
        XWPFTable table = document.createTable(4, 2);

        cell(table, 0, 0, "UGURIJWE", true);
        cell(table, 0, 1, "UGURIJE", true);

        cell(
                table, 1, 0,
                value(borrower.getFirstName())
                        + " "
                        + value(borrower.getLastName()),
                false);

        cell(
                table, 1, 1,
                "TUYISENGE Annuarite\n"
                        + "Umuyobozi Mukuru\n"
                        + "Noble Loan Solutions Ltd",
                false);

        cell(
                table, 2, 0,
                "Umukono: _____________________",
                false);
        cell(
                table, 2, 1,
                "Umukono: _____________________",
                false);

        cell(
                table, 3, 0,
                "Itariki: _____________________",
                false);
        cell(
                table, 3, 1,
                "Itariki: _____________________",
                false);

        document.createParagraph();
    }

    // =========================================================
    // FINANCIAL VALUES AND KINYARWANDA NUMBER WORDING
    // =========================================================

    private static BigDecimal calculateRate(
            BigDecimal principal,
            BigDecimal rate,
            BigDecimal divisor) {
        if (principal == null || rate == null) {
            return null;
        }

        return principal.multiply(rate)
                .divide(
                        divisor,
                        2,
                        RoundingMode.HALF_UP);
    }

    private static String numericMoney(
            BigDecimal amount,
            String currency) {
        if (amount == null) {
            return "[KUZUZA]";
        }

        return value(currency) + " "
                + amount.setScale(
                        2,
                        RoundingMode.HALF_UP).toPlainString();
    }

    private static String moneyInWords(
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

        if (!"RWF".equalsIgnoreCase(currency)) {
            return numericMoney(normalized, currency);
        }

        BigInteger whole = normalized.toBigInteger();

        int cents = normalized.remainder(BigDecimal.ONE)
                .movePointRight(2)
                .intValue();

        String words = numberWords(whole)
                + " z’amafaranga y’u Rwanda";

        if (cents > 0) {
            words += " n’amasente "
                    + numberWords(BigInteger.valueOf(cents));
        }

        return words + " (RWF "
                + normalized.toPlainString()
                + ")";
    }

    private static String numberWords(
            BigInteger number) {
        if (number.signum() < 0) {
            throw new IllegalArgumentException(
                    "Negative number.");
        }

        if (number.signum() == 0) {
            return "zeru";
        }

        if (number.compareTo(
                new BigInteger("999999999999999")) > 0) {
            throw new IllegalArgumentException(
                    "Amount exceeds supported range.");
        }

        String[] scales = {
                "",
                "ibihumbi",
                "miliyoni",
                "miliyari",
                "tiriyoni"
        };

        List<String> groups = new ArrayList<>();

        BigInteger thousand = BigInteger.valueOf(1000);

        int scale = 0;

        while (number.signum() > 0) {
            int group = number.mod(thousand).intValue();

            if (group != 0) {
                String words;

                if (scale == 0) {
                    words = underThousand(group);

                } else if (group == 1) {
                    if (scale == 1) {
                        words = "igihumbi kimwe";
                    } else {
                        words = scales[scale] + " imwe";
                    }

                } else if (scale == 1 && group == 2) {
                    words = "ibihumbi bibiri";

                } else if (scale >= 2 && group == 2) {
                    words = scales[scale] + " ebyiri";

                } else {
                    words = scales[scale]
                            + " " + underThousand(group);
                }

                groups.add(0, words);
            }

            number = number.divide(thousand);
            scale++;
        }

        return String.join(" na ", groups);
    }

    private static String underThousand(int number) {
        if (number < 0 || number > 999) {
            throw new IllegalArgumentException(
                    "Expected number between 0 and 999.");
        }

        if (number == 0) {
            return "zeru";
        }

        String[] units = {
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

        if (number >= 100) {
            parts.add(hundreds[number / 100]);
        }

        int remainder = number % 100;

        if (remainder >= 20) {
            String text = tens[remainder / 10];

            if (remainder % 10 > 0) {
                text += " na " + units[remainder % 10];
            }

            parts.add(text);

        } else if (remainder >= 10) {
            parts.add(
                    remainder == 10
                            ? "icumi"
                            : "cumi na "
                                    + units[remainder - 10]);

        } else if (remainder > 0) {
            parts.add(units[remainder]);
        }

        return String.join(" na ", parts);
    }

    private static String percent(BigDecimal rate) {
        return rate == null
                ? "[KUZUZA]"
                : rate.stripTrailingZeros()
                        .toPlainString()
                        + "%";
    }

    private static String value(Object value) {
        return value == null
                || value.toString().isBlank()
                        ? "[KUZUZA]"
                        : value.toString().trim();
    }

    private static String optional(Object value) {
        return value == null
                || value.toString().isBlank()
                        ? "Nta makuru yatanzwe"
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
                ? value(borrower.getAddress())
                : joiner.toString();
    }
}

package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Contract is composed entirely from Java source; no DOCX template is loaded.
 */
final class LoanContractTemplateRenderer {
    private LoanContractTemplateRenderer() {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // Source of truth: the company logo extracted from the approved NLS Word
    // contract.
    // Stored as a classpath resource instead of a large Base64 constant.
    private static final String LOGO_RESOURCE = "/branding/nls-logo.png";

    static byte[] render(Loan loan, List<PaymentSchedule> schedules) {
        Borrower b = loan.getBorrower();
        if (b == null)
            throw new IllegalArgumentException("Borrower is required");
        BigDecimal principal = loan.getAmountDecimal();
        if (principal == null || principal.signum() <= 0)
            throw new IllegalArgumentException("Loan principal must be positive.");
        BigDecimal interest = loan.getInterestRateDecimal();
        BigDecimal management = loan.getManagementFeeRateDecimal();
        BigDecimal application = loan.getApplicationFeeRateDecimal();
        if (interest == null || management == null || application == null)
            throw new IllegalStateException(
                    "Loan interest, management fee and application fee rates must be saved before drafting its agreement.");
        boolean annual = "ANNUAL".equalsIgnoreCase(String.valueOf(loan.getInterestRateType()));
        BigDecimal monthlyInterest = principal.multiply(interest)
                .divide(annual ? new BigDecimal("1200") : new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal monthlyManagement = principal.multiply(management).divide(new BigDecimal("100"), 2,
                RoundingMode.HALF_UP);
        // A newly created loan may store applicationFee=0 until disbursement.
        // The contract must show the AGREED one-time 2% fee, not a misleading zero.
        BigDecimal applicationFee = principal.multiply(application)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal assessedFee = loan.getApplicationFeeDecimal();
        if (assessedFee != null && assessedFee.signum() > 0
                && assessedFee.setScale(2, RoundingMode.HALF_UP).compareTo(applicationFee) != 0) {
            throw new IllegalStateException("Application fee assessed on this loan does not match its saved rate. "
                    + "Review the loan fee terms before issuing a contract.");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("BORROWER_NAME", str(b.getFirstName()) + " " + str(b.getLastName()));
        values.put("NATIONAL_ID", str(b.getNationalId()));

        values.put("PHONE", str(b.getPhone()));
        values.put("EMAIL", str(b.getEmail()));
        values.put("SPOUSE_NAME", optional(b.getSpouseFullName()));
        values.put("SPOUSE_ID", optional(b.getSpouseNationalId()));
        values.put("ADDRESS", address(b));
        values.put("PRINCIPAL", money(principal, loan.getCurrency()));
        values.put("PURPOSE", str(loan.getPurpose()));
        values.put("REFERENCE", str(loan.getReferenceNumber()));
        values.put("INTEREST_RATE", pct(interest));
        values.put("INTEREST_PERIOD", annual ? "ku mwaka" : "ku kwezi");
        values.put("MONTHLY_INTEREST", money(monthlyInterest, loan.getCurrency()));
        values.put("APPLICATION_RATE", pct(application));
        values.put("APPLICATION_FEE", money(applicationFee, loan.getCurrency()));
        values.put("MANAGEMENT_RATE", pct(management));
        values.put("MONTHLY_MANAGEMENT", money(monthlyManagement, loan.getCurrency()));
        values.put("PENALTY_RATE", pct(loan.getPenaltyRateDecimal()));
        values.put("DURATION", str(loan.getDurationMonths()));
        values.put("TOTAL_REPAYABLE", money(loan.getTotalRepayableDecimal(), loan.getCurrency()));
        values.put("DATE", LocalDate.now().format(DATE));
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // The original approved PNG is stored on the classpath.
            // This is a genuine repeating Word header, not a first-page picture.
            XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
            XWPFParagraph brand = header.createParagraph();
            brand.setAlignment(ParagraphAlignment.CENTER);
            brand.setSpacingAfter(10);
            try (java.io.InputStream logoStream = LoanContractTemplateRenderer.class
                    .getResourceAsStream(LOGO_RESOURCE)) {
                if (logoStream == null)
                    throw new IllegalStateException("Original NLS logo is not packaged: " + LOGO_RESOURCE);
                brand.createRun().addPicture(logoStream, Document.PICTURE_TYPE_PNG,
                        "nls-original-logo.png", Units.toEMU(165), Units.toEMU(55));
            }
            XWPFParagraph brandName = header.createParagraph();
            brandName.setAlignment(ParagraphAlignment.CENTER);
            brandName.setSpacingAfter(25);
            XWPFRun brandNameRun = brandName.createRun();
            brandNameRun.setText("AMASEZERANO Y’INGUZANYO  •  RWANDA");
            brandNameRun.setBold(true);
            brandNameRun.setFontFamily("Aptos");
            brandNameRun.setFontSize(8);
            brandNameRun.setColor("17324D");
            XWPFParagraph classification = header.createParagraph();
            classification.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun classificationRun = classification.createRun();
            classificationRun.setText("IBANGA  •  CONFIDENTIAL  •  NLS");
            classificationRun.setFontFamily("Aptos");
            classificationRun.setFontSize(8);
            classificationRun.setBold(true);
            classificationRun.setColor("926D25");

            // Allow sufficient vertical space for the repeating logo/header.
            var sect = doc.getDocument().getBody().isSetSectPr()
                    ? doc.getDocument().getBody().getSectPr()
                    : doc.getDocument().getBody().addNewSectPr();
            var margins = sect.isSetPgMar() ? sect.getPgMar() : sect.addNewPgMar();
            margins.setTop(java.math.BigInteger.valueOf(2400));
            margins.setBottom(java.math.BigInteger.valueOf(1050));
            margins.setLeft(java.math.BigInteger.valueOf(1150));
            margins.setRight(java.math.BigInteger.valueOf(1150));
            margins.setHeader(java.math.BigInteger.valueOf(260));
            margins.setFooter(java.math.BigInteger.valueOf(400));

            addIdentityCard(doc, loan);

            add(doc, replace("AMASEZERANO Y’IGURIZA NA NOBLE LOAN SOLUTIONS LTD", values), true);
            add(doc, replace(
                    "NOBLE LOAN SOLUTIONS LTD,yemerewe kugurizanya ifite icyemezo cya  Banki  nkuru y’u Rwanda (BNR)  No 7420/2025- 00211/7000 BNR, ifite icyicaro Gisozi,  ihagarariwe na Madamu Annuarite Tuyisenge, Umuyobozi Mukuru wa NLS yitwa “Noble loan Solutions Ltd   ”muri aya masezerano ku ruhande rumwe,",
                    values), false);
            add(doc, replace(
                    "Bwana/Madamu {{BORROWER_NAME}}, ufite indangamuntu No: {{NATIONAL_ID}}, Telephone: {{PHONE}}, email: {{EMAIL}}. Uwo bashakanye (niba ahari): {{SPOUSE_NAME}}, indangamuntu No: {{SPOUSE_ID}}. Aho atuye: {{ADDRESS}}.",
                    values), false);
            add(doc, replace("Muri aya masezerano akaba yitwa “Ugurijwe”, ku rundi ruhande.", values), false);
            add(doc, replace("HEMERANYIJWE IBI BIKURIKIRA:", values), true);
            add(doc, replace("INGINGO YA 1", values), true);
            add(doc, replace("Ubusobanuro", values), true);
            add(doc, replace(
                    "Muri aya masezerano, aya magambo, asobanuwe mu buryo bukurikira, uretse aho aya masezerano abiteganya ukundi:",
                    values), false);
            add(doc, replace("1.1 “Noble loan solutions Ltd” isobanura “ugurije” Campany yemerewe kuguriza", values),
                    false);
            add(doc, replace("1.2 “Ugurijwe”: ni umuntu cyangwa company bigaragara muri aya masezerano ahabanza",
                    values),
                    false);
            add(doc, replace("1.3 NLS biri mu mpine ariko bisobanura Noble loan solutions Ltd", values), false);
            add(doc, replace("INGINGO YA 2", values), true);
            add(doc, replace(
                    "NLS ihaye inguzanyo ugurijwe, ikurikije amategeko-ngenga yayo, Politiki y’inguzanyo, ndetse n’aya masezerano, inguzanyo y’amafaranga angana na {{PRINCIPAL}} yo gukoresha mu rwego rwa {{PURPOSE}}. Inomero y’inguzanyo: {{REFERENCE}}.",
                    values), false);
            addFinancialOverview(doc, loan, monthlyInterest, monthlyManagement, applicationFee);
            add(doc, replace(
                    "INGINGO YA 3: Inyungu ku nguzanyo , amafaranga yo gukurikirana  inguzanyo n’umufuragiro ku nguzanyo",
                    values), true);
            add(doc, replace(
                    "Iyi nguzanyo ibarirwa ku nyungu fatizo ya {{INTEREST_RATE}} {{INTEREST_PERIOD}}; amafaranga y’inyungu y’ukwezi kwa mbere (ku mushoro w’ibanze): {{MONTHLY_INTEREST}}. Inyungu zibarwa ku mushoro usigaye.",
                    values), false);
            add(doc, replace(
                    "Mu gihe cyo gutanga inguzanyo hishyurwa amafaranga yo kwiga dosiye, umufuragiro w’inguzanyo wa {{APPLICATION_RATE}} ungana na {{APPLICATION_FEE}}, ukatwa ku mafaranga ahabwa ugurijwe igihe cyo gutanga inguzanyo.",
                    values), false);
            add(doc, replace(
                    "Ugurijwe yishyura buri kwezi NLS Ltd amafaranga yo gukurikirana inguzanyo abariwe kuri {{MANAGEMENT_RATE}} ku kwezi ku mushoro usigaye. Ku mushoro w’ibanze ayo mafaranga ni {{MONTHLY_MANAGEMENT}}.",
                    values), false);
            add(doc, replace(
                    "Iyo ugurijwe atishyuye inguzanyo ye mu gihe cyagenwe nkuko biteganywa n’aya masezerano, yishyura igihano cy’ubukererwe ku giciro cya {{PENALTY_RATE}} ku kwezi ku mafaranga yose ari mu bukererwe.",
                    values), false);
            add(doc, replace("INGINGO YA 4", values), true);
            add(doc, replace(
                    "Umwenda wose (ishyingu, inyungu, amafaranga yo gukurikirana inguzanyo) uzishyurwa mu gihe cy’amezi {{DURATION}}. Amafaranga yose ateganyijwe kwishyurwa: {{TOTAL_REPAYABLE}}. Gahunda yo kwishyura ni iyi:",
                    values), false);
            addSchedule(doc, schedules, loan.getCurrency(), loan.getTotalRepayableDecimal());
            add(doc, replace(
                    "NB: Amafaranga yose yishyurwa kuri Konti NIMERO 100236776071 RWF YA NOBLE LOAN SOLUTIONS  LTD iri muri BANK OF KIGALI",
                    values), false);
            add(doc, replace(
                    "Abiherewe uburenganzira na NLS , Ugurijwe ashobora kwishyura umwenda wose cyangwa igice cyawo mbere y’igihe cyateganyijwe mu masezerano  agasigara y’ishyura inyungu zayo azigaje kwishyura ndetse hagakorwa amasezerano mashya",
                    values), false);
            add(doc, replace(
                    "Mu gihe habayeho kugura umwenda bikozwe n’ikigo cy’imali  cyangwa iyindi company ikora imirimo ijya gusa niya NLS  ikora, mbere y’uko itariki yanyuma igera, NLS  yishyurwa inyungu zose zari ziteganijwe.",
                    values), false);
            add(doc, replace("INGINGO YA 5", values), true);
            add(doc, replace(
                    "NLS   ifite uburenganzira bwo gusesa aya masezerano nta nteguza cyangwa kandi igahita yishyuza amafaranga yose ako kanya ibinyujije mu ibaruwa ishinganye cyangwa mu gihe bidashobotse, ibaruwa igashyikirizwa ugurijwe aho atuye iyo bigaragaye ko :",
                    values), false);
            // The original approved agreement includes five explicit default triggers.
            add(doc, "a) Iyo inguzanyo yakoreshejwe mu bintu bitandukanye n'ibyo yasabiwe.", false);
            add(doc, "b) Ubukererwe mu kwishyura burenze iminsi itatu.", false);
            add(doc, "c) Habayeho kubeshya mu byo Ugurijwe yabwiye NLS cyangwa kutubahiriza zimwe mu nshingano Ugurijwe afitanye na NLS.",
                    false);
            add(doc, "d) Ibibazo bituruka ku kwanga kwandikisha ingwate k’Umwanditsi Mukuru, kutajya kwa noteri, gutakaza agaciro kose cyangwa k’igice k’ingwate, cyangwa umwishingizi watanzwe ku mwenda n’ibindi byose yasabwa na NLS.",
                    false);
            add(doc, "e) Guhomba, iburira ry’igihombo, imenyekanisha ry’igihombo n’ikurikirana mu Nkiko bigamije ihungabana ry’umutungo cyangwa ry’Ugurijwe.",
                    false);
            add(doc, replace(
                    "Hashingiwe ku byavuzwe mu gika cyo hejuru, kuba NLS  yaba izi impamvu yagatumye isesa aya masezerano maze igakomeza ikagira igikorwa ikora kirebana no kubahiriza aya masezerano, ntabwo bivuga ko itafata icyemezo cyo kuyasesa nta nteguza nk’uko byavuzwe haruguru.",
                    values), false);
            add(doc, replace("INGINGO YA 6", values), true);
            add(doc, replace(
                    "Uwagurijwe yemeye kubahiriza gahunda yo kwishyura nk’uko yakabaye. Iyo akererewe iminsi itatu birahagije ko aburiwe bwa nyuma kandi yirengera ingaruka zose ziteganywa n’amategeko ndetse n’aya masezerano .",
                    values), false);
            add(doc, replace(
                    "Uretse uburenganzira “UGURIJE”ari we NLS  afite ku ngwate zose Ugurijwe atanze no kuba yagurishwa mucyamunara hakurikijwe amategeko abigenga murwego rwo kugaruza umwenda waba usigaye ndetse n’ibiwukomokaho, NLS  inafite kandi uburenganzira bwo kuregera iyindi mitungo ye cyangwa iyumwishingizi we yaba iyimukanwa ndetse nitimukanwa, amafaranga ye abikije mu bigo by’imari byose, byishingira inguzanyo yose mu kwishyura NLS  mu gihe Ugurijwe azaba yananiwe kwishyura cyangwa ingwate yagurishijwe ntihaboneke amafaranga yo kurangiza umwenda wose waba usigaye, kandi ku ngwate zatanzwe, NLS  iza imbere ku bandi ugurijwe abereyemo imyenda iyo ingwate yatanzwe ku mwanya wa mbere muri  NLS .",
                    values), false);
            add(doc, replace("INGINGO YA 7", values), true);
            add(doc, replace(
                    "Uwagurijwe ahaye NLS  uburenganzira bwo gutanga amakuru arebana n’imyenda ye mu Kigo gishinzwe ibijyanye n’imyenda « Credit Reference Bureau Africa Ltd (CRB Africa) » Iryo menyekanisha ntirishobora gufatwa nko kumena amabanga kuri NLS . Ugurijwe afite uburenganzira bwo kubaza, igihe icyo aricyo cyose, mu Kigo gishinzwe ibijyanye n’imyenda « Credit Reference Bureau Africa Limited » uko umwenda we wamenyekanishijwe na NLS uteye. Igihe Ugurijwe abonye mu imenyekanisha ryakozwe na NLS harabayemo ikosa ahita abimenyesha NLS  hagakorwa ikosora.",
                    values), false);
            add(doc, replace("INGINGO YA 8", values), true);
            add(doc, replace(
                    "Amafaranga yose, n’ibihembo bizakoreshwa mu kubahiriza aya masezerano n’ingaruka zizayakomokaho kimwe n’azagenda ku ngwate ndetse nayo NLS  yakoresha kugira ngo igaruze inguzanyo cyangwa ingwate zatanzwe, yishyurwa n’Ugurijwe kandi akayatanga akiyabazwa nta yandi mananiza.",
                    values), false);
            add(doc, replace(
                    "Ugurijwe agomba kumenyesha NLS , mu nyandiko, igihe bibaye ngombwa ko ahinduye aho yari atuye mu gihe aya masezerano acyubahirijwe. NLS yemerewe kwohereza inyandiko Ugurijwe aho atuye cyangwa kumurongo wa interineti.",
                    values), false);
            add(doc, replace("INGINGO YA 9", values), true);
            add(doc, replace(
                    "Uwagurijwe yemeye ko asobanukiwe akanemera ibikubiye muri aya masezerano ashyiraho  umukono.",
                    values), false);
            add(doc, replace("INGINGO YA 10", values), true);
            add(doc, replace(
                    "Impaka zose zaturuka kuri aya masezerano zizakemurwa n’Inkiko zibifitiye ububasha zo mu Rwanda.",
                    values), false);
            add(doc, replace("Bikorewe Kigali, kuwa {{DATE}}.", values), false);
            addSignatureTable(doc, b);
            XWPFFooter footer = doc.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
            XWPFParagraph footerParagraph = footer.createParagraph();
            footerParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun footerRun = footerParagraph.createRun();
            footerRun.setFontFamily("Aptos");
            footerRun.setFontSize(8);
            footerRun.setColor("687384");
            footerRun.setText("NOBLE LOAN SOLUTIONS LTD  •  CONFIDENTIAL  •  "
                    + str(loan.getReferenceNumber()) + "  •  DRAFT / NOT SIGNED  •  PAGE ");
            footerParagraph.getCTP().addNewFldSimple().setInstr("PAGE");

            doc.getProperties().getCoreProperties()
                    .setTitle("Amasezerano y'iguriza - " + str(loan.getReferenceNumber()));
            doc.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate Noble Loan Solutions contract from Java", ex);
        }
    }

    private static void add(XWPFDocument doc, String text, boolean heading) {
        XWPFParagraph p = doc.createParagraph();
        boolean section = text.startsWith("INGINGO YA ");
        boolean title = text.startsWith("AMASEZERANO Y’IGURIZA");
        p.setSpacingBefore(section ? 245 : (title ? 120 : 0));
        p.setSpacingAfter(title ? 215 : (heading ? 120 : 112));
        p.setAlignment(title ? ParagraphAlignment.CENTER
                : heading ? ParagraphAlignment.LEFT : ParagraphAlignment.BOTH);
        if (heading) {
            var properties = p.getCTP().isSetPPr()
                    ? p.getCTP().getPPr()
                    : p.getCTP().addNewPPr();
            if (!properties.isSetKeepNext())
                properties.addNewKeepNext();
        }
        XWPFRun r = p.createRun();
        r.setFontFamily("Carlito");
        r.setFontSize(title ? 15 : section ? 11 : 10);
        r.setColor(title ? "17324D" : section ? "926D25" : "253346");
        r.setBold(heading);
        String[] parts = text.split("\n", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0)
                r.addBreak();
            r.setText(parts[i]);
        }
    }

    private static void addIdentityCard(XWPFDocument doc, Loan loan) {
        XWPFTable table = doc.createTable(2, 3);
        String[] labels = { "INOMERO Y’INGUZANYO", "ITARIKI", "IMITERERE" };
        String[] values = { str(loan.getReferenceNumber()), LocalDate.now().format(DATE), "DRAFT / NTIRASINYWA" };
        for (int c = 0; c < 3; c++) {
            setCell(table, 0, c, labels[c], true);
            setCell(table, 1, c, values[c], false);
        }
        doc.createParagraph().setSpacingAfter(60);
    }

    private static void addFinancialOverview(XWPFDocument doc, Loan loan, BigDecimal monthlyInterest,
            BigDecimal monthlyManagement, BigDecimal applicationFee) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(110);
        p.setSpacingAfter(75);
        XWPFRun heading = p.createRun();
        heading.setText("INCAMAKE Y’INGUZANYO  /  LOAN SUMMARY");
        heading.setBold(true);
        heading.setFontFamily("Carlito");
        heading.setFontSize(10);
        heading.setColor("926D25");
        XWPFTable table = doc.createTable(2, 3);
        String[] labels = { "UMUSHORO (RWF)", "IGIHE", "AYO KWISHYURA YOSE" };
        String[] values = { shortMoney(loan.getAmountDecimal(), loan.getCurrency()),
                str(loan.getDurationMonths()) + " amezi",
                shortMoney(loan.getTotalRepayableDecimal(), loan.getCurrency()) };
        for (int c = 0; c < 3; c++) {
            setCell(table, 0, c, labels[c], true);
            setCell(table, 1, c, values[c], false);
        }
        doc.createParagraph().setSpacingAfter(75);
    }

    private static void addSignatureTable(XWPFDocument doc, Borrower borrower) {
        add(doc, "IMIKONO Y’ABAGIRANYE AMASEZERANO", true);
        XWPFTable table = doc.createTable(4, 2);
        setCell(table, 0, 0, "UGURIJWE / BORROWER", true);
        setCell(table, 0, 1, "UGURIJE / LENDER", true);
        setCell(table, 1, 0, str(borrower.getFirstName()) + " " + str(borrower.getLastName()), false);
        setCell(table, 1, 1, "TUYISENGE Annuarite — Umuyobozi Mukuru", false);
        setCell(table, 2, 0, "Umukono: _______________________", false);
        setCell(table, 2, 1, "Umukono: _______________________", false);
        setCell(table, 3, 0, "Itariki: _________________________", false);
        setCell(table, 3, 1, "Itariki: _________________________", false);
    }

    private static String replace(String s, Map<String, String> values) {
        for (var entry : values.entrySet())
            s = s.replace("{{" + entry.getKey() + "}}", entry.getValue());
        if (s.contains("{{"))
            throw new IllegalStateException("Unresolved contract variable: " + s);
        return s;
    }

    private static BigDecimal nz(BigDecimal n) {
        return n == null ? BigDecimal.ZERO : n;
    }

    private static String str(Object v) {
        return v == null || v.toString().isBlank() ? "[KUZUZA]" : v.toString().trim();
    }

    private static String pct(BigDecimal n) {
        return n == null ? "[KUZUZA]" : n.stripTrailingZeros().toPlainString() + "%";
    }

    private static String money(BigDecimal n, String currency) {
        if (n == null)
            return "[KUZUZA]";
        if (n.signum() < 0)
            throw new IllegalArgumentException("Negative contract amount: " + n);
        BigDecimal amount = n.setScale(2, RoundingMode.HALF_UP);
        String figure = String.format(Locale.US, "%,.2f", amount);
        // Words are applicable to RWF; other currencies remain numerical.
        if (!"RWF".equalsIgnoreCase(currency))
            return str(currency) + " " + figure;
        if (amount.stripTrailingZeros().scale() > 0)
            return "RWF " + figure + " (including cents)";
        return rwfWords(amount.toBigIntegerExact()) + " (RWF "
                + String.format(Locale.US, "%,.0f", amount) + ")";
    }

    /**
     * Kinyarwanda amounts in words. Review grammatical variants before external
     * use.
     */
    private static String rwfWords(java.math.BigInteger value) {
        if (value.signum() < 0)
            throw new IllegalArgumentException("Negative amount");
        if (value.signum() == 0)
            return "zeru y'amafaranga y'u Rwanda";
        if (value.compareTo(new java.math.BigInteger("999999999999999")) > 0)
            throw new IllegalArgumentException("Amount too large for words");
        java.math.BigInteger thousand = java.math.BigInteger.valueOf(1000);
        String[] scales = { "", "ibihumbi", "miliyoni", "miliyari", "tiriyoni" };
        java.util.List<String> pieces = new java.util.ArrayList<>();
        int scale = 0;
        while (value.signum() > 0) {
            int group = value.mod(thousand).intValue();
            if (group > 0) {
                String part;
                if (scale == 0)
                    part = underThousand(group);
                else if (group == 1)
                    part = scale == 1 ? "igihumbi kimwe" : scales[scale] + " imwe";
                else if (scale == 1 && group == 2)
                    part = "ibihumbi bibiri";
                else
                    part = scales[scale] + " " + underThousand(group);
                pieces.add(0, part);
            }
            value = value.divide(thousand);
            scale++;
        }
        return String.join(" na ", pieces) + " z'amafaranga y'u Rwanda";
    }

    private static String underThousand(int n) {
        String[] units = { "", "rimwe", "abiri", "atatu", "ane", "atanu", "atandatu", "arindwi", "umunani", "icyenda" };
        String[] tens = { "", "icumi", "makumyabiri", "mirongo itatu", "mirongo ine", "mirongo itanu",
                "mirongo itandatu", "mirongo irindwi", "mirongo inani", "mirongo icyenda" };
        String[] hundreds = { "", "ijana", "magana abiri", "magana atatu", "magana ane", "magana atanu",
                "magana atandatu", "magana arindwi", "magana inani", "magana icyenda" };
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (n >= 100)
            parts.add(hundreds[n / 100]);
        int rem = n % 100;
        if (rem >= 20) {
            parts.add(tens[rem / 10]);
            if (rem % 10 > 0)
                parts.add(units[rem % 10]);
        } else if (rem >= 10)
            parts.add(rem == 10 ? "icumi" : "cumi na " + units[rem - 10]);
        else if (rem > 0)
            parts.add(units[rem]);
        return String.join(" na ", parts);
    }

    /** Repayment breakdown from the database, never a fabricated schedule. */
    /** Schedule values are always read from saved PaymentSchedule rows. */
    private static void addSchedule(XWPFDocument doc, List<PaymentSchedule> schedules,
            String currency, BigDecimal totalRepayable) {
        if (schedules == null || schedules.isEmpty()) {
            add(doc, "[GAHUNDA YO KWISHYURA NTIRABONEKA — NTIMWOHEREZE AMASEZERANO]", false);
            return;
        }
        XWPFTable table = doc.createTable(schedules.size() + 2, 4);
        String[] headers = { "IGICE", "ITARIKI", "UMUSHORO", "YOSE HAMWE" };
        for (int c = 0; c < headers.length; c++)
            setCell(table, 0, c, headers[c], true);
        table.getRow(0).getCtRow().addNewTrPr().addNewTblHeader();
        BigDecimal sum = BigDecimal.ZERO;
        boolean incomplete = false;
        for (int i = 0; i < schedules.size(); i++) {
            PaymentSchedule item = schedules.get(i);
            int row = i + 1;
            setCell(table, row, 0, str(item.getInstallmentNumber()), false);
            setCell(table, row, 1,
                    item.getDueDate() == null ? "[ITARIKI IBURA]" : item.getDueDate().format(DATE), false);
            setCell(table, row, 2, shortMoney(item.getPrincipalAmount(), currency), false);
            setCell(table, row, 3, shortMoney(item.getInstallmentAmount(), currency), false);
            if (item.getDueDate() == null || item.getInstallmentAmount() == null
                    || item.getInstallmentAmount().signum() <= 0 || item.getPrincipalAmount() == null)
                incomplete = true;
            if (item.getInstallmentAmount() != null)
                sum = sum.add(item.getInstallmentAmount());
        }
        int last = schedules.size() + 1;
        setCell(table, last, 0, "IGITERANYO", true);
        setCell(table, last, 1, "", true);
        setCell(table, last, 2, "", true);
        setCell(table, last, 3, shortMoney(sum, currency), true);
        if (totalRepayable == null || sum.setScale(2, RoundingMode.HALF_UP)
                .compareTo(totalRepayable.setScale(2, RoundingMode.HALF_UP)) != 0)
            incomplete = true;
        if (incomplete)
            add(doc, "[KUZUZA] Gahunda yo kwishyura ntihuye n’amafaranga yose ateganyijwe cyangwa ifite amakuru atuzuye. Ntimuwohereze.",
                    false);
        doc.createParagraph();
    }

    private static String shortMoney(BigDecimal amount, String currency) {
        return amount == null ? "[KUZUZA]"
                : str(currency) + " "
                        + String.format(Locale.US, "%,.2f", amount);
    }

    private static void setCell(XWPFTable table, int row, int col, String text, boolean header) {
        XWPFTableCell cell = table.getRow(row).getCell(col);
        if (header)
            cell.setColor("F3EBD7");
        XWPFRun r = cell.getParagraphs().get(0).createRun();
        r.setText(text);
        r.setFontFamily("Carlito");
        r.setFontSize(header ? 8 : 9);
        r.setBold(header);
        r.setColor(header ? "17324D" : "253346");
    }

    private static String optional(Object v) {
        return v == null || v.toString().isBlank() ? "ntabwo byatanzwe" : v.toString().trim();
    }

    private static String address(Borrower b) {
        StringJoiner sj = new StringJoiner(", ");
        Object[] parts = { b.getPhysicalAddressVillage(), b.getPhysicalAddressCell(), b.getPhysicalAddressSector(),
                b.getPhysicalAddressDistrict(), b.getCity(), b.getCountry() };
        for (Object p : parts)
            if (p != null && !p.toString().isBlank())
                sj.add(p.toString().trim());
        return sj.length() == 0 ? str(b.getAddress()) : sj.toString();
    }
}

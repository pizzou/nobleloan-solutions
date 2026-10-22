package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
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
        BigDecimal principal = nz(loan.getAmountDecimal());
        BigDecimal interest = nz(loan.getInterestRateDecimal());
        BigDecimal management = nz(loan.getManagementFeeRateDecimal());
        BigDecimal application = nz(loan.getApplicationFeeRateDecimal());
        boolean annual = "ANNUAL".equalsIgnoreCase(String.valueOf(loan.getInterestRateType()));
        BigDecimal monthlyInterest = principal.multiply(interest)
                .divide(annual ? new BigDecimal("1200") : new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal monthlyManagement = principal.multiply(management).divide(new BigDecimal("100"), 2,
                RoundingMode.HALF_UP);
        BigDecimal applicationFee = loan.getApplicationFeeDecimal() == null
                ? principal.multiply(application).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : loan.getApplicationFeeDecimal();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("BORROWER_NAME", str(b.getFirstName()) + " " + str(b.getLastName()));
        values.put("NATIONAL_ID", str(b.getNationalId()));
        values.put("ID_ISSUED_AT", "[AHO INDANGAMUNTU YATANGIWE: KUZUZA]");
        values.put("PHONE", str(b.getPhone()));
        values.put("EMAIL", str(b.getEmail()));
        values.put("SPOUSE_NAME", str(b.getSpouseFullName()));
        values.put("SPOUSE_ID", str(b.getSpouseNationalId()));
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
        StringBuilder schedule = new StringBuilder();
        if (schedules == null || schedules.isEmpty())
            schedule.append("[GAHUNDA YO KWISHYURA NTIRABONEKA — NTIMWOHEREZE AMASEZERANO]");
        else
            for (PaymentSchedule p : schedules) {
                if (schedule.length() > 0)
                    schedule.append("\n");
                schedule.append("Taliki ")
                        .append(p.getDueDate() == null ? "[ITARIKI IBURA]" : p.getDueDate().format(DATE))
                        .append(": igice ").append(str(p.getInstallmentNumber())).append(" — ")
                        .append(money(p.getInstallmentAmount(), loan.getCurrency()));
            }
        values.put("SCHEDULE", schedule.toString());
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // True repeating Word header: the approved company logo appears on every page.
            XWPFHeader header = doc.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
            XWPFParagraph brand = header.createParagraph();
            brand.setAlignment(ParagraphAlignment.CENTER);
            brand.setSpacingAfter(70);
            try (java.io.InputStream logoStream = LoanContractTemplateRenderer.class
                    .getResourceAsStream(LOGO_RESOURCE)) {
                if (logoStream == null) {
                    throw new IllegalStateException("Missing contract branding resource: " + LOGO_RESOURCE);
                }
                XWPFRun logoRun = brand.createRun();
                logoRun.addPicture(logoStream, Document.PICTURE_TYPE_PNG,
                        "nls-logo.png", Units.toEMU(175), Units.toEMU(59));
            }
            XWPFParagraph classification = header.createParagraph();
            classification.setAlignment(ParagraphAlignment.RIGHT);
            XWPFRun classificationRun = classification.createRun();
            classificationRun.setText("IBANGA • CONFIDENTIAL | LOAN AGREEMENT");
            classificationRun.setFontFamily("Aptos");
            classificationRun.setFontSize(8);
            classificationRun.setBold(true);
            classificationRun.setColor("926D25");

            XWPFParagraph meta = doc.createParagraph();
            meta.setAlignment(ParagraphAlignment.CENTER);
            meta.setSpacingAfter(220);
            XWPFRun metaRun = meta.createRun();
            metaRun.setFontFamily("Aptos");
            metaRun.setFontSize(9);
            metaRun.setColor("536173");
            metaRun.setText("REFERENCE: " + str(loan.getReferenceNumber())
                    + "    |    DATE: " + LocalDate.now().format(DATE)
                    + "    |    STATUS: DRAFT — NOT SIGNED");

            add(doc, replace("AMASEZERANO Y’IGURIZA NA NOBLE LOAN SOLUTIONS LTD", values), true);
            add(doc, replace(
                    "NOBLE LOAN SOLUTIONS LTD,yemerewe kugurizanya ifite icyemezo cya  Banki  nkuru y’u Rwanda (BNR)  No 7420/2025- 00211/7000 BNR, ifite icyicaro Gisozi,  ihagarariwe na Madamu Annuarite Tuyisenge, Umuyobozi Mukuru wa NLS yitwa “Noble loan Solutions Ltd   ”muri aya masezerano ku ruhande rumwe,",
                    values), false);
            add(doc, replace(
                    "Bwana {{BORROWER_NAME}}, ufite indangamuntu No: {{NATIONAL_ID}}, yatangiwe {{ID_ISSUED_AT}}, Telephone: {{PHONE}}, email: {{EMAIL}}. Uwo bashakanye: {{SPOUSE_NAME}}, indangamuntu No: {{SPOUSE_ID}}. Aho atuye: {{ADDRESS}}.",
                    values), false);
            add(doc, replace("Muri aya masezerano akaba yitwa “Ugurijwe”, ku rundi ruhande.", values), false);
            add(doc, replace("HEMERANYIJWE IBI BIKURIKIRA:", values), true);
            add(doc, replace("INGINGO YA 1", values), true);
            add(doc, replace("Ubusobanuro", values), true);
            add(doc, replace(
                    "Muri aya masezerano, aya magambo, asobanuwe mu buryo bukurikira, uretse aho aya masezerano abiteganya ukundi:",
                    values), false);
            add(doc, replace("“Noble loan solutions Ltd” isobanura  “ugurije” Campany yemerewe kuguriza", values),
                    false);
            add(doc, replace("“Ugurijwe”: ni umuntu cyangwa company bigaragara muri aya masezerano ahabanza", values),
                    false);
            add(doc, replace("NLS  biri mu mpine ariko bisobanura Noble loan solutions Ltd", values), false);
            add(doc, replace("INGINGO YA 2", values), true);
            add(doc, replace(
                    "NLS ihaye inguzanyo ugurijwe, ikurikije amategeko-ngenga yayo, Politiki y’inguzanyo, ndetse n’aya masezerano, inguzanyo y’amafaranga angana na {{PRINCIPAL}} yo gukoresha mu rwego rwa {{PURPOSE}}. Inomero y’inguzanyo: {{REFERENCE}}.",
                    values), false);
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
            add(doc, replace("{{SCHEDULE}}", values), false);
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
            add(doc, replace(
                    "UGURIJWE	                                                                                  UGURIJE",
                    values), true);
            add(doc, replace("_________________________                         TUYISENGE Annuarite", values), false);
            add(doc, replace("Umuyobozi Mukuru", values), false);
            add(doc, replace("Noble Loan Solutions Ltd", values), false);
            XWPFFooter footer = doc.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
            XWPFParagraph footerParagraph = footer.createParagraph();
            footerParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun footerRun = footerParagraph.createRun();
            footerRun.setFontFamily("Aptos");
            footerRun.setFontSize(8);
            footerRun.setColor("687384");
            footerRun.setText("NOBLE LOAN SOLUTIONS LTD  •  CONFIDENTIAL  •  "
                    + str(loan.getReferenceNumber()) + "  •  DRAFT / NOT SIGNED");

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
        p.setSpacingBefore(section ? 220 : (title ? 140 : 0));
        p.setSpacingAfter(title ? 210 : (heading ? 130 : 105));
        p.setAlignment(title ? ParagraphAlignment.CENTER
                : heading ? ParagraphAlignment.LEFT : ParagraphAlignment.BOTH);
        if (heading) {
            // Apache POI XWPFParagraph has no setKeepWithNext(boolean) method.
            // WordprocessingML keepNext prevents a heading from being orphaned.
            var paragraphProperties = p.getCTP().isSetPPr()
                    ? p.getCTP().getPPr()
                    : p.getCTP().addNewPPr();
            if (!paragraphProperties.isSetKeepNext()) {
                paragraphProperties.addNewKeepNext();
            }
        }
        XWPFRun r = p.createRun();
        r.setFontFamily("Aptos");
        r.setFontSize(title ? 15 : section ? 11 : 10);
        r.setColor(title || section ? "17324D" : "253346");
        r.setBold(heading);
        if (title)
            r.setText(" ");
        String[] parts = text.split("\n", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0)
                r.addBreak();
            r.setText(parts[i]);
        }
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

    private static String money(BigDecimal n, String c) {
        if (n == null)
            return "[KUZUZA]";
        BigDecimal amount = n.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() < 0)
            throw new IllegalArgumentException("Negative contract amount: " + amount);
        if (amount.stripTrailingZeros().scale() > 0) {
            return amount.toPlainString() + " (" + str(c) + " " + amount.toPlainString() + ")"; // fractions must not be
                                                                                                // misrepresented as
                                                                                                // whole francs
        }
        return rwfWords(amount.toBigIntegerExact()) + " (" + str(c) + " "
                + amount.toBigIntegerExact().toString() + ")";
    }

    private static String rwfWords(java.math.BigInteger value) {
        if (value.signum() == 0)
            return "amafaranga y’u Rwanda zeru";
        if (value.compareTo(new java.math.BigInteger("999999999999999")) > 0)
            throw new IllegalArgumentException("Amount too large for words");
        String[] units = { "", "rimwe", "abiri", "atatu", "ane", "atanu", "atandatu", "arindwi", "umunani", "icyenda" };
        String[] tens = { "", "icumi", "makumyabiri", "mirongo itatu", "mirongo ine", "mirongo itanu",
                "mirongo itandatu", "mirongo irindwi", "mirongo inani", "mirongo icyenda" };
        String[] hundreds = { "", "ijana", "magana abiri", "magana atatu", "magana ane", "magana atanu",
                "magana atandatu", "magana arindwi", "magana inani", "magana icyenda" };
        String[] scales = { "", "ibihumbi", "miliyoni", "miliyari", "tiriyoni" };
        java.util.List<String> groups = new java.util.ArrayList<>();
        int group = 0;
        while (value.signum() > 0) {
            int n = value.mod(java.math.BigInteger.valueOf(1000)).intValue();
            if (n > 0) {
                java.util.List<String> parts = new java.util.ArrayList<>();
                if (n >= 100)
                    parts.add(hundreds[n / 100]);
                int rem = n % 100;
                if (rem >= 20) {
                    parts.add(tens[rem / 10]);
                    if (rem % 10 > 0)
                        parts.add(units[rem % 10]);
                } else if (rem >= 10) {
                    parts.add(rem == 10 ? "icumi" : "cumi na " + units[rem - 10]);
                } else if (rem > 0)
                    parts.add(units[rem]);
                groups.add(0, (group > 0 ? scales[group] + " " : "") + String.join(" ", parts));
            }
            value = value.divide(java.math.BigInteger.valueOf(1000));
            group++;
        }
        return "amafaranga y’u Rwanda " + String.join(" ", groups);
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

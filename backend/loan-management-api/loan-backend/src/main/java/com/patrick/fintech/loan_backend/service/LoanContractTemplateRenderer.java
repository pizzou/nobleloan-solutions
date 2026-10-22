package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Generates the Noble Loan Solutions contract directly from Java and the saved
 * loan terms. The approved Word file is a visual/legal reference only; it is
 * deliberately not loaded or embedded as a DOCX template at runtime.
 */
final class LoanContractTemplateRenderer {
    private LoanContractTemplateRenderer() {
    }

    /**
     * Bump this value whenever the rendered layout, header/footer, logo placement,
     * or clause structure changes. It is stored in the DOCX description and
     * included
     * in the draft fingerprint so an old unsent draft cannot hide a renderer fix.
     */
    static final String DOCUMENT_SCHEMA = "NLS-CONTRACT-RENDERER-2026-10-22-V2";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String LOGO_RESOURCE = "/branding/nls-logo.png";
    private static final String NAVY = "17324D";
    private static final String GOLD = "B98B2E";
    private static final String TEXT = "253346";
    private static final String MUTED = "657386";

    static byte[] render(Loan loan, List<PaymentSchedule> schedules) {
        if (loan == null) {
            throw new IllegalArgumentException("Loan is required to generate a contract.");
        }
        Borrower borrower = loan.getBorrower();
        if (borrower == null) {
            throw new IllegalArgumentException("Borrower is required to generate a contract.");
        }
        BigDecimal principal = loan.getAmountDecimal();
        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException("A positive approved loan amount is required to generate a contract.");
        }

        String currency = currency(loan.getCurrency());
        BigDecimal interestRate = loan.getInterestRateDecimal();
        BigDecimal managementRate = loan.getManagementFeeRateDecimal();
        BigDecimal applicationRate = loan.getApplicationFeeRateDecimal();
        boolean annualInterest = "ANNUAL".equalsIgnoreCase(String.valueOf(loan.getInterestRateType()));
        BigDecimal monthlyInterest = interestRate == null ? null
                : principal.multiply(interestRate)
                        .divide(annualInterest ? new BigDecimal("1200") : new BigDecimal("100"), 2,
                                RoundingMode.HALF_UP);
        BigDecimal monthlyManagement = managementRate == null ? null
                : principal.multiply(managementRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        // Use the assessed/snapshotted application fee when present. A null legacy
        // value may be calculated from its stored rate; never silently replace an
        // explicitly saved zero (e.g. an approved waiver) with a new charge.
        BigDecimal applicationFee = loan.getApplicationFee();
        if (applicationFee == null) {
            applicationFee = principal.multiply(applicationRate == null ? BigDecimal.ZERO : applicationRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        BigDecimal totalRepayable = loan.getTotalRepayableDecimal();
        if (totalRepayable == null && schedules != null && !schedules.isEmpty()) {
            totalRepayable = schedules.stream()
                    .map(PaymentSchedule::getInstallmentAmount)
                    .filter(value -> value != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("BORROWER_NAME", fullName(borrower));
        values.put("NATIONAL_ID", display(borrower.getNationalId(), "Ntibyatanzwe"));
        values.put("ID_ISSUED_AT", "Ntibiboneka muri dosiye y'usaba inguzanyo");
        values.put("PHONE", display(borrower.getPhone(), "Ntibyatanzwe"));
        values.put("EMAIL", display(borrower.getEmail(), "Ntibyatanzwe"));
        values.put("SPOUSE_NAME", display(borrower.getSpouseFullName(), "Ntibyatanzwe"));
        values.put("SPOUSE_ID", display(borrower.getSpouseNationalId(), "Ntibyatanzwe"));
        values.put("ADDRESS", address(borrower));
        values.put("PRINCIPAL", money(principal, currency));
        values.put("PURPOSE", display(loan.getPurpose(), "Ntibyasobanuwe"));
        values.put("REFERENCE", display(loan.getReferenceNumber(), "Ntibyatanzwe"));
        values.put("INTEREST_RATE", pct(interestRate));
        values.put("INTEREST_PERIOD", annualInterest ? "ku mwaka" : "ku kwezi");
        values.put("MONTHLY_INTEREST", money(monthlyInterest, currency));
        values.put("APPLICATION_RATE", pct(applicationRate));
        values.put("APPLICATION_FEE", money(applicationFee, currency));
        values.put("MANAGEMENT_RATE", pct(managementRate));
        values.put("MONTHLY_MANAGEMENT", money(monthlyManagement, currency));
        values.put("PENALTY_RATE", pct(loan.getPenaltyRateDecimal()));
        values.put("DURATION", loan.getDurationMonths() == null ? "Ntibyatanzwe" : loan.getDurationMonths().toString());
        values.put("TOTAL_REPAYABLE", money(totalRepayable, currency));
        values.put("DATE", LocalDate.now().format(DATE));
        values.put("SCHEDULE_WARNING", "GAHUNDA YO KWISHYURA NTIRABONEKA MURI SISITEMU — NTIMWOHEREZE AMASEZERANO.");

        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            configurePage(doc);
            renderHeader(doc);
            renderFooter(doc, borrower, loan);
            doc.getProperties().getCoreProperties().setTitle("Amasezerano y'iguriza - " + values.get("REFERENCE"));
            doc.getProperties().getCoreProperties().setDescription(
                    DOCUMENT_SCHEMA
                            + " | Dynamically generated Noble Loan Solutions loan contract draft; review and signature required.");

            addTitle(doc, "AMASEZERANO Y’IGURIZA NA NOBLE LOAN SOLUTIONS LTD");
            addMetadata(doc, values.get("REFERENCE"), values.get("DATE"));

            addBody(doc,
                    "NOBLE LOAN SOLUTIONS LTD yemerewe gukora ibikorwa byo kuguriza, ifite uruhushya rwa Banki Nkuru y’u Rwanda (BNR) No. 7420/2025-00211/7000, ifite icyicaro i Gisozi, ihagarariwe na Madamu Annuarite Tuyisenge, Umuyobozi Mukuru; muri aya masezerano yitwa “NLS” cyangwa “Ugurije”, ku ruhande rumwe.");
            addBody(doc, "Ugurijwe ni " + values.get("BORROWER_NAME") + ", ufite indangamuntu No. "
                    + values.get("NATIONAL_ID") + ", yatangiwe " + values.get("ID_ISSUED_AT")
                    + "; telefoni: " + values.get("PHONE") + "; email: " + values.get("EMAIL")
                    + ". Uwo bashakanye: " + values.get("SPOUSE_NAME") + ", indangamuntu No. "
                    + values.get("SPOUSE_ID") + ". Aho atuye: " + values.get("ADDRESS")
                    + ". Muri aya masezerano yitwa “Ugurijwe”, ku rundi ruhande.");
            addLead(doc, "HEMERANYIJWE IBI BIKURIKIRA:");

            addClauseHeading(doc, "INGINGO YA 1 — UBUSOBANURO");
            addBody(doc,
                    "Muri aya masezerano, amagambo akurikira asobanurwa uku, uretse aho aya masezerano abiteganya ukundi:");
            addNumbered(doc, "1.1", "“Noble Loan Solutions Ltd” cyangwa “NLS” ni ikigo cyemerewe gutanga inguzanyo.");
            addNumbered(doc, "1.2", "“Ugurijwe” ni umuntu cyangwa ikigo bigaragazwa mu ntangiriro z’aya masezerano.");
            addNumbered(doc, "1.3", "“Inguzanyo” ni amafaranga NLS yemeye guha Ugurijwe hakurikijwe aya masezerano.");

            addClauseHeading(doc, "INGINGO YA 2 — INGUZANYO N’ICYO IZAKORESHWA");
            addBody(doc,
                    "NLS iha Ugurijwe inguzanyo, ikurikije amategeko-ngenga yayo, politiki y’inguzanyo n’aya masezerano, ingana na "
                            + values.get("PRINCIPAL") + ". Inguzanyo izakoreshwa ku mpamvu zikurikira: "
                            + values.get("PURPOSE")
                            + ". Inomero y’inguzanyo: " + values.get("REFERENCE") + ".");

            addClauseHeading(doc, "INGINGO YA 3 — INYUNGU, APPLICATION FEE, N’AMAFARANGA YO GUKURIKIRANA INGUZANYO");
            addBullet(doc, "Inyungu ku nguzanyo",
                    "Iyi nguzanyo ibarirwa inyungu fatizo ya " + values.get("INTEREST_RATE")
                            + " " + values.get("INTEREST_PERIOD")
                            + ". Inyungu z’ukwezi kwa mbere, zibarwa ku mushoro w’ibanze, zingana na "
                            + values.get("MONTHLY_INTEREST")
                            + ". Inyungu zibarwa ku mushoro usigaye hakurikijwe ibisabwa n’amasezerano.");
            addBullet(doc, "Application Fee", "Igihe cyo gutanga inguzanyo, hishyurwa Application Fee ingana na "
                    + values.get("APPLICATION_RATE") + " y’inguzanyo, ingana na " + values.get("APPLICATION_FEE")
                    + ". Yishyurwa kuri konti ya NLS cyangwa igakatwa ku mafaranga ahabwa Ugurijwe igihe cyo gutanga inguzanyo, nk’uko byemejwe mu nguzanyo.");
            addBullet(doc, "Amafaranga yo gukurikirana inguzanyo",
                    "Ugurijwe yishyura buri kwezi NLS Ltd amafaranga yo gukurikirana inguzanyo angana na "
                            + values.get("MANAGEMENT_RATE")
                            + " ku kwezi ku mushoro w’inguzanyo usigaye kwishyurwa. Ku kwezi kwa mbere, amafaranga abariwe ku mushoro w’ibanze angana na "
                            + values.get("MONTHLY_MANAGEMENT") + ".");
            addBullet(doc, "Igihano cy’ubukererwe", "Iyo Ugurijwe atishyuye ku gihe, igihano cy’ubukererwe kiba "
                    + values.get("PENALTY_RATE")
                    + " ku kwezi ku mafaranga ari mu bukererwe, nyuma y’iminsi itatu (3) y’imbabazi, kandi kikabarwa hakurikijwe politiki y’imari ikoreshwa kuri iyi nguzanyo.");

            addClauseHeading(doc, "INGINGO YA 4 — IGIHE N’UBURYO BWO KWISHYURA");
            addBody(doc,
                    "Umwenda wose, ugizwe n’umushoro, inyungu n’amafaranga yo gukurikirana inguzanyo, uzishyurwa mu gihe cy’amezi "
                            + values.get("DURATION") + ". Amafaranga yose ateganyijwe kwishyurwa ni "
                            + values.get("TOTAL_REPAYABLE")
                            + ". Gahunda yo kwishyura igaragazwa mu mbonerahamwe ikurikira:");
            renderSchedule(doc, schedules, currency, values.get("SCHEDULE_WARNING"));
            addLead(doc,
                    "NB: Amafaranga yose yishyurwa kuri konti No. 100236776071 RWF ya NOBLE LOAN SOLUTIONS LTD iri muri BANK OF KIGALI.");
            addBody(doc,
                    "Abiherewe uburenganzira na NLS, Ugurijwe ashobora kwishyura umwenda wose cyangwa igice cyawo mbere y’igihe cyateganyijwe. Igihe habayeho kwishyura mbere y’igihe, hazabarwa inyungu n’amafaranga asigaye kwishyurwa hakurikijwe aya masezerano, kandi hagakorwa amasezerano mashya igihe bibaye ngombwa.");
            addBody(doc,
                    "Mu gihe ikigo cy’imari cyangwa indi sosiyete ikora imirimo ijya gusa n’iya NLS iguze cyangwa yakiriye umwenda mbere y’uko itariki ya nyuma igera, NLS yishyurwa inyungu zose zari ziteganyijwe muri aya masezerano.");

            addClauseHeading(doc, "INGINGO YA 5 — GUSESWA KW’AMASEZERANO");
            addBody(doc,
                    "NLS ifite uburenganzira bwo gusesa aya masezerano nta nteguza no guhita isaba kwishyurwa amafaranga yose ako kanya, ibinyujije mu ibaruwa ishinganye cyangwa, iyo bidashobotse, ibaruwa igashyikirizwa Ugurijwe aho atuye, iyo hagaragaye kimwe muri ibi bikurikira:");
            addLetter(doc, "a", "Inguzanyo yakoreshejwe mu bintu bitandukanye n’ibyo yasabiwe.");
            addLetter(doc, "b", "Ubukererwe mu kwishyura burenze iminsi itatu (3).");
            addLetter(doc, "c",
                    "Habayeho kubeshya mu makuru Ugurijwe yahaye NLS cyangwa kutubahiriza zimwe mu nshingano afitanye na NLS.");
            addLetter(doc, "d",
                    "Habayeho kwanga kwandikisha ingwate ku Mwanditsi Mukuru, kutajya kwa noteri, gutakaza agaciro k’ingwate kose cyangwa igice cyayo, cyangwa ikibazo ku mwishingizi cyangwa ku bindi NLS yasabye.");
            addLetter(doc, "e",
                    "Habayeho guhomba, iburira ry’igihombo, imenyekanisha ry’igihombo cyangwa ikurikiranwa mu nkiko rigamije umutungo cyangwa Ugurijwe.");
            addBody(doc,
                    "Kuba NLS yaba izi impamvu zishobora gutuma isesa aya masezerano ariko igakomeza gukora igikorwa kijyanye no kuyubahiriza ntibivuga ko yaretse uburenganzira bwo kuyasesa nta nteguza nk’uko biteganyijwe haruguru.");

            addClauseHeading(doc, "INGINGO YA 6 — KUTUBAHIRIZA GAHUNDA NO KUGARUZWA K’UMWENDA");
            addBody(doc,
                    "Ugurijwe yemeye kubahiriza gahunda yo kwishyura nk’uko yateganyijwe. Iyo akererewe iminsi itatu (3), NLS ishobora kumuburira bwa nyuma, kandi Ugurijwe yirengera ingaruka ziteganywa n’amategeko n’aya masezerano.");
            addBody(doc,
                    "NLS ifite uburenganzira bwo gukurikirana ingwate zose zatanzwe no kuzishyurisha muri cyamunara hakurikijwe amategeko kugira ngo igaruze umwenda usigaye n’ibiwukomokaho. Iyo ingwate yagurishijwe itabonetsemo amafaranga ahagije yo kurangiza umwenda, NLS ishobora kwishyuza indi mitungo y’Ugurijwe cyangwa iy’umwishingizi we, yaba iyimukanwa cyangwa itimukanwa, n’amafaranga abikijwe mu bigo by’imari, hakurikijwe amategeko n’amasezerano y’ubwishingizi. Iyo ingwate yatanzwe ku mwanya wa mbere muri NLS, NLS iza imbere y’abandi bagurijwe ku bijyanye n’iyo ngwate.");

            addClauseHeading(doc, "INGINGO YA 7 — AMAKURU YA CREDIT REFERENCE BUREAU");
            addBody(doc,
                    "Ugurijwe yemereye NLS gutanga amakuru yerekeye umwenda we kuri Credit Reference Bureau Africa Ltd (CRB Africa), hakurikijwe amategeko n’amabwiriza abigenga. Ugurijwe ashobora kubaza CRB Africa uko umwenda we watangajwe. Iyo abonye ikosa mu makuru yatanzwe, agomba kubimenyesha NLS kugira ngo risuzumwe kandi rikosorwe igihe bibaye ngombwa.");

            addClauseHeading(doc, "INGINGO YA 8 — AMAFARANGA AKOMOKA KU GUSHYIRA MU BIKORWA AMASEZERANO");
            addBody(doc,
                    "Amafaranga yose n’ibihembo bizakenerwa mu kubahiriza aya masezerano n’ingaruka zizayakomokaho, harimo amafaranga ajyanye n’ingwate n’ayo NLS yakoresha kugira ngo igaruze inguzanyo cyangwa ingwate zatanzwe, bizishyurwa n’Ugurijwe kandi abazwe ayo mafaranga hakurikijwe amategeko n’aya masezerano.");
            addBody(doc,
                    "Ugurijwe agomba kumenyesha NLS mu nyandiko igihe ahinduye aho atuye mu gihe aya masezerano agikurikizwa. NLS ishobora kohereza inyandiko Ugurijwe aho atuye cyangwa kuri aderesi ya interineti yatanze.");

            addClauseHeading(doc, "INGINGO YA 9 — KWEMERA AMASEZERANO N’IMIKONO");
            addBody(doc,
                    "Ugurijwe yemeza ko yasomye, yasobanukiwe kandi yemeye ibikubiye muri aya masezerano, abigaragaza ashyiraho umukono. Aya masezerano agomba gusuzumwa no gusinywa n’impande zombi mbere y’uko afatwa nk’amasezerano yashyizwe mu bikorwa.");

            addClauseHeading(doc, "INGINGO YA 10 — GUKEMURA IMPAKA");
            addBody(doc,
                    "Impaka zose zituruka kuri aya masezerano zizakemurwa n’inkiko zibifitiye ububasha zo mu Rwanda.");
            addBody(doc, "Bikorewe i Kigali, kuwa " + values.get("DATE") + ".");
            renderSignatureBlock(doc, values.get("BORROWER_NAME"));

            doc.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate Noble Loan Solutions contract from Java", ex);
        }
    }

    private static void configurePage(XWPFDocument doc) {
        // Use an explicit A4 page and stable printable margins so Word and
        // LibreOffice do not choose different paper sizes for generated drafts.
        var body = doc.getDocument().getBody();
        var section = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        var pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(11906));
        pageSize.setH(BigInteger.valueOf(16838));
        var margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1800));
        margins.setBottom(BigInteger.valueOf(1440));
        margins.setLeft(BigInteger.valueOf(1440));
        margins.setRight(BigInteger.valueOf(1440));
        margins.setHeader(BigInteger.valueOf(360));
        margins.setFooter(BigInteger.valueOf(360));
        margins.setGutter(BigInteger.ZERO);
        doc.getProperties().getCoreProperties().setCreator("Noble Loan Solutions Ltd");
    }

    private static void renderHeader(XWPFDocument doc) throws Exception {
        XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph logoParagraph = header.getParagraphs().isEmpty()
                ? header.createParagraph()
                : header.getParagraphs().get(0);
        logoParagraph.setAlignment(ParagraphAlignment.CENTER);
        logoParagraph.setSpacingBefore(0);
        logoParagraph.setSpacingAfter(20);
        try (InputStream logoStream = LoanContractTemplateRenderer.class.getResourceAsStream(LOGO_RESOURCE)) {
            if (logoStream == null) {
                throw new IllegalStateException(
                        "Missing contract branding resource from backend classpath: " + LOGO_RESOURCE);
            }
            XWPFRun logo = logoParagraph.createRun();
            logo.addPicture(logoStream, Document.PICTURE_TYPE_PNG, "nls-logo.png",
                    Units.toEMU(164), Units.toEMU(52));
        }

        XWPFParagraph companyLine = header.createParagraph();
        companyLine.setAlignment(ParagraphAlignment.CENTER);
        companyLine.setSpacingBefore(0);
        companyLine.setSpacingAfter(70);
        XWPFRun company = companyLine.createRun();
        company.setFontFamily("Aptos");
        company.setFontSize(8);
        company.setBold(true);
        company.setColor(NAVY);
        company.setText(
                "LICENSED LENDER  |  BNR No. 7420/2025-00211/7000  |  GISOZI, RWANDA  |  NOBLELOANSOLUTIONS.RW");
        addBottomRule(companyLine, GOLD);
    }

    private static void renderFooter(XWPFDocument doc, Borrower borrower, Loan loan) {
        XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph borrowerLine = footer.getParagraphs().isEmpty()
                ? footer.createParagraph()
                : footer.getParagraphs().get(0);
        borrowerLine.setAlignment(ParagraphAlignment.CENTER);
        borrowerLine.setSpacingBefore(30);
        borrowerLine.setSpacingAfter(0);
        XWPFRun borrowerRun = borrowerLine.createRun();
        borrowerRun.setFontFamily("Aptos");
        borrowerRun.setFontSize(7);
        borrowerRun.setBold(true);
        borrowerRun.setColor(NAVY);
        borrowerRun.setText("UGURIJWE: " + fullName(borrower) + "  |  INDANGAMUNTU: "
                + display(borrower.getNationalId(), "N/A") + "  |  TELEFONI: "
                + display(borrower.getPhone(), "N/A") + "  |  INOMERO Y’INGUZANYO: "
                + display(loan.getReferenceNumber(), "N/A"));
        addTopRule(borrowerLine, GOLD);

        XWPFParagraph statusLine = footer.createParagraph();
        statusLine.setAlignment(ParagraphAlignment.CENTER);
        statusLine.setSpacingBefore(0);
        statusLine.setSpacingAfter(0);
        XWPFRun statusRun = statusLine.createRun();
        statusRun.setFontFamily("Aptos");
        statusRun.setFontSize(7);
        statusRun.setColor(MUTED);
        statusRun.setText("CONFIDENTIAL  |  DRAFT - NOT SIGNED  |  PAGE ");
        addField(statusLine, "PAGE");
    }

    private static void addField(XWPFParagraph paragraph, String instruction) {
        XWPFRun begin = paragraph.createRun();
        begin.getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
        XWPFRun code = paragraph.createRun();
        code.getCTR().addNewInstrText().setStringValue(" " + instruction + " ");
        XWPFRun separate = paragraph.createRun();
        separate.getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
        XWPFRun value = paragraph.createRun();
        value.setText("1");
        XWPFRun end = paragraph.createRun();
        end.getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
    }

    private static void addBottomRule(XWPFParagraph paragraph, String color) {
        var pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        var borders = pPr.isSetPBdr() ? pPr.getPBdr() : pPr.addNewPBdr();
        var bottom = borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom();
        bottom.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
        bottom.setSz(BigInteger.valueOf(8));
        bottom.setColor(color);
        bottom.setSpace(BigInteger.valueOf(4));
    }

    private static void addTopRule(XWPFParagraph paragraph, String color) {
        var pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        var borders = pPr.isSetPBdr() ? pPr.getPBdr() : pPr.addNewPBdr();
        var top = borders.isSetTop() ? borders.getTop() : borders.addNewTop();
        top.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
        top.setSz(BigInteger.valueOf(8));
        top.setColor(color);
        top.setSpace(BigInteger.valueOf(4));
    }

    private static void addTitle(XWPFDocument doc, String title) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingBefore(100);
        paragraph.setSpacingAfter(100);
        keepWithNext(paragraph);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Aptos Display");
        run.setFontSize(15);
        run.setBold(true);
        run.setColor(NAVY);
        run.setText(title);
    }

    private static void addMetadata(XWPFDocument doc, String reference, String date) {
        XWPFParagraph metadata = doc.createParagraph();
        metadata.setAlignment(ParagraphAlignment.CENTER);
        metadata.setSpacingAfter(160);
        XWPFRun run = metadata.createRun();
        run.setFontFamily("Aptos");
        run.setFontSize(8);
        run.setColor(MUTED);
        run.setText(
                "INOMERO Y’INGUZANYO: " + reference + "  |  ITARIKI: " + date + "  |  IMITERERE: DRAFT / NTIRASINYWA");
    }

    private static void addLead(XWPFDocument doc, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setSpacingBefore(80);
        paragraph.setSpacingAfter(100);
        keepWithNext(paragraph);
        XWPFRun run = paragraph.createRun();
        formatRun(run, 9, true, GOLD);
        run.setText(text);
    }

    private static void addClauseHeading(XWPFDocument doc, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setSpacingBefore(180);
        paragraph.setSpacingAfter(80);
        keepWithNext(paragraph);
        XWPFRun run = paragraph.createRun();
        formatRun(run, 10, true, NAVY);
        run.setText(text);
    }

    private static void addBody(XWPFDocument doc, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(80);
        paragraph.setSpacingBetween(1.08);
        addTextRuns(paragraph, text, 9, false, TEXT);
    }

    private static void addNumbered(XWPFDocument doc, String number, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setIndentationLeft(240);
        paragraph.setIndentationFirstLine(-240);
        paragraph.setSpacingAfter(45);
        XWPFRun numberRun = paragraph.createRun();
        formatRun(numberRun, 9, true, NAVY);
        numberRun.setText(number + "  ");
        addTextRuns(paragraph, text, 9, false, TEXT);
    }

    private static void addBullet(XWPFDocument doc, String label, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setIndentationLeft(240);
        paragraph.setIndentationFirstLine(-240);
        paragraph.setSpacingAfter(95);
        XWPFRun marker = paragraph.createRun();
        formatRun(marker, 9, true, GOLD);
        marker.setText("•  " + label + ": ");
        addTextRuns(paragraph, text, 9, false, TEXT);
    }

    private static void addLetter(XWPFDocument doc, String letter, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setIndentationLeft(300);
        paragraph.setIndentationFirstLine(-260);
        paragraph.setSpacingAfter(45);
        XWPFRun marker = paragraph.createRun();
        formatRun(marker, 9, true, NAVY);
        marker.setText(letter + ".  ");
        addTextRuns(paragraph, text, 9, false, TEXT);
    }

    private static void renderSchedule(XWPFDocument doc, List<PaymentSchedule> schedules,
            String currency, String warning) {
        if (schedules == null || schedules.isEmpty()) {
            XWPFParagraph paragraph = doc.createParagraph();
            paragraph.setSpacingBefore(40);
            paragraph.setSpacingAfter(100);
            XWPFRun run = paragraph.createRun();
            formatRun(run, 9, true, "A33A3A");
            run.setText("⚠ " + warning);
            return;
        }

        XWPFTable table = doc.createTable(schedules.size() + 1, 3);
        table.setWidth("100%");
        table.setCellMargins(70, 90, 70, 90);
        table.getRow(0).setRepeatHeader(true);
        table.getRow(0).setCantSplitRow(true);
        setCell(table.getRow(0).getCell(0), "IGICE", true);
        setCell(table.getRow(0).getCell(1), "ITARIKI YO KWISHYURA", true);
        setCell(table.getRow(0).getCell(2), "AMAFARANGA YO KWISHYURA", true);
        for (int i = 0; i < schedules.size(); i++) {
            PaymentSchedule schedule = schedules.get(i);
            table.getRow(i + 1).setCantSplitRow(true);
            setCell(table.getRow(i + 1).getCell(0),
                    schedule.getInstallmentNumber() == null ? Integer.toString(i + 1)
                            : schedule.getInstallmentNumber().toString(),
                    false);
            setCell(table.getRow(i + 1).getCell(1),
                    schedule.getDueDate() == null ? "[ITARIKI IBURA]" : schedule.getDueDate().format(DATE), false);
            setCell(table.getRow(i + 1).getCell(2), money(schedule.getInstallmentAmount(), currency), false);
        }
        doc.createParagraph().setSpacingAfter(25);
    }

    private static void setCell(XWPFTableCell cell, String text, boolean heading) {
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        if (heading) {
            cell.setColor(NAVY);
        }
        XWPFParagraph paragraph = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        String[] lines = (text == null ? "" : text).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                paragraph.createRun().addBreak();
            }
            XWPFRun run = paragraph.createRun();
            formatRun(run, 8, heading, heading ? "FFFFFF" : TEXT);
            run.setText(lines[i]);
        }
    }

    private static void renderSignatureBlock(XWPFDocument doc, String borrowerName) {
        XWPFTable table = doc.createTable(3, 2);
        table.setWidth("100%");
        table.setCellMargins(90, 110, 90, 110);
        setCell(table.getRow(0).getCell(0), "UGURIJWE", true);
        setCell(table.getRow(0).getCell(1), "UGURIJE", true);
        setCell(table.getRow(1).getCell(0), borrowerName, false);
        setCell(table.getRow(1).getCell(1), "TUYISENGE Annuarite", false);
        setCell(table.getRow(2).getCell(0),
                "Umukono: __________________________\nItariki: ____________________________", false);
        setCell(table.getRow(2).getCell(1),
                "Umuyobozi Mukuru\nNoble Loan Solutions Ltd\nUmukono: _________________________\nItariki: ____________________________",
                false);
        doc.createParagraph();
    }

    private static void keepWithNext(XWPFParagraph paragraph) {
        var pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        if (!pPr.isSetKeepNext()) {
            pPr.addNewKeepNext();
        }
    }

    private static void addTextRuns(XWPFParagraph paragraph, String text, int fontSize, boolean bold, String color) {
        String[] parts = (text == null ? "" : text).split("\n", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                paragraph.createRun().addBreak();
            }
            XWPFRun run = paragraph.createRun();
            formatRun(run, fontSize, bold, color);
            run.setText(parts[i]);
        }
    }

    private static void formatRun(XWPFRun run, int fontSize, boolean bold, String color) {
        run.setFontFamily("Aptos");
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setColor(color);
    }

    private static String fullName(Borrower borrower) {
        String name = ((borrower.getFirstName() == null ? "" : borrower.getFirstName().trim()) + " "
                + (borrower.getLastName() == null ? "" : borrower.getLastName().trim())).trim();
        return name.isBlank() ? "[IZINA RY’UGURIJWE RIRABURA]" : name;
    }

    private static String display(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    private static String pct(BigDecimal value) {
        return value == null ? "[IGIPIMO NTIKIRIHO]" : value.stripTrailingZeros().toPlainString() + "%";
    }

    private static String currency(String value) {
        return value == null || value.isBlank() ? "RWF" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Monetary values are always presented as words first, followed by the
     * currency and exact numeric amount in brackets. RWF decimals, if present in
     * historical data, are explicitly represented as hundredths rather than lost.
     */
    static String money(BigDecimal value, String currencyCode) {
        if (value == null) {
            return "[AMAFARANGA NTARASHYIRWAHO]";
        }
        BigDecimal amount = value.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Negative contract amount: " + amount);
        }
        String code = currency(currencyCode);
        BigInteger whole = amount.toBigInteger();
        int fraction = amount.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
        String amountWords = currencyWords(code) + " " + numberWords(whole);
        if (fraction > 0) {
            amountWords += " n'ibice " + numberWords(BigInteger.valueOf(fraction)) + " kuri ijana";
        }
        String numeric = fraction == 0
                ? String.format(Locale.US, "%,d", whole)
                : String.format(Locale.US, "%,d.%02d", whole, fraction);
        return amountWords + " (" + code + " " + numeric + ")";
    }

    private static String currencyWords(String code) {
        return switch (code) {
            case "RWF" -> "amafaranga y’u Rwanda";
            case "USD" -> "amadolari ya Amerika";
            case "EUR" -> "ama-euro";
            case "GBP" -> "ama-pound";
            default -> "amafaranga ya " + code;
        };
    }

    private static String numberWords(BigInteger value) {
        if (value.signum() == 0) {
            return "zeru";
        }
        if (value.signum() < 0 || value.compareTo(new BigInteger("999999999999999")) > 0) {
            throw new IllegalArgumentException("Amount too large for Kinyarwanda number words: " + value);
        }
        String[] scales = { "", "ibihumbi", "miliyoni", "miliyari", "tiriyari" };
        List<String> groups = new ArrayList<>();
        int group = 0;
        BigInteger remaining = value;
        while (remaining.signum() > 0) {
            int n = remaining.mod(BigInteger.valueOf(1000)).intValue();
            if (n > 0) {
                groups.add(0, group == 0 ? underThousandWords(n) : scaledGroupWords(n, group, scales[group]));
            }
            remaining = remaining.divide(BigInteger.valueOf(1000));
            group++;
        }
        return String.join(" ", groups);
    }

    private static String scaledGroupWords(int amount, int group, String pluralScale) {
        if (group == 1 && amount == 1) {
            return "igihumbi kimwe";
        }
        if (group == 2 && amount == 1) {
            return "miliyoni imwe";
        }
        if (group == 3 && amount == 1) {
            return "miliyari imwe";
        }
        if (group == 4 && amount == 1) {
            return "tiriyari imwe";
        }
        return pluralScale + " " + scaleQuantityWords(amount);
    }

    private static String scaleQuantityWords(int value) {
        String[] quantities = { "", "kimwe", "bibiri", "bitatu", "bine", "bitanu", "bitandatu", "birindwi", "umunani",
                "icyenda" };
        if (value > 0 && value < quantities.length) {
            return quantities[value];
        }
        return underThousandWords(value);
    }

    private static String underThousandWords(int value) {
        String[] units = { "", "rimwe", "abiri", "atatu", "ane", "atanu", "atandatu", "arindwi", "umunani", "icyenda" };
        String[] tens = { "", "icumi", "makumyabiri", "mirongo itatu", "mirongo ine", "mirongo itanu",
                "mirongo itandatu", "mirongo irindwi", "mirongo inani", "mirongo icyenda" };
        String[] hundreds = { "", "ijana", "magana abiri", "magana atatu", "magana ane", "magana atanu",
                "magana atandatu", "magana arindwi", "magana inani", "magana icyenda" };
        List<String> parts = new ArrayList<>();
        if (value >= 100) {
            parts.add(hundreds[value / 100]);
        }
        int remainder = value % 100;
        if (remainder >= 20) {
            parts.add(tens[remainder / 10]);
            if (remainder % 10 > 0) {
                parts.add("na " + units[remainder % 10]);
            }
        } else if (remainder >= 10) {
            parts.add(remainder == 10 ? "icumi" : "cumi na " + units[remainder - 10]);
        } else if (remainder > 0) {
            parts.add(units[remainder]);
        }
        return String.join(" ", parts);
    }

    private static String address(Borrower borrower) {
        StringJoiner address = new StringJoiner(", ");
        Object[] parts = {
                borrower.getAddressLine1(), borrower.getAddressLine2(), borrower.getPhysicalAddressVillage(),
                borrower.getPhysicalAddressCell(), borrower.getPhysicalAddressSector(),
                borrower.getPhysicalAddressDistrict(), borrower.getPhysicalAddressProvince(), borrower.getCity(),
                borrower.getCountry()
        };
        for (Object part : parts) {
            if (part == null || part.toString().isBlank()) {
                continue;
            }
            String normalized = part.toString().trim();
            if (!address.toString().toLowerCase(Locale.ROOT).contains(normalized.toLowerCase(Locale.ROOT))) {
                address.add(normalized);
            }
        }
        if (address.length() == 0) {
            return display(borrower.getAddress(), "Aho atuye ntihagaragajwe");
        }
        return address.toString();
    }
}

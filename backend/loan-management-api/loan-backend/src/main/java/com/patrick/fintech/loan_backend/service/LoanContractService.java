package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanContract;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanContractRepository;
import com.patrick.fintech.loan_backend.repository.PaymentScheduleRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanContractService {
    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LoanContractRepository contractRepository;
    private final PaymentScheduleRepository paymentScheduleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final MailService mailService;

    /**
     * Creates the initial draft after the loan's product terms are persisted. The
     * file is
     * not an executed contract and is not proof that the borrower accepted the
     * terms.
     */
    @Transactional
    public LoanContract generateForNewLoan(Loan loan, User actor) {
        if (loan == null || loan.getId() == null || loan.getOrganization() == null
                || loan.getOrganization().getId() == null || loan.getBorrower() == null) {
            throw new IllegalArgumentException(
                    "A saved loan, organization and borrower are required to generate a contract.");
        }

        byte[] bytes = buildDraftDocument(loan);
        String currentHash = sha256(bytes);
        String currentFingerprint = textFingerprint(bytes);
        var existing = contractRepository.findByLoan_IdAndOrganization_Id(
                loan.getId(), loan.getOrganization().getId());
        if (existing.isPresent()) {
            LoanContract prior = existing.get();
            String priorFingerprint = textFingerprint(prior.getDocumentData());
            if (prior.getStatus() == LoanContract.ContractStatus.SENT_TO_BORROWER) {
                if (!currentFingerprint.equals(priorFingerprint)) {
                    throw new IllegalStateException(
                            "The contract was already sent to the borrower, but the loan terms have changed. Do not overwrite the sent copy; create and approve a formal revised contract before dispatching new terms.");
                }
                return prior;
            }
            if (currentFingerprint.equals(priorFingerprint))
                return prior;
            String previousHash = prior.getSha256();
            prior.setDocumentData(bytes);
            prior.setSha256(currentHash);
            prior.setFileName("NLS-Loan-Contract-" + safeFilePart(loan.getReferenceNumber()) + "-DRAFT.docx");
            prior.setGeneratedBy(actor);
            prior.setGeneratedAt(LocalDateTime.now());
            LoanContract refreshed = contractRepository.save(prior);
            auditService.log(loan.getOrganization(), actor,
                    "LOAN_CONTRACT_DRAFT_REFRESHED", "LOAN_CONTRACT",
                    String.valueOf(refreshed.getId()),
                    "Refreshed unsent draft from the current loan terms. Previous draft hash="
                            + previousHash + "; current hash=" + currentHash,
                    null, null, "Loan Contracts");
            return refreshed;
        }

        LoanContract contract = LoanContract.builder()
                .organization(loan.getOrganization())
                .loan(loan)
                .status(LoanContract.ContractStatus.DRAFT)
                .fileName("NLS-Loan-Contract-" + safeFilePart(loan.getReferenceNumber()) + "-DRAFT.docx")
                .contentType(DOCX_TYPE)
                .documentData(bytes)
                .sha256(currentHash)
                .generatedBy(actor)
                .generatedAt(LocalDateTime.now())
                .build();
        LoanContract saved = contractRepository.save(contract);

        List<User> recipients;
        if (loan.getLoanOfficer() != null) {
            recipients = List.of(loan.getLoanOfficer());
        } else {
            recipients = userRepository.findByOrganization(loan.getOrganization()).stream()
                    .filter(user -> user.getRole() != null && user.getRole().getName() != null)
                    .filter(user -> {
                        String role = user.getRole().getName().toUpperCase(Locale.ROOT);
                        return role.equals("LOAN_OFFICER") || role.equals("ADMIN") || role.equals("MANAGER");
                    }).toList();
            if (recipients.isEmpty() && actor != null) {
                recipients = List.of(actor);
            }
        }
        if (!recipients.isEmpty()) {
            notificationService.notifyUsers(
                    recipients,
                    "Loan contract draft ready",
                    "A draft contract was generated for loan " + loan.getReferenceNumber()
                            + ". Download it, review it, and send the draft to the borrower for review and signature.",
                    "LOAN_CONTRACT_DRAFT",
                    "/dashboard/loans/" + loan.getId());
        }
        auditService.log(
                loan.getOrganization(), actor,
                "LOAN_CONTRACT_DRAFT_GENERATED", "LOAN_CONTRACT",
                String.valueOf(saved.getId()),
                "Generated draft contract for loan " + loan.getReferenceNumber()
                        + "; draft requires review and signature.",
                null, null, "Loan Contracts");
        log.info("Draft contract generated. loanId={}, contractId={}, sha256={}",
                loan.getId(), saved.getId(), saved.getSha256());
        return saved;
    }

    @Transactional(readOnly = true)
    public LoanContract getForOrganization(Long loanId, Long organizationId) {
        if (loanId == null || loanId <= 0 || organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("Loan and organization identifiers are required.");
        }
        return contractRepository.findByLoan_IdAndOrganization_Id(loanId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found for this loan."));
    }

    /**
     * Staff records that they have sent the downloaded draft through their approved
     * channel.
     * The platform then notifies the borrower's email; it does not claim the
     * contract was signed.
     */
    @Transactional
    public LoanContract markSent(Long loanId, Long organizationId, User actor, boolean termsReviewed) {
        if (actor == null || actor.getOrganization() == null
                || actor.getOrganization().getId() == null
                || !actor.getOrganization().getId().equals(organizationId)) {
            throw new IllegalArgumentException("You are not authorized for this organization's contract.");
        }
        LoanContract contract = getForOrganization(loanId, organizationId);
        if (contract.getStatus() == LoanContract.ContractStatus.SENT_TO_BORROWER)
            return contract;
        if (!termsReviewed) {
            throw new IllegalArgumentException(
                    "The loan officer must explicitly confirm that borrower details, rates, fees, penalty terms and the repayment schedule were reviewed before dispatch.");
        }
        String contractText = extractDocumentText(contract.getDocumentData()).toLowerCase(Locale.ROOT);
        if (contractText.contains("gahunda y’ubwishyu ntiraboneka muri sisitemu")) {
            throw new IllegalStateException(
                    "The draft does not contain a repayment schedule. Generate the schedule, refresh the draft, and review it before sending the contract to the borrower.");
        }
        contract.setStatus(LoanContract.ContractStatus.SENT_TO_BORROWER);
        contract.setSentBy(actor);
        contract.setSentAt(LocalDateTime.now());
        LoanContract saved = contractRepository.save(contract);
        auditService.log(
                contract.getOrganization(), actor,
                "LOAN_CONTRACT_DRAFT_SENT", "LOAN_CONTRACT",
                String.valueOf(contract.getId()),
                "Recorded dispatch of draft contract for loan "
                        + contract.getLoan().getReferenceNumber()
                        + "; staff explicitly confirmed review of borrower details, rates, fees, penalty terms and repayment schedule; this does not represent borrower signature.",
                null, null, "Loan Contracts");

        if (mailService.isMailEnabled()) {
            try {
                mailService.sendLoanContractDraftNotice(contract.getLoan());
                // This timestamp records queueing, not proof of inbox delivery or borrower
                // reading.
                saved.setBorrowerNotifiedAt(LocalDateTime.now());
                saved = contractRepository.save(saved);
            } catch (RuntimeException e) {
                log.error("Could not queue borrower contract notice. loanId={}", loanId, e);
            }
        } else {
            log.warn(
                    "Contract dispatch recorded but borrower email notice was not queued because app.mail.enabled=false. loanId={}",
                    loanId);
        }
        return saved;
    }

    /**
     * Generates the contract document entirely in Java. No DOCX template or other
     * contract file is loaded from application resources.
     */

    private byte[] buildDraftDocument(Loan loan) {
        Borrower borrower = loan.getBorrower();

        BigDecimal principal = zeroIfNull(loan.getAmountDecimal());
        BigDecimal interestRate = zeroIfNull(loan.getInterestRateDecimal());
        BigDecimal managementRate = zeroIfNull(loan.getManagementFeeRateDecimal());
        BigDecimal applicationRate = zeroIfNull(loan.getApplicationFeeRateDecimal());
        BigDecimal penaltyRate = zeroIfNull(loan.getPenaltyRateDecimal());

        BigDecimal monthlyInterest = principal.multiply(interestRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        if ("ANNUAL".equalsIgnoreCase(value(loan.getInterestRateType()))) {
            monthlyInterest = monthlyInterest.divide(
                    new BigDecimal("12"), 2, RoundingMode.HALF_UP);
        }

        BigDecimal monthlyManagement = principal.multiply(managementRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        BigDecimal applicationFee = loan.getApplicationFeeDecimal() == null
                ? principal.multiply(applicationRate)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : loan.getApplicationFeeDecimal();

        List<PaymentSchedule> schedules = loan.getId() == null
                ? List.of()
                : paymentScheduleRepository
                        .findByLoanIdOrderByInstallmentNumberAsc(loan.getId());

        try (XWPFDocument doc = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            doc.getProperties().getCoreProperties()
                    .setTitle("Amasezerano y'inguzanyo - "
                            + value(loan.getReferenceNumber()));

            doc.getProperties().getCoreProperties()
                    .setSubjectProperty("Umushinga w'amasezerano y'inguzanyo");

            doc.getProperties().getCoreProperties()
                    .setCreator("Noble Loan Solutions Ltd");

            XWPFParagraph company = doc.createParagraph();
            company.setAlignment(ParagraphAlignment.CENTER);

            XWPFRun companyRun = company.createRun();
            companyRun.setBold(true);
            companyRun.setFontSize(16);
            companyRun.setText("NOBLE LOAN SOLUTIONS LTD");

            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);

            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(14);
            titleRun.setText(
                    "AMASEZERANO Y’INGUZANYO — UMUSHINGA (DRAFT / NTIRASINYWA)");

            paragraph(doc,
                    "Nomero y'inguzanyo: " + value(loan.getReferenceNumber())
                            + "     Itariki yakozwemo: "
                            + LocalDate.now().format(DATE));

            paragraph(doc,
                    "Aya ni amasezerano y'ibanze yakozwe hakoreshejwe amakuru abitswe muri sisitemu. "
                            + "Agomba gusuzumwa n'ababifitiye ububasha mbere yo koherezwa cyangwa gusinywa.");

            heading(doc, "1. AMAKURU Y'UGURIZA N'UGURIZWA");

            paragraph(doc,
                    "Ugizanya ni Noble Loan Solutions Ltd (NLS), ihagarariwe n'uhagarariye ikigo ubifitiye ububasha.");

            paragraph(doc, "Ugurizwa: " + fullName(borrower));

            paragraph(doc,
                    "Indangamuntu/pasiporo: " + value(borrower.getNationalId()));

            paragraph(doc,
                    "Telefone: " + value(borrower.getPhone())
                            + " | Email: " + value(borrower.getEmail()));

            paragraph(doc,
                    "Uwo bashakanye: " + value(borrower.getSpouseFullName())
                            + " | Indangamuntu y'uwo bashakanye: "
                            + value(borrower.getSpouseNationalId()));

            paragraph(doc, "Aderesi: " + address(borrower));

            paragraph(doc,
                    "Aho indangamuntu yatangiwe: amakuru ataratangwa muri dosiye.");

            heading(doc, "2. UMUBARE W'INGUZANYO N'IMPAMVU YAYO");

            paragraph(doc,
                    "NLS ishyira imbere y'ugurizwa inguzanyo ingana na "
                            + money(principal, loan.getCurrency())
                            + ". Impamvu/icyo inguzanyo izakoreshwamo: "
                            + value(loan.getPurpose()) + ".");

            paragraph(doc,
                    "Igihe cy'inguzanyo: amezi " + value(loan.getDurationMonths())
                            + ". Itariki yo gutangiriraho: " + value(loan.getStartDate())
                            + ". Amafaranga ateganyijwe kwishyurwa yose: "
                            + money(loan.getTotalRepayableDecimal(), loan.getCurrency())
                            + ".");

            heading(doc, "3. INYUNGU N'AMAFARANGA AJYANYE N'INGUZANYO");

            paragraph(doc,
                    "Igipimo cy'inyungu: " + percent(interestRate)
                            + ("ANNUAL".equalsIgnoreCase(
                                    value(loan.getInterestRateType()))
                                            ? " ku mwaka"
                                            : " ku kwezi")
                            + ". Inyungu y'ukwezi ku mubare wose w'ibanze (ikigereranyo): "
                            + money(monthlyInterest, loan.getCurrency()) + ".");

            paragraph(doc,
                    "Amafaranga yo gutunganya dosiye: " + percent(applicationRate)
                            + " (ikigereranyo: "
                            + money(applicationFee, loan.getCurrency()) + ").");

            paragraph(doc,
                    "Amafaranga yo gukurikirana inguzanyo: " + percent(managementRate)
                            + " ku kwezi (ikigereranyo ku mubare w'ibanze: "
                            + money(monthlyManagement, loan.getCurrency()) + ").");

            paragraph(doc,
                    "Igipimo cy'igihano cy'ubukererwe kibitswe kuri iyi nguzanyo: "
                            + percent(penaltyRate)
                            + ". Uburyo bwo kubara no gukoresha igihano bugomba guhuye "
                            + "na politiki yemejwe n'amategeko akurikizwa.");

            heading(doc, "4. GAHUNDA YO KWISHYURA");

            paragraph(doc,
                    "Ugurizwa azishyura hakurikijwe gahunda yemejwe iri muri sisitemu. "
                            + "Nomero y'inguzanyo: "
                            + value(loan.getReferenceNumber()) + ".");

            if (schedules == null || schedules.isEmpty()) {
                paragraph(doc,
                        "IKIMENYETSO CYO KUGENZURA: gahunda y’ubwishyu ntiraboneka muri sisitemu. "
                                + "Ntukohereze uyu mushinga kugeza gahunda yanyuma ibitswe kandi igenzuwe.");
            } else {
                String[][] rows = new String[schedules.size()][5];

                for (int i = 0; i < schedules.size(); i++) {
                    PaymentSchedule p = schedules.get(i);

                    rows[i][0] = value(p.getInstallmentNumber());
                    rows[i][1] = date(p.getDueDate());
                    rows[i][2] = money(p.getPrincipalAmount(), loan.getCurrency());
                    rows[i][3] = money(p.getInterestAmount(), loan.getCurrency());
                    rows[i][4] = money(p.getInstallmentAmount(), loan.getCurrency());
                }

                addTable(doc,
                        new String[] {
                                "Igice",
                                "Itariki yo kwishyura",
                                "Umwenda shingiro",
                                "Inyungu",
                                "Igiteranyo"
                        },
                        rows);
            }

            heading(doc, "5. INSHINGANO Z'UGURIZWA");

            paragraph(doc,
                    "Ugurizwa yiyemeza kwishyura amafaranga yemejwe ku matariki agaragara "
                            + "muri gahunda yo kwishyura, gutanga amakuru y'ukuri, no kumenyesha "
                            + "NLS impinduka zikomeye ku makuru ye cyangwa ubushobozi bwo kwishyura.");

            heading(doc, "6. UBURYO BWO KWISHYURA");

            paragraph(doc,
                    "Kwishyura bizakorwa hakoreshejwe uburyo bwemejwe n'ikigo kandi buhuzwa "
                            + "na konti/amakuru yemejwe mu nyandiko z'ikigo. Umukozi agomba "
                            + "kwemeza amakuru ya banki mbere yo kohereza amasezerano.");

            heading(doc, "7. UBUTINDE N'INGARUKA ZABWO");

            paragraph(doc,
                    "Iyo ubwishyu bukerewe, igihano n'izindi ngamba bizakurikiza gusa ibipimo, "
                            + "uburyo bwo kubara n'amategeko byemejwe kandi byasobanuwe "
                            + "mu masezerano yemejwe.");

            heading(doc, "8. GUKEMURA AMAKIMBIRANE");

            paragraph(doc,
                    "Impande zizagerageza gukemura ikibazo mu bwumvikane. Iyo bidashobotse, "
                            + "ikibazo kizakemurwa hakurikijwe amategeko n'ububasha "
                            + "bw'inzego zibifitiye ububasha.");

            heading(doc, "9. GUHINDURA AMASEZERANO");

            paragraph(doc,
                    "Impinduka ku mubare, ibiciro, amafaranga cyangwa gahunda yo kwishyura "
                            + "zigomba kwandikwa, kwemezwa n'ababifitiye ububasha, no gusinywa "
                            + "n'impande bireba.");

            heading(doc, "10. KWEMERA NO GUSINYA");

            paragraph(doc,
                    "Buri ruhande rugomba gusoma no gusobanukirwa amasezerano yose mbere yo kuyasinya. "
                            + "Iyi nyandiko ni umushinga gusa kandi ntiba amasezerano yemewe kugeza "
                            + "igihe impande zisinyiye kopi ya nyuma.");

            heading(doc, "IMIKONO");

            paragraph(doc,
                    "Ugurizwa / Borrower: " + fullName(borrower));

            paragraph(doc,
                    "Umukono: ____________________________________    Itariki: ____________________");

            paragraph(doc,
                    "Uhagarariye Noble Loan Solutions Ltd: Tuyisenge Annuarite, CEO "
                            + "(genzura izina n'ububasha mbere yo gusinya)");

            paragraph(doc,
                    "Umukono: ____________________________________    Itariki: ____________________");

            XWPFParagraph footer = doc.createParagraph();
            footer.setSpacingBefore(200);

            XWPFRun footerRun = footer.createRun();
            footerRun.setItalic(true);
            footerRun.setFontSize(8);
            footerRun.setText(
                    "UMUSHINGA GUSA — NTIRASINYWA. Iyi nyandiko yakozwe mu buryo bwikora "
                            + "hakoreshejwe amakuru ya sisitemu. Igomba gusuzumwa n'ababifitiye "
                            + "ububasha; ntabwo yemeza ko inguzanyo yemejwe cyangwa ko amafaranga yatanzwe.");

            doc.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not generate the loan contract draft from loan and borrower data.",
                    e);
        }
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value;
    }

    private static void paragraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(100);
        p.createRun().setText(text);
    }

    private static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(120);
        p.setSpacingAfter(60);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(11);
        r.setText(text);
    }

    private static void addTable(XWPFDocument doc, String[] headers, String[][] rows) {
        XWPFTable table = doc.createTable(rows.length + 1, headers.length);
        for (int c = 0; c < headers.length; c++) {
            XWPFRun run = table.getRow(0).getCell(c).getParagraphs().get(0).createRun();
            run.setBold(true);
            run.setText(headers[c]);
        }
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < headers.length; c++) {
                table.getRow(r + 1).getCell(c).getParagraphs().get(0).createRun()
                        .setText(rows[r][c] == null ? "" : rows[r][c]);
            }
        }
        doc.createParagraph();
    }

    private static String fullName(Borrower b) {
        return ((value(b.getFirstName()) + " " + value(b.getLastName())).trim());
    }

    private static String address(Borrower b) {
        StringBuilder s = new StringBuilder();
        for (String part : List.of(value(b.getAddressLine1()), value(b.getAddressLine2()),
                value(b.getPhysicalAddressVillage()), value(b.getPhysicalAddressCell()),
                value(b.getPhysicalAddressSector()), value(b.getPhysicalAddressDistrict()),
                value(b.getCity()), value(b.getCountry()))) {
            if (!part.equals("—") && !s.toString().contains(part)) {
                if (s.length() > 0)
                    s.append(", ");
                s.append(part);
            }
        }
        return s.length() == 0 ? value(b.getAddress()) : s.toString();
    }

    private static String value(Object value) {
        if (value == null || value.toString().isBlank())
            return "—";
        return value.toString().trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String date(LocalDate date) {
        return date == null ? "—" : date.format(DATE);
    }

    private static String money(BigDecimal amount, String currency) {
        if (amount == null)
            return "—";
        return value(currency) + " " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String percent(BigDecimal amount) {
        return amount == null ? "—" : amount.stripTrailingZeros().toPlainString() + "%";
    }

    private static String safeFilePart(String input) {
        return (input == null ? "LOAN" : input.replaceAll("[^A-Za-z0-9_-]", "-"));
    }

    private static String textFingerprint(byte[] documentBytes) {
        return sha256(extractDocumentText(documentBytes).getBytes(StandardCharsets.UTF_8));
    }

    private static String extractDocumentText(byte[] documentBytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(documentBytes))) {
            StringBuilder content = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                content.append(paragraph.getText()).append('\n');
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        content.append(cell.getText()).append('\t');
                    }
                    content.append('\n');
                }
            }
            return content.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read the generated loan contract document.", e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate contract checksum.", e);
        }
    }
}

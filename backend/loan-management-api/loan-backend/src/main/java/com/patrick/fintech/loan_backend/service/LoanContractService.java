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
        String currentFingerprint = textFingerprint(bytes); // contract-body fingerprint for sent-term safety
        String currentDraftFingerprint = draftFingerprint(bytes); // also detects header/footer/logo/schema changes
        var existing = contractRepository.findByLoan_IdAndOrganization_Id(
                loan.getId(), loan.getOrganization().getId());
        if (existing.isPresent()) {
            LoanContract prior = existing.get();
            String priorFingerprint = textFingerprint(prior.getDocumentData());
            String priorDraftFingerprint = draftFingerprint(prior.getDocumentData());
            if (prior.getStatus() == LoanContract.ContractStatus.SENT_TO_BORROWER) {
                if (!currentFingerprint.equals(priorFingerprint)) {
                    throw new IllegalStateException(
                            "The contract was already sent to the borrower, but the loan terms have changed. Do not overwrite the sent copy; create and approve a formal revised contract before dispatching new terms.");
                }
                return prior;
            }
            if (currentDraftFingerprint.equals(priorDraftFingerprint))
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
        if (contractText.contains("gahunda yo kwishyura ntiraboneka muri sisitemu")
                || contractText.contains("ntimwohereze amasezerano")
                || contractText.contains("[kuzuza]")
                || contractText.contains("[itariki ibura]")) {
            throw new IllegalStateException(
                    "The draft does not contain a repayment schedule. Generate the schedule, refresh the draft, and review it before sending the contract to the borrower.");
        }
        if (contractText.contains("[igipimo ntikiriho]")
                || contractText.contains("[amafaranga ntarashyirwaho]")) {
            throw new IllegalStateException(
                    "The draft is missing one or more contractual rates or monetary amounts. Correct the saved loan terms, regenerate the draft, and review it before sending the contract to the borrower.");
        }
        // The attachment is the exact immutable document bytes already persisted
        // on this contract, not a newly rendered copy with potentially changed terms.
        // A provider rejection must not transition the contract to SENT_TO_BORROWER.
        mailService.sendLoanContractDraftWithAttachment(
                contract.getLoan(), contract.getFileName(), contract.getDocumentData());

        contract.setStatus(LoanContract.ContractStatus.SENT_TO_BORROWER);
        contract.setSentBy(actor);
        contract.setSentAt(LocalDateTime.now());
        // Provider acceptance is not proof of inbox delivery or borrower reading.
        contract.setBorrowerNotifiedAt(LocalDateTime.now());
        LoanContract saved = contractRepository.save(contract);
        auditService.log(
                contract.getOrganization(), actor,
                "LOAN_CONTRACT_DRAFT_SENT", "LOAN_CONTRACT",
                String.valueOf(contract.getId()),
                "Email provider accepted the draft DOCX attachment for loan "
                        + contract.getLoan().getReferenceNumber()
                        + "; staff confirmed review; borrower signature and delivery are not confirmed.",
                null, null, "Loan Contracts");
        return saved;
    }

    /**
     * Generates the contract document entirely in Java. No DOCX template or other
     * contract file is loaded from application resources.
     */

    private byte[] buildDraftDocument(Loan loan) {
        return LoanContractTemplateRenderer.render(
                loan, paymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId()));
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
        return sha256(extractBodyText(documentBytes).getBytes(StandardCharsets.UTF_8));
    }

    private static String draftFingerprint(byte[] documentBytes) {
        return sha256(extractDocumentText(documentBytes).getBytes(StandardCharsets.UTF_8));
    }

    private static String extractBodyText(byte[] documentBytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(documentBytes))) {
            StringBuilder content = new StringBuilder();
            appendDocumentText(content, document.getParagraphs(), document.getTables());
            return content.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read the generated loan contract document.", e);
        }
    }

    private static String extractDocumentText(byte[] documentBytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(documentBytes))) {
            StringBuilder content = new StringBuilder();
            // Include the hidden renderer schema in the fingerprint. Layout-only
            // changes (for example moving the logo into the header) must refresh an
            // unsent draft even when all legal body text remains identical.
            if (document.getProperties() != null && document.getProperties().getCoreProperties() != null) {
                content.append("\n[CONTRACT_SCHEMA:")
                        .append(document.getProperties().getCoreProperties().getDescription())
                        .append("]\n");
            }
            appendDocumentText(content, document.getParagraphs(), document.getTables());
            for (XWPFHeader header : document.getHeaderList()) {
                content.append("\n[HEADER]\n");
                appendDocumentText(content, header.getParagraphs(), header.getTables());
            }
            for (XWPFFooter footer : document.getFooterList()) {
                content.append("\n[FOOTER]\n");
                appendDocumentText(content, footer.getParagraphs(), footer.getTables());
            }
            return content.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read the generated loan contract document.", e);
        }
    }

    private static void appendDocumentText(StringBuilder content,
            List<XWPFParagraph> paragraphs, List<XWPFTable> tables) {
        for (XWPFParagraph paragraph : paragraphs) {
            content.append(paragraph.getText()).append('\n');
        }
        for (XWPFTable table : tables) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    content.append(cell.getText()).append('\t');
                }
                content.append('\n');
            }
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

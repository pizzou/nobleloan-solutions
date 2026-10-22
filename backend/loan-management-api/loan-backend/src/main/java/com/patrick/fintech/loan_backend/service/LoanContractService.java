
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanContract;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanContractRepository;
import com.patrick.fintech.loan_backend.repository.PaymentScheduleRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanContractService {

    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final LoanContractRepository contractRepository;
    private final PaymentScheduleRepository paymentScheduleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final MailService mailService;

    @Transactional
    public LoanContract generateForNewLoan(Loan loan, User actor) {
        if (loan == null
                || loan.getId() == null
                || loan.getOrganization() == null
                || loan.getOrganization().getId() == null
                || loan.getBorrower() == null) {
            throw new IllegalArgumentException(
                    "A saved loan, organization and borrower are required.");
        }

        byte[] documentBytes = buildDraftDocument(loan);
        String documentHash = sha256(documentBytes);
        String termsFingerprint = textFingerprint(documentBytes);

        var existing = contractRepository
                .findByLoan_IdAndOrganization_Id(
                        loan.getId(),
                        loan.getOrganization().getId());

        if (existing.isPresent()) {
            LoanContract prior = existing.get();

            if (prior.getStatus() == LoanContract.ContractStatus.SENT_TO_BORROWER) {

                String previousTerms = textFingerprint(prior.getDocumentData());

                if (!termsFingerprint.equals(previousTerms)) {
                    throw new IllegalStateException(
                            "This contract has already been sent. "
                                    + "The terms have changed. A formally "
                                    + "approved revised contract is required.");
                }

                // Never replace an already-sent document.
                return prior;
            }

            // Compare complete DOCX bytes, not just body text.
            // This detects new headers, footers, images and styles.
            String storedHash = sha256(prior.getDocumentData());

            if (documentHash.equals(storedHash)) {
                return prior;
            }

            String previousHash = prior.getSha256();

            prior.setDocumentData(documentBytes);
            prior.setSha256(documentHash);
            prior.setContentType(DOCX_TYPE);
            prior.setFileName(
                    contractFileName(loan.getReferenceNumber()));
            prior.setGeneratedBy(actor);
            prior.setGeneratedAt(LocalDateTime.now());

            LoanContract refreshed = contractRepository.save(prior);

            auditService.log(
                    loan.getOrganization(),
                    actor,
                    "LOAN_CONTRACT_DRAFT_REFRESHED",
                    "LOAN_CONTRACT",
                    String.valueOf(refreshed.getId()),
                    "Refreshed unsent contract document. Previous hash="
                            + previousHash
                            + "; current hash=" + documentHash,
                    null,
                    null,
                    "Loan Contracts");

            log.info(
                    "Loan contract refreshed. loanId={}, contractId={}, hash={}",
                    loan.getId(),
                    refreshed.getId(),
                    documentHash);

            return refreshed;
        }

        LoanContract contract = LoanContract.builder()
                .organization(loan.getOrganization())
                .loan(loan)
                .status(LoanContract.ContractStatus.DRAFT)
                .fileName(contractFileName(loan.getReferenceNumber()))
                .contentType(DOCX_TYPE)
                .documentData(documentBytes)
                .sha256(documentHash)
                .generatedBy(actor)
                .generatedAt(LocalDateTime.now())
                .build();

        LoanContract saved = contractRepository.save(contract);

        List<User> recipients;

        if (loan.getLoanOfficer() != null) {
            recipients = List.of(loan.getLoanOfficer());
        } else {
            recipients = userRepository
                    .findByOrganization(loan.getOrganization())
                    .stream()
                    .filter(user -> user.getRole() != null
                            && user.getRole().getName() != null)
                    .filter(user -> {
                        String role = user.getRole()
                                .getName()
                                .toUpperCase(Locale.ROOT);

                        return role.equals("LOAN_OFFICER")
                                || role.equals("ADMIN")
                                || role.equals("MANAGER");
                    })
                    .toList();

            if (recipients.isEmpty() && actor != null) {
                recipients = List.of(actor);
            }
        }

        if (!recipients.isEmpty()) {
            notificationService.notifyUsers(
                    recipients,
                    "Loan contract draft ready",
                    "A draft contract was generated for loan "
                            + loan.getReferenceNumber()
                            + ". Review the document before sending.",
                    "LOAN_CONTRACT_DRAFT",
                    "/dashboard/loans/" + loan.getId());
        }

        auditService.log(
                loan.getOrganization(),
                actor,
                "LOAN_CONTRACT_DRAFT_GENERATED",
                "LOAN_CONTRACT",
                String.valueOf(saved.getId()),
                "Generated contract draft for loan "
                        + loan.getReferenceNumber(),
                null,
                null,
                "Loan Contracts");

        log.info(
                "Loan contract generated. loanId={}, contractId={}, hash={}",
                loan.getId(),
                saved.getId(),
                documentHash);

        return saved;
    }

    @Transactional(readOnly = true)
    public LoanContract getForOrganization(
            Long loanId,
            Long organizationId) {
        if (loanId == null
                || loanId <= 0
                || organizationId == null
                || organizationId <= 0) {
            throw new IllegalArgumentException(
                    "Valid loan and organization identifiers are required.");
        }

        return contractRepository
                .findByLoan_IdAndOrganization_Id(
                        loanId,
                        organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Contract not found for this loan."));
    }

    @Transactional
    public LoanContract markSent(
            Long loanId,
            Long organizationId,
            User actor,
            boolean termsReviewed) {
        if (actor == null
                || actor.getOrganization() == null
                || actor.getOrganization().getId() == null
                || !actor.getOrganization().getId()
                        .equals(organizationId)) {
            throw new IllegalArgumentException(
                    "You are not authorized for this organization's contract.");
        }

        LoanContract contract = getForOrganization(loanId, organizationId);

        if (contract.getStatus() == LoanContract.ContractStatus.SENT_TO_BORROWER) {
            return contract;
        }

        if (!termsReviewed) {
            throw new IllegalArgumentException(
                    "Staff must confirm review of borrower details, "
                            + "rates, fees, penalties and repayment schedule.");
        }

        String documentText = extractDocumentText(contract.getDocumentData())
                .toLowerCase(Locale.ROOT);

        if (documentText.contains("gahunda yo kwishyura ntiraboneka")
                || documentText.contains("ntimwohereze amasezerano")
                || documentText.contains("[kuzuza]")
                || documentText.contains("[itariki ibura]")) {
            throw new IllegalStateException(
                    "The contract contains incomplete information. "
                            + "Correct the loan data and regenerate the draft.");
        }

        // Send the exact stored attachment.
        mailService.sendLoanContractDraftWithAttachment(
                contract.getLoan(),
                contract.getFileName(),
                contract.getDocumentData());

        contract.setStatus(
                LoanContract.ContractStatus.SENT_TO_BORROWER);
        contract.setSentBy(actor);
        contract.setSentAt(LocalDateTime.now());
        contract.setBorrowerNotifiedAt(LocalDateTime.now());

        LoanContract saved = contractRepository.save(contract);

        auditService.log(
                contract.getOrganization(),
                actor,
                "LOAN_CONTRACT_DRAFT_SENT",
                "LOAN_CONTRACT",
                String.valueOf(saved.getId()),
                "Email provider accepted the contract attachment for loan "
                        + contract.getLoan().getReferenceNumber()
                        + ". Borrower signature is not confirmed.",
                null,
                null,
                "Loan Contracts");

        return saved;
    }

    private byte[] buildDraftDocument(Loan loan) {
        return LoanContractTemplateRenderer.render(
                loan,
                paymentScheduleRepository
                        .findByLoanIdOrderByInstallmentNumberAsc(
                                loan.getId()));
    }

    private static String contractFileName(String reference) {
        return "NLS-Loan-Contract-"
                + safeFilePart(reference)
                + "-DRAFT.docx";
    }

    private static String safeFilePart(String input) {
        return input == null
                ? "LOAN"
                : input.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private static String textFingerprint(byte[] bytes) {
        return sha256(
                extractDocumentText(bytes)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static String extractDocumentText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException(
                    "Contract document is missing.");
        }

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {

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

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to read loan contract DOCX.",
                    ex);
        }
    }

    private static String sha256(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException(
                    "Cannot hash a null contract document.");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(bytes));

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to calculate contract SHA-256.",
                    ex);
        }
    }
}

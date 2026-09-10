package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.LendingActionRequest;
import com.patrick.fintech.loan_backend.model.BankAccount;
import com.patrick.fintech.loan_backend.model.BankStatementLine;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.BorrowerFile;
import com.patrick.fintech.loan_backend.model.CollectionCase;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.LendingFeatureRecord;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.BankAccountRepository;
import com.patrick.fintech.loan_backend.repository.BankStatementLineRepository;
import com.patrick.fintech.loan_backend.repository.BorrowerFileRepository;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.CollectionCaseRepository;
import com.patrick.fintech.loan_backend.repository.LendingFeatureRecordRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LendingIntelligenceService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal MAX_REQUEST = new BigDecimal("50000000");
    private static final BigDecimal MAX_EXPOSURE = new BigDecimal("100000000");
    private static final BigDecimal MAX_DTI = new BigDecimal("40");
    private static final BigDecimal CONCENTRATION_LIMIT = new BigDecimal("25");
    private static final BigDecimal DEFAULT_LGD = new BigDecimal("45");

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;
    private final BorrowerRepository borrowerRepository;
    private final BankStatementLineRepository bankStatementLineRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BorrowerFileRepository borrowerFileRepository;
    private final CollectionCaseRepository collectionCaseRepository;
    private final LendingFeatureRecordRepository recordRepository;
    private final OrganizationRepository organizationRepository;
    private final CurrentUserUtil currentUserUtil;
    private final ObjectMapper objectMapper;

    private Long organizationId() {
        return currentUserUtil.getCurrentOrganizationId();
    }

    // 1. PAR1/PAR7/PAR30/PAR60/PAR90
    public Map<String, Object> portfolioRisk(LocalDate asOf) {
        LocalDate reportDate = asOf == null ? LocalDate.now() : asOf;
        List<Loan> loans = organizationLoans();
        BigDecimal totalOutstanding = sumLoans(loans, Loan::getOutstandingBalance);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asOf", reportDate);
        result.put("totalOutstandingPrincipal", money(totalOutstanding));
        result.put("par1", parBucket(loans, 1, totalOutstanding, reportDate));
        result.put("par7", parBucket(loans, 7, totalOutstanding, reportDate));
        result.put("par30", parBucket(loans, 30, totalOutstanding, reportDate));
        result.put("par60", parBucket(loans, 60, totalOutstanding, reportDate));
        result.put("par90", parBucket(loans, 90, totalOutstanding, reportDate));
        return result;
    }

    private Map<String, Object> parBucket(List<Loan> loans, int threshold,
                                          BigDecimal denominator, LocalDate asOf) {
        List<Loan> matched = loans.stream()
                .filter(loan -> daysPastDue(loan, asOf) >= threshold)
                .filter(loan -> positive(loan.getOutstandingBalance()))
                .toList();
        BigDecimal amount = sumLoans(matched, Loan::getOutstandingBalance);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("thresholdDays", threshold);
        result.put("loanCount", matched.size());
        result.put("outstandingPrincipal", money(amount));
        result.put("percentage", percentage(amount, denominator));
        return result;
    }

    // 2. Vintage/cohort analysis
    public List<Map<String, Object>> vintage(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusYears(3) : from;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("from date cannot be after to date");
        }

        Map<YearMonth, List<Loan>> groups = organizationLoans().stream()
                .filter(loan -> loan.getDisbursedAt() != null)
                .filter(loan -> {
                    LocalDate d = loan.getDisbursedAt().toLocalDate();
                    return !d.isBefore(start) && !d.isAfter(end);
                })
                .collect(Collectors.groupingBy(
                        loan -> YearMonth.from(loan.getDisbursedAt()),
                        TreeMap::new,
                        Collectors.toList()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<YearMonth, List<Loan>> entry : groups.entrySet()) {
            List<Loan> loans = entry.getValue();
            long par30 = loans.stream()
                    .filter(loan -> daysPastDue(loan, end) >= 30)
                    .count();
            BigDecimal disbursed = sumLoans(loans,
                    loan -> loan.getDisbursedAmount() != null
                            ? loan.getDisbursedAmount() : loan.getAmount());
            BigDecimal outstanding = sumLoans(loans, Loan::getOutstandingBalance);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("vintage", entry.getKey().toString());
            row.put("loanCount", loans.size());
            row.put("disbursedAmount", money(disbursed));
            row.put("outstandingAmount", money(outstanding));
            row.put("par30Loans", par30);
            row.put("par30Percentage", percentage(
                    BigDecimal.valueOf(par30), BigDecimal.valueOf(loans.size())));
            result.add(row);
        }
        return result;
    }

    // 3. IFRS 9 / ECL baseline
    public Map<String, Object> ecl(LocalDate asOf) {
        LocalDate reportDate = asOf == null ? LocalDate.now() : asOf;
        Map<String, BigDecimal> stageEcl = new LinkedHashMap<>();
        stageEcl.put("STAGE_1", ZERO);
        stageEcl.put("STAGE_2", ZERO);
        stageEcl.put("STAGE_3", ZERO);

        for (Loan loan : organizationLoans()) {
            BigDecimal ead = nonNegative(loan.getOutstandingBalance());
            int dpd = daysPastDue(loan, reportDate);
            String stage = dpd >= 90 ? "STAGE_3" : dpd >= 30 ? "STAGE_2" : "STAGE_1";
            BigDecimal pd = dpd >= 90
                    ? new BigDecimal("60")
                    : dpd >= 30 ? new BigDecimal("20") : new BigDecimal("3");
            BigDecimal ecl = ead.multiply(pd)
                    .divide(HUNDRED, 8, RoundingMode.HALF_UP)
                    .multiply(DEFAULT_LGD)
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            stageEcl.put(stage, stageEcl.get(stage).add(ecl));
        }

        BigDecimal total = stageEcl.values().stream().reduce(ZERO, BigDecimal::add);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asOf", reportDate);
        result.put("methodology", "Baseline ECL segmentation: Stage 1 <30 DPD, Stage 2 30-89 DPD, Stage 3 >=90 DPD");
        result.put("lgdPercent", DEFAULT_LGD);
        result.put("stageEcl", stageEcl.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> money(e.getValue()),
                        (a, b) -> a, LinkedHashMap::new)));
        result.put("totalEcl", money(total));
        return result;
    }

    // 4. Automated affordability from bank statements
    public Map<String, Object> affordability(Long bankAccountId, LocalDate from, LocalDate to) {
        Long orgId = organizationId();
        BankAccount account = null;
        if (bankAccountId != null) {
            account = bankAccountRepository.findById(bankAccountId)
                    .filter(a -> a.getOrganization() != null
                            && orgId.equals(a.getOrganization().getId()))
                    .orElseThrow(() -> new NoSuchElementException("Bank account not found"));
        }

        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(90) : from;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("from date cannot be after to date");
        }

        List<BankStatementLine> lines = bankAccountId == null
                ? bankStatementLineRepository.findAll().stream()
                    .filter(line -> line.getOrganization() != null
                            && orgId.equals(line.getOrganization().getId()))
                    .filter(line -> !line.getTransactionDate().isBefore(start)
                            && !line.getTransactionDate().isAfter(end))
                    .toList()
                : bankStatementLineRepository
                    .findByOrganization_IdAndBankAccount_IdAndTransactionDateBetweenOrderByTransactionDateAscIdAsc(
                            orgId, bankAccountId, start, end);

        BigDecimal credits = lines.stream()
                .map(BankStatementLine::getAmount)
                .filter(Objects::nonNull)
                .filter(amount -> amount.signum() > 0)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal debits = lines.stream()
                .map(BankStatementLine::getAmount)
                .filter(Objects::nonNull)
                .filter(amount -> amount.signum() < 0)
                .map(BigDecimal::abs)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal net = credits.subtract(debits);
        long months = Math.max(1, ChronoUnit.MONTHS.between(
                YearMonth.from(start), YearMonth.from(end)) + 1);
        BigDecimal monthlyNet = net.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        BigDecimal debtServiceCapacity = monthlyNet.max(ZERO)
                .multiply(new BigDecimal("0.40"))
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bankAccountId", account == null ? null : account.getId());
        result.put("periodStart", start);
        result.put("periodEnd", end);
        result.put("transactionCount", lines.size());
        result.put("grossCredits", money(credits));
        result.put("debitExpenses", money(debits));
        result.put("netCashFlow", money(net));
        result.put("estimatedMonthlyNetCashFlow", money(monthlyNet));
        result.put("affordableDebtService", money(debtServiceCapacity));
        result.put("assumption", "Positive statement amounts are credits and negative amounts are debits; default debt-service ceiling is 40% of positive monthly net cash flow.");
        return result;
    }

    // 5. Configurable credit-policy decision foundation
    public Map<String, Object> creditPolicy(Long loanId) {
        Loan loan = organizationLoan(loanId);
        Long borrowerId = loan.getBorrower() == null ? null : loan.getBorrower().getId();
        BigDecimal exposure = borrowerId == null ? ZERO : organizationLoans().stream()
                .filter(item -> item.getBorrower() != null
                        && borrowerId.equals(item.getBorrower().getId()))
                .map(Loan::getOutstandingBalance)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        List<String> failures = new ArrayList<>();
        if (loan.getRequestedAmount() != null
                && loan.getRequestedAmount().compareTo(MAX_REQUEST) > 0) {
            failures.add("REQUESTED_AMOUNT_ABOVE_POLICY_LIMIT");
        }
        if (loan.getDebtToIncomeRatio() != null
                && loan.getDebtToIncomeRatio().compareTo(MAX_DTI) > 0) {
            failures.add("DTI_ABOVE_40_PERCENT");
        }
        if (exposure.compareTo(MAX_EXPOSURE) > 0) {
            failures.add("BORROWER_EXPOSURE_ABOVE_POLICY_LIMIT");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("decision", failures.isEmpty() ? "PASS" : "REFER");
        result.put("failures", failures);
        result.put("borrowerExistingExposure", money(exposure));
        result.put("maxRequestedAmount", MAX_REQUEST);
        result.put("maxBorrowerExposure", MAX_EXPOSURE);
        result.put("maxDtiPercent", MAX_DTI);
        return result;
    }

    // 6. Duplicate borrower / fraud checks
    public Map<String, Object> fraudCheck(Long borrowerId) {
        Borrower borrower = organizationBorrower(borrowerId);
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Borrower candidate : borrowerRepository.findByOrganization_Id(organizationId())) {
            if (Objects.equals(candidate.getId(), borrower.getId())) {
                continue;
            }
            List<String> reasons = new ArrayList<>();
            if (borrower.getPhoneHash() != null
                    && borrower.getPhoneHash().equals(candidate.getPhoneHash())) {
                reasons.add("PHONE");
            }
            if (borrower.getNationalIdHash() != null
                    && borrower.getNationalIdHash().equals(candidate.getNationalIdHash())) {
                reasons.add("NATIONAL_ID");
            }
            if (!reasons.isEmpty()) {
                Map<String, Object> match = new LinkedHashMap<>();
                match.put("borrowerId", candidate.getId());
                match.put("reasons", reasons);
                matches.add(match);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("risk", matches.isEmpty() ? "CLEAR" : "REVIEW");
        result.put("matches", matches);
        return result;
    }

    // 7 + 8. Document OCR workflow / expiry-reverification workflow
    public List<Map<String, Object>> documentStatus(Long borrowerId) {
        Borrower borrower = organizationBorrower(borrowerId);
        LocalDate today = LocalDate.now();
        return borrowerFileRepository.findByBorrowerId(borrower.getId()).stream()
                .map(file -> documentRow(file, today))
                .toList();
    }

    private Map<String, Object> documentRow(BorrowerFile file, LocalDate today) {
        LocalDate uploaded = file.getUploadedAt() == null
                ? null : file.getUploadedAt().toLocalDate();
        LocalDate reviewDue = uploaded == null ? null : uploaded.plusYears(1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fileId", file.getId());
        row.put("type", file.getDocumentType());
        row.put("verificationStatus", file.getVerificationStatus());
        row.put("uploadedAt", file.getUploadedAt());
        row.put("expired", reviewDue != null && reviewDue.isBefore(today));
        row.put("reverificationDue", reviewDue);
        return row;
    }

    // 9. Early settlement / payoff quote
    public Map<String, Object> payoff(Long loanId, LocalDate asOf) {
        Loan loan = organizationLoan(loanId);
        LocalDate quoteDate = asOf == null ? LocalDate.now() : asOf;
        BigDecimal principal = nonNegative(loan.getOutstandingBalance());
        BigDecimal interest = nonNegative(loan.getInterestOutstanding());
        BigDecimal management = nonNegative(loan.getManagementFeeOutstanding());
        BigDecimal extension = nonNegative(loan.getExtensionFeeOutstanding());
        BigDecimal penalty = nonNegative(nonNegative(loan.getPenaltiesAssessed())
                .subtract(nonNegative(loan.getPenaltiesPaid())));
        BigDecimal total = principal.add(interest).add(management).add(extension).add(penalty);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loanId", loanId);
        result.put("quoteDate", quoteDate);
        result.put("principal", money(principal));
        result.put("interest", money(interest));
        result.put("managementFee", money(management));
        result.put("extensionFee", money(extension));
        result.put("penalty", money(penalty));
        result.put("settlementAmount", money(total));
        result.put("validThrough", quoteDate.plusDays(1));
        return result;
    }

    // 10. Partial prepayment recalculation proposal
    public Map<String, Object> prepayment(Long loanId, BigDecimal amount) {
        Loan loan = organizationLoan(loanId);
        BigDecimal outstanding = nonNegative(loan.getOutstandingBalance());
        BigDecimal requested = amount == null ? outstanding : amount.max(ZERO).min(outstanding);
        BigDecimal remaining = outstanding.subtract(requested).max(ZERO);
        BigDecimal ratio = outstanding.signum() == 0
                ? ZERO : requested.divide(outstanding, 8, RoundingMode.HALF_UP);
        BigDecimal indicativeInterestReduction = nonNegative(loan.getInterestOutstanding())
                .multiply(ratio).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loanId", loanId);
        result.put("currentPrincipalOutstanding", money(outstanding));
        result.put("prepaymentAmount", money(requested));
        result.put("remainingPrincipal", money(remaining));
        result.put("indicativeInterestReduction", money(indicativeInterestReduction));
        result.put("recalculationMode", "REDUCE_PRINCIPAL_AND_RECALCULATE_SCHEDULE");
        result.put("requiresApproval", true);
        return result;
    }

    // 11 + 13 + 14 + 15 + 22 + 24: controlled workflow records
    @Transactional
    public LendingFeatureRecord createWorkflowRecord(LendingActionRequest request) {
        validateRequest(request);
        Long orgId = organizationId();
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new NoSuchElementException("Organization not found"));

        LendingFeatureRecord record = LendingFeatureRecord.builder()
                .organization(organization)
                .featureType(normalize(request.getFeatureType()))
                .status(normalizeOrDefault(request.getStatus(), "OPEN"))
                .priority(normalizeNullable(request.getPriority()))
                .amount(request.getAmount())
                .dueDate(request.getDueDate())
                .assignedUserId(request.getAssignedUserId())
                .payload(toJson(request.getPayload()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (request.getLoanId() != null) {
            record.setLoan(organizationLoan(request.getLoanId()));
        }
        if (request.getBorrowerId() != null) {
            record.setBorrower(organizationBorrower(request.getBorrowerId()));
        }
        return recordRepository.save(record);
    }

    // 12. Automated collection queues + escalation
    public Map<String, Object> collectionsQueue() {
        List<CollectionCase> cases = collectionCaseRepository.findByOrganization_Id(organizationId());
        List<Map<String, Object>> queue = cases.stream()
                .filter(item -> item.getStatus() != CollectionCase.CollectionStatus.RESOLVED)
                .filter(item -> item.getStatus() != CollectionCase.CollectionStatus.WRITTEN_OFF)
                .sorted(Comparator.comparingInt((CollectionCase item) ->
                        priorityRank(item.getPriority())).reversed())
                .map(this::collectionRow)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queue", queue);
        result.put("openCount", queue.size());
        return result;
    }

    private Map<String, Object> collectionRow(CollectionCase item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("caseId", item.getId());
        row.put("loanId", item.getLoan() == null ? null : item.getLoan().getId());
        row.put("bucket", item.getBucket());
        row.put("daysPastDue", item.getDaysPastDue());
        row.put("priority", item.getPriority());
        row.put("nextActionDate", item.getNextActionDate());
        row.put("promiseToPayDate", item.getPromiseToPayDate());
        row.put("promiseToPayAmount", item.getPromiseToPayAmountDecimal());
        return row;
    }

    private int priorityRank(CollectionCase.Priority priority) {
        if (priority == null) return CollectionCase.Priority.MEDIUM.ordinal();
        return priority.ordinal();
    }

    // 16. Loan-officer productivity
    public Map<String, Object> officerAnalytics() {
        Map<Long, List<Loan>> groups = organizationLoans().stream()
                .filter(loan -> loan.getLoanOfficer() != null)
                .collect(Collectors.groupingBy(loan -> loan.getLoanOfficer().getId()));
        List<Map<String, Object>> officers = new ArrayList<>();
        for (Map.Entry<Long, List<Loan>> entry : groups.entrySet()) {
            List<Loan> loans = entry.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("loanOfficerId", entry.getKey());
            row.put("loanCount", loans.size());
            row.put("disbursedAmount", money(sumLoans(loans,
                    loan -> loan.getDisbursedAmount() != null
                            ? loan.getDisbursedAmount() : loan.getAmount())));
            row.put("outstanding", money(sumLoans(loans, Loan::getOutstandingBalance)));
            row.put("par30Loans", loans.stream()
                    .filter(loan -> daysPastDue(loan, LocalDate.now()) >= 30).count());
            row.put("paidLoans", loans.stream()
                    .filter(loan -> loan.getStatus() == LoanStatus.PAID
                            || loan.getStatus() == LoanStatus.CLOSED).count());
            officers.add(row);
        }
        return Map.of("officers", officers);
    }

    // 17. Product/branch profitability
    public Map<String, Object> profitability() {
        Map<String, Map<String, Object>> branches = new TreeMap<>();
        for (Loan loan : organizationLoans()) {
            String key = loan.getBranch() == null ? "UNASSIGNED" : String.valueOf(loan.getBranch().getId());
            Map<String, Object> row = branches.computeIfAbsent(key, ignored -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("branchId", key);
                created.put("loanCount", 0L);
                created.put("revenue", ZERO);
                created.put("outstanding", ZERO);
                return created;
            });
            row.put("loanCount", ((Long) row.get("loanCount")) + 1L);
            BigDecimal revenue = nonNegative(loan.getInterestPaid())
                    .add(nonNegative(loan.getManagementFeePaid()))
                    .add(nonNegative(loan.getApplicationFeePaid()))
                    .add(nonNegative(loan.getExtensionFeePaid()))
                    .add(nonNegative(loan.getPenaltiesPaid()));
            row.put("revenue", money(((BigDecimal) row.get("revenue")).add(revenue)));
            row.put("outstanding", money(((BigDecimal) row.get("outstanding"))
                    .add(nonNegative(loan.getOutstandingBalance()))));
        }
        return Map.of("branches", branches.values());
    }

    // 18. Portfolio concentration limits
    public Map<String, Object> concentration() {
        List<Loan> loans = organizationLoans();
        BigDecimal total = sumLoans(loans, Loan::getOutstandingBalance);
        Map<String, BigDecimal> byProduct = new TreeMap<>();
        Map<String, BigDecimal> byBranch = new TreeMap<>();
        for (Loan loan : loans) {
            String product = loan.getLoanType() == null
                    ? "UNKNOWN" : loan.getLoanType().name();
            String branch = loan.getBranch() == null
                    ? "UNASSIGNED" : String.valueOf(loan.getBranch().getId());
            BigDecimal outstanding = nonNegative(loan.getOutstandingBalance());
            byProduct.merge(product, outstanding, BigDecimal::add);
            byBranch.merge(branch, outstanding, BigDecimal::add);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalOutstanding", money(total));
        result.put("byProduct", concentrationRows(byProduct, total));
        result.put("byBranch", concentrationRows(byBranch, total));
        result.put("alertThresholdPercent", CONCENTRATION_LIMIT);
        return result;
    }

    private List<Map<String, Object>> concentrationRows(Map<String, BigDecimal> values,
                                                         BigDecimal total) {
        return values.entrySet().stream().map(entry -> {
            BigDecimal share = percentage(entry.getValue(), total);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", entry.getKey());
            row.put("outstanding", money(entry.getValue()));
            row.put("sharePercent", share);
            row.put("concentrationAlert", share.compareTo(CONCENTRATION_LIMIT) > 0);
            return row;
        }).toList();
    }

    // 19. Stress testing
    public Map<String, Object> stress() {
        BigDecimal base = sumLoans(organizationLoans(), Loan::getOutstandingBalance);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseOutstanding", money(base));
        for (int shock : List.of(10, 20, 30)) {
            BigDecimal stressed = base.multiply(
                    BigDecimal.ONE.add(BigDecimal.valueOf(shock).divide(HUNDRED, 6, RoundingMode.HALF_UP)));
            result.put("stressPlus" + shock + "Percent", money(stressed));
        }
        result.put("interpretation", "Exposure shocks are scenario inputs and should be combined with approved PD/LGD assumptions before capital decisions.");
        return result;
    }

    // 20. Cash-flow/liquidity forecast
    public Map<String, Object> liquidity(int horizonDays) {
        int days = Math.max(1, Math.min(horizonDays <= 0 ? 30 : horizonDays, 365));
        LocalDate cutoff = LocalDate.now().plusDays(days);
        BigDecimal receivable = sumLoans(organizationLoans(), Loan::getOutstandingBalance);
        BigDecimal scheduled = organizationLoans().stream()
                .filter(loan -> loan.getNextDueDate() != null
                        && !loan.getNextDueDate().isBefore(LocalDate.now())
                        && !loan.getNextDueDate().isAfter(cutoff))
                .map(loan -> nonNegative(loan.getNextInstallmentAmount()))
                .reduce(ZERO, BigDecimal::add);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("horizonDays", days);
        result.put("grossOutstandingReceivable", money(receivable));
        result.put("expectedScheduledCollections", money(scheduled));
        result.put("method", "Forecast uses currently stored loan next-due-date and next-installment fields; treasury balances remain sourced from accounting and bank reconciliation.");
        return result;
    }

    // 21. Customer 360
    public Map<String, Object> customer360(Long borrowerId) {
        Borrower borrower = organizationBorrower(borrowerId);
        Long orgId = organizationId();
        List<Loan> loans = loanRepository.findByBorrowerIdAndOrganizationId(borrowerId, orgId);
        List<Payment> payments = paymentRepository.findByBorrowerIdAndOrganizationId(borrowerId, orgId);
        List<LendingFeatureRecord> records = recordRepository
                .findByOrganization_IdAndBorrower_IdOrderByCreatedAtDesc(orgId, borrowerId);

        List<Map<String, Object>> events = new ArrayList<>();
        for (Loan loan : loans) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "LOAN");
            event.put("date", loan.getCreatedAt());
            event.put("loanId", loan.getId());
            event.put("status", loan.getStatus());
            events.add(event);
        }
        for (Payment payment : payments) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "PAYMENT");
            event.put("date", payment.getPaidDate() != null
                    ? payment.getPaidDate() : payment.getCreatedAt());
            event.put("paymentId", payment.getId());
            event.put("amount", nonNegative(payment.getAmountPaidDecimal()));
            events.add(event);
        }
        for (LendingFeatureRecord record : records) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", record.getFeatureType());
            event.put("date", record.getCreatedAt());
            event.put("recordId", record.getId());
            event.put("status", record.getStatus());
            events.add(event);
        }
        events.sort(Comparator.comparing(
                event -> String.valueOf(event.get("date")), Comparator.reverseOrder()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("borrowerId", borrower.getId());
        result.put("name", ((borrower.getFirstName() == null ? "" : borrower.getFirstName())
                + " " + (borrower.getLastName() == null ? "" : borrower.getLastName())).trim());
        result.put("loanCount", loans.size());
        result.put("paymentCount", payments.size());
        result.put("events", events);
        return result;
    }

    // 23. Reconciliation alerts
    public Map<String, Object> reconciliationAlerts() {
        Long orgId = organizationId();
        long unmatched = bankStatementLineRepository.findAll().stream()
                .filter(line -> line.getOrganization() != null
                        && orgId.equals(line.getOrganization().getId()))
                .filter(line -> !"MATCHED".equalsIgnoreCase(line.getReconciliationStatus()))
                .count();
        long deadLetters = recordRepository.countByOrganization_IdAndFeatureTypeAndStatus(
                orgId, "WEBHOOK_DEAD_LETTER", "OPEN");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unmatchedBankStatementLines", unmatched);
        result.put("webhookDeadLetters", deadLetters);
        result.put("alert", unmatched > 0 || deadLetters > 0);
        return result;
    }

    // 25. BNR/CRB pre-submission validation, including CRB borrower identity/address fields
    public Map<String, Object> regulatoryValidation() {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (Loan loan : organizationLoans()) {
            Borrower borrower = loan.getBorrower();
            if (borrower == null) {
                addValidation(errors, loan, "BORROWER", "Borrower is missing");
                continue;
            }
            required(errors, loan, "NATIONAL_ID", borrower.getNationalId());
            required(errors, loan, "NATIONALITY", borrower.getNationality());
            required(errors, loan, "COUNTRY", borrower.getCountry());
            required(errors, loan, "PLACE_OF_BIRTH", borrower.getPlaceOfBirth());
            required(errors, loan, "PHYSICAL_ADDRESS", borrower.getAddressLine1() != null
                    ? borrower.getAddressLine1() : borrower.getAddress());
            required(errors, loan, "PHYSICAL_ADDRESS_PROVINCE",
                    borrower.getPhysicalAddressProvince() != null
                            ? borrower.getPhysicalAddressProvince()
                            : borrower.getStateProvince());
            required(errors, loan, "PHYSICAL_ADDRESS_DISTRICT", borrower.getPhysicalAddressDistrict());
            required(errors, loan, "PHYSICAL_ADDRESS_SECTOR", borrower.getPhysicalAddressSector());
            required(errors, loan, "PHYSICAL_ADDRESS_CELL", borrower.getPhysicalAddressCell());
            required(errors, loan, "ACCOUNT_NUMBER", loan.getReferenceNumber());
            required(errors, loan, "CURRENCY", loan.getCurrency());
            if (loan.getAmount() == null || loan.getAmount().signum() < 0) {
                addValidation(errors, loan, "AMOUNT", "Invalid loan amount");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", errors.isEmpty());
        result.put("errorCount", errors.size());
        result.put("errors", errors.stream().limit(500).toList());
        result.put("crbRequiredFieldsChecked", List.of(
                "Nationality", "Place Of Birth", "Physical Address Line 1",
                "Physical Address Province", "Physical Address District",
                "Physical Address Sector", "Physical Address Cell", "Country",
                "Account Number"));
        result.put("note", "Account Number is sourced from Loan.referenceNumber. CRB location fields are stored explicitly on Borrower; no regulatory value is guessed.");
        return result;
    }

    private void required(List<Map<String, Object>> errors, Loan loan,
                          String field, String value) {
        if (value == null || value.isBlank()) {
            addValidation(errors, loan, field, "Required CRB/BNR field is missing");
        }
    }

    private void addValidation(List<Map<String, Object>> errors, Loan loan,
                               String field, String message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("loanId", loan.getId());
        row.put("field", field);
        row.put("message", message);
        errors.add(row);
    }

    // 25-feature catalog
    public List<String> featureCatalog() {
        return List.of(
                "PAR1_PAR7_PAR30_PAR60_PAR90",
                "VINTAGE_COHORT_ANALYSIS",
                "IFRS9_ECL",
                "BANK_STATEMENT_AFFORDABILITY",
                "CONFIGURABLE_CREDIT_POLICY",
                "FRAUD_DUPLICATE_BORROWER",
                "DOCUMENT_OCR_WORKFLOW",
                "DOCUMENT_EXPIRY_REVERIFICATION",
                "EARLY_SETTLEMENT_PAYOFF",
                "PARTIAL_PREPAYMENT_RECALCULATION",
                "PAYMENT_REVERSAL_REFUND_WORKFLOW",
                "COLLECTION_QUEUE_ESCALATION",
                "PROMISE_TO_PAY",
                "WRITE_OFF_RECOVERY",
                "RECOVERY_AGENT_MANAGEMENT",
                "LOAN_OFFICER_PRODUCTIVITY",
                "PRODUCT_BRANCH_PROFITABILITY",
                "PORTFOLIO_CONCENTRATION_LIMITS",
                "STRESS_TESTING",
                "LIQUIDITY_FORECAST",
                "CUSTOMER_360",
                "DEVICE_BEHAVIOR_FRAUD_WORKFLOW",
                "REAL_TIME_RECONCILIATION_ALERTS",
                "PAYMENT_WEBHOOK_DEAD_LETTER_RETRY_WORKFLOW",
                "BNR_CRB_REGULATORY_VALIDATION"
        );
    }

    public List<LendingFeatureRecord> records(String type) {
        Long orgId = organizationId();
        if (type == null || type.isBlank()) {
            return recordRepository.findByOrganization_IdAndStatusOrderByCreatedAtDesc(orgId, "OPEN");
        }
        return recordRepository.findByOrganization_IdAndFeatureTypeOrderByCreatedAtDesc(
                orgId, normalize(type));
    }

    @Transactional
    public LendingFeatureRecord updateStatus(Long id, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }
        LendingFeatureRecord record = recordRepository.findById(id)
                .filter(item -> item.getOrganization() != null
                        && organizationId().equals(item.getOrganization().getId()))
                .orElseThrow(() -> new NoSuchElementException("Feature record not found"));
        record.setStatus(normalize(status));
        return recordRepository.save(record);
    }

    private List<Loan> organizationLoans() {
        return loanRepository.findByOrganization_Id(organizationId());
    }

    private Loan organizationLoan(Long loanId) {
        if (loanId == null) {
            throw new IllegalArgumentException("Loan ID is required");
        }
        return loanRepository.findById(loanId)
                .filter(loan -> loan.getOrganization() != null
                        && organizationId().equals(loan.getOrganization().getId()))
                .orElseThrow(() -> new NoSuchElementException("Loan not found"));
    }

    private Borrower organizationBorrower(Long borrowerId) {
        if (borrowerId == null) {
            throw new IllegalArgumentException("Borrower ID is required");
        }
        return borrowerRepository.findById(borrowerId)
                .filter(borrower -> borrower.getOrganization() != null
                        && organizationId().equals(borrower.getOrganization().getId()))
                .orElseThrow(() -> new NoSuchElementException("Borrower not found"));
    }

    private int daysPastDue(Loan loan, LocalDate asOf) {
        if (loan.getNextDueDate() == null || !positive(loan.getOutstandingBalance())) {
            return 0;
        }
        return Math.max(0, (int) ChronoUnit.DAYS.between(loan.getNextDueDate(), asOf));
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value == null ? ZERO : value.max(ZERO);
    }

    private BigDecimal money(BigDecimal value) {
        return nonNegative(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal amount, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.multiply(HUNDRED)
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumLoans(List<Loan> loans,
                                java.util.function.Function<Loan, BigDecimal> extractor) {
        return loans.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
    }

    private void validateRequest(LendingActionRequest request) {
        if (request == null || request.getFeatureType() == null
                || request.getFeatureType().isBlank()) {
            throw new IllegalArgumentException("Feature type is required");
        }
        if (request.getAmount() != null && request.getAmount().signum() < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : normalize(value);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : normalize(value);
    }

    private String toJson(Object payload) {
        try {
            return payload == null ? "{}" : objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid feature payload", ex);
        }
    }
}

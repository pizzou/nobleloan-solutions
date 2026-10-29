package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegacyLoanCorrectionServiceTest {
        @Mock
        LoanRepository loans;
        @Mock
        LegacyLoanCorrectionRepository corrections;
        @Mock
        AccountingService accounting;
        @Mock
        AuditService audit;
        @Mock
        EntityManager em;
        private LegacyLoanCorrectionService service;
        private Loan loan;
        private User owner;
        private User admin;
        private User manager;
        private User accountant;

        private User actor(Long id, String roleName) {
                Role r = new Role();
                r.setName(roleName);
                return User.builder().id(id).role(r).organization(loan.getOrganization()).build();
        }

        @BeforeEach
        void setup() {
                service = new LegacyLoanCorrectionService(loans, corrections, accounting, audit, new ObjectMapper(),
                                em);
                Organization org = Organization.builder().id(1L).build();
                Role role = new Role();
                role.setName("BUSINESS_OWNER");
                owner = User.builder().id(7L).role(role).organization(org).build();
                loan = Loan.builder().id(77L).organization(org).imported(true)
                                .referenceNumber("NLS-000077").amount(new BigDecimal("8000000.00"))
                                .principalPaid(new BigDecimal("6000000.00"))
                                .outstandingBalance(new BigDecimal("2000000.00"))
                                .interestPaid(BigDecimal.ZERO).interestOutstanding(BigDecimal.ZERO)
                                .managementFeePaid(BigDecimal.ZERO).managementFeeOutstanding(BigDecimal.ZERO)
                                .penaltiesAssessed(BigDecimal.ZERO).penaltiesPaid(BigDecimal.ZERO)
                                .totalPaid(new BigDecimal("6000000.00"))
                                .totalRepayable(new BigDecimal("8000000.00"))
                                .status(LoanStatus.OVERDUE).durationMonths(6).maturityDate(LocalDate.of(2026, 9, 26))
                                .startDate(LocalDate.of(2026, 3, 26)).nextDueDate(LocalDate.of(2026, 4, 26))
                                .build();
                admin = actor(8L, "ADMIN");
                manager = actor(9L, "MANAGER");
                accountant = actor(10L, "ACCOUNTANT");
                lenient().when(loans.findById(77L)).thenReturn(Optional.of(loan));
                lenient().when(loans.findByIdForUpdate(77L)).thenReturn(Optional.of(loan));
        }

        private LegacyLoanCorrectionRequest request(LocalDate due, BigDecimal principal) {
                return new LegacyLoanCorrectionRequest("NLS-000077",
                                "Verified original contract and bank statement show corrected allocation.",
                                "Evidence-Statement-2026-0001", due, principal, null, null, null, null, null, null,
                                null);
        }

        /**
         * The production guard queries four ledgers before an opening-position
         * repair. Unit tests must model an existing, queryable database result;
         * an unstubbed EntityManager mock returns null and does not represent a
         * real query execution.
         */
        private void stubFinancialActivityCount(long count) {
                jakarta.persistence.Query query = mock(jakarta.persistence.Query.class);
                when(em.createNativeQuery(anyString())).thenReturn(query);
                when(query.setParameter(anyString(), any())).thenReturn(query);
                when(query.getSingleResult()).thenReturn(count);
        }

        private void stubNoSubsequentFinancialActivity() {
                stubFinancialActivityCount(0L);
        }

        @Test
        void rejectsPrincipalPaymentsLargerThanOriginalPrincipal() {
                assertThrows(IllegalArgumentException.class, () -> service.preview(77L,
                                request(LocalDate.of(2026, 8, 31), new BigDecimal("9000000.00")), admin));
        }

        @Test
        void dueDateOnlyPreviewDoesNotAlterFinancialAmounts() {
                var p = service.preview(77L, request(LocalDate.of(2026, 8, 31), null), admin);
                assertEquals(0, p.receivableDeltas().size());
                assertEquals(p.current().get("totalPaid"), p.proposed().get("totalPaid"));
                assertEquals(p.current().get("totalRepayable"), p.proposed().get("totalRepayable"));
                assertEquals("2026-08-31", p.proposed().get("oldestUnpaidDueDate"));
        }

        @Test
        void correctsPrincipalOutstandingWhenPaymentIsVerified() {
                var p = service.preview(77L, request(LocalDate.of(2026, 8, 31), new BigDecimal("7000000.00")), manager);
                assertEquals(new BigDecimal("1000000.00"), p.proposed().get("principalOutstanding"));
                assertEquals(new BigDecimal("-1000000.00"), p.receivableDeltas().get("1100"));
        }

        @Test
        void correctionIsBlockedWhenSubsequentFinancialActivityExists() {
                var req = new LegacyLoanCorrectionRequest("NLS-000077",
                                "Signed agreement verifies a transcription mistake in the term.",
                                "Signed-Agreement-2026-03-26", null, null, null, null, null, null, null, null, 5);
                stubFinancialActivityCount(1L);
                assertThrows(IllegalStateException.class, () -> service.submit(77L, req, admin));
                verify(corrections, never()).saveAndFlush(any());
                assertEquals(6, loan.getDurationMonths());
        }

        @Test
        void correctsImportedDurationAndShowsNewMaturityWithoutChangingMoney() {
                var req = new LegacyLoanCorrectionRequest("NLS-000077",
                                "Original signed agreement says five months, not six months.",
                                "Signed-Agreement-2026-03-26", null, null, null, null, null, null, null, null, 5);
                var p = service.preview(77L, req, accountant);
                assertEquals(5, p.proposed().get("durationMonths"));
                assertEquals("2026-08-26", p.proposed().get("maturityDate"));
                assertEquals(p.current().get("totalPaid"), p.proposed().get("totalPaid"));
                assertEquals(p.current().get("totalRepayable"), p.proposed().get("totalRepayable"));
                assertTrue(p.receivableDeltas().isEmpty());
        }

        @Test
        void rejectsDurationOutsideContractualLimits() {
                var req = new LegacyLoanCorrectionRequest("NLS-000077",
                                "Signed agreement verifies a transcription mistake in the term.",
                                "Signed-Agreement-2026-03-26", null, null, null, null, null, null, null, null, 9);
                assertThrows(IllegalArgumentException.class, () -> service.preview(77L, req, admin));
        }

        @Test
        void preventsMisclassifyingRestructureAsDurationTypo() {
                loan.setStatus(LoanStatus.RESTRUCTURED);
                var req = new LegacyLoanCorrectionRequest("NLS-000077",
                                "Signed agreement verifies a transcription mistake in the term.",
                                "Signed-Agreement-2026-03-26", null, null, null, null, null, null, null, null, 5);
                assertThrows(IllegalStateException.class, () -> service.preview(77L, req, admin));
        }

        @Test
        void onlyBusinessOwnerApprovalAppliesVerifiedDurationWithoutCashPosting() {
                var req = new LegacyLoanCorrectionRequest("NLS-000077",
                                "Signed agreement shows the import was mistakenly recorded as six months.",
                                "Signed-Agreement-2026-03-26", null, null, null, null, null, null, null, null, 5);
                stubNoSubsequentFinancialActivity();
                var item = service.submit(77L, req, admin);
                assertEquals(6, loan.getDurationMonths()); // Submitting never changes the loan
                item.setId(123L);
                when(corrections.lock(1L, 77L, 123L)).thenReturn(Optional.of(item));
                var outcome = service.decide(77L, 123L, true,
                                new LegacyLoanCorrectionDecision("NLS-000077",
                                                "Compared against signed original agreement"),
                                owner);
                assertEquals("APPROVED", outcome.getStatus());
                assertEquals(5, loan.getDurationMonths());
                assertEquals(LocalDate.of(2026, 8, 26), loan.getMaturityDate());
                assertNull(outcome.getJournalEntryId());
                verify(accounting, never()).postApprovedLegacyCorrection(any(), any(), any());
        }

        @Test
        void rejectsIncorrectReference() {
                var req = new LegacyLoanCorrectionRequest("NLS-OTHER",
                                "Verified repayment entries and signed statement",
                                "Evidence-Statement-2026-0001", LocalDate.of(2026, 8, 31), null, null, null, null, null,
                                null, null, null);
                assertThrows(IllegalArgumentException.class, () -> service.preview(77L, req, accountant));
        }

        @Test
        void normalLoanCannotUseHistoricalCorrection() {
                loan.setImported(false);
                assertThrows(IllegalStateException.class,
                                () -> service.preview(77L, request(LocalDate.of(2026, 8, 31), null), admin));
        }

        @Test
        void adminManagerAndAccountantMayProposeNormalLoanCorrections() {
                for (User proposer : new User[] { admin, manager, accountant }) {
                        var result = service.preview(77L, request(LocalDate.of(2026, 8, 31), null), proposer);
                        assertEquals("2026-08-31", result.proposed().get("oldestUnpaidDueDate"));
                }
        }

        @Test
        void loanOfficerAndCreditAnalystCanRequestButNotApprove() {
                for (String role : new String[] { "LOAN_OFFICER", "CREDIT_ANALYST" }) {
                        User staff = actor(11L, role);
                        assertEquals("2026-08-31", service.preview(77L,
                                        request(LocalDate.of(2026, 8, 31), null), staff).proposed()
                                        .get("oldestUnpaidDueDate"));
                        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                        () -> service.decide(77L, 1L, true,
                                                        new LegacyLoanCorrectionDecision("NLS-000077",
                                                                        "Verified against original receipts"),
                                                        staff));
                }
        }

        @Test
        void ownerCannotSubmitNormalScopeCorrection() {
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> service.preview(77L, request(LocalDate.of(2026, 8, 31), null), owner));
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> service.submit(77L, request(LocalDate.of(2026, 8, 31), null), owner));
        }

        @Test
        void normalLoanRequesterCannotApproveOrReject() {
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> service.decide(77L, 1L, true,
                                                new LegacyLoanCorrectionDecision("NLS-000077",
                                                                "Review verified with evidence"),
                                                admin));
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> service.decide(77L, 1L, false,
                                                new LegacyLoanCorrectionDecision("NLS-000077",
                                                                "Review verified with evidence"),
                                                manager));
        }

        @Test
        void normalRequestCanBeRejectedOnlyByBusinessOwner() {
                var req = request(LocalDate.of(2026, 8, 31), null);
                var pending = service.submit(77L, req, admin);
                pending.setId(123L);
                when(corrections.lock(1L, 77L, 123L)).thenReturn(Optional.of(pending));
                var decided = service.decide(77L, 123L, false,
                                new LegacyLoanCorrectionDecision("NLS-000077",
                                                "Verified receipts do not support correction"),
                                owner);
                assertEquals("REJECTED", decided.getStatus());
                assertEquals(owner.getId(), decided.getCheckerId());
                assertNull(decided.getJournalEntryId());
        }

        @Test
        void ownerOnlyBorrowerLoanCorrectionsAreNotExposedToNonOwners() {
                loan.setBusinessOwnerOnly(true);
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> service.history(77L, admin));
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> service.preview(77L, request(LocalDate.of(2026, 8, 31), null), manager));
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> service.submit(77L, request(LocalDate.of(2026, 8, 31), null), accountant));
                assertEquals("2026-08-31", service.preview(77L,
                                request(LocalDate.of(2026, 8, 31), null), owner).proposed().get("oldestUnpaidDueDate"));
        }

        @Test
        void theSameOwnerCannotApproveTheirOwnerOnlyProposal() {
                loan.setBusinessOwnerOnly(true);
                var pending = service.submit(77L, request(LocalDate.of(2026, 8, 31), null), owner);
                pending.setId(123L);
                when(corrections.lock(1L, 77L, 123L)).thenReturn(Optional.of(pending));
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> service.decide(77L, 123L, true,
                                                new LegacyLoanCorrectionDecision("NLS-000077",
                                                                "I checked the receipt evidence"),
                                                owner));
        }

        @Test
        void theOwnerOnlyQueueIsNotVisibleToManagers() {
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> service.pendingForBusinessOwner(manager));
                when(corrections.findByOrganizationIdAndStatusOrderByCreatedAtAsc(1L, "PENDING"))
                                .thenReturn(java.util.List.of());
                assertTrue(service.pendingForBusinessOwner(owner).isEmpty());
        }

        @Test
        void stalePendingRequestCanStillBeRejectedSoLoanIsNotBlockedForever() {
                var pending = service.submit(77L, request(LocalDate.of(2026, 8, 31), null), admin);
                pending.setId(333L);
                // A separate loan update invalidated the original financial snapshot.
                loan.setNextDueDate(LocalDate.of(2026, 9, 30));
                when(corrections.lock(1L, 77L, 333L)).thenReturn(Optional.of(pending));
                var result = service.decide(77L, 333L, false,
                                new LegacyLoanCorrectionDecision("NLS-000077",
                                                "Rejecting stale correction so verified figures can be resubmitted"),
                                owner);
                assertEquals("REJECTED", result.getStatus());
                assertEquals(LocalDate.of(2026, 9, 30), loan.getNextDueDate());
                verify(accounting, never()).postApprovedLegacyCorrection(any(), any(), any());
        }

        @Test
        void correctingOnlyOldestUnpaidDateMustNotPostCashOrChangeAmounts() {
                LocalDate actualDue = LocalDate.now().minusDays(21);
                var pending = service.submit(77L, request(actualDue, null), admin);
                pending.setId(334L);
                when(corrections.lock(1L, 77L, 334L)).thenReturn(Optional.of(pending));
                stubNoSubsequentFinancialActivity();
                var result = service.decide(77L, 334L, true,
                                new LegacyLoanCorrectionDecision("NLS-000077",
                                                "Bank statement and amortization dates independently verified"),
                                owner);
                assertEquals("APPROVED", result.getStatus());
                assertEquals(21, loan.getDaysOverdue());
                assertEquals(new BigDecimal("6000000.00"), loan.getTotalPaid());
                assertEquals(new BigDecimal("8000000.00"), loan.getTotalRepayable());
                verify(accounting, never()).postApprovedLegacyCorrection(any(), any(), any());
        }
}

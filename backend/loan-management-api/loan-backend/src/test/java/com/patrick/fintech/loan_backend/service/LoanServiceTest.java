package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.LoanRequest;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.AuditLogRepository;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanProductRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

        @Mock
        LoanRepository loanRepository;

        @Mock
        OrganizationRepository organizationRepository;

        @Mock
        PaymentRepository paymentRepository;

        @Mock
        BorrowerRepository borrowerRepository;

        @Mock
        RiskScoringService riskScoringService;

        @Mock
        NotificationService notificationService;

        @Mock
        AuditLogRepository auditLogRepository;

        @Mock
        WebhookService webhookService;

        @Mock
        LoanProductRepository loanProductRepo;

        @Mock
        BorrowerFileService fileService;

        @Mock
        AuditService auditService;

        @Mock
        MailService mailService;

        @Mock
        SmsService smsService;

        @Mock
        HolidayService holidayService;

        @Mock
        CreditBureauService creditBureauService;

        @Mock
        PaymentScheduleService paymentScheduleService;

        @InjectMocks
        LoanService loanService;

        private Organization org;
        private Borrower borrower;
        private User officer;

        @BeforeEach
        void setUp() {

                org = new Organization();
                org.setId(1L);
                org.setName("TestOrg");
                org.setDefaultCurrency("USD");

                borrower = new Borrower();
                borrower.setId(1L);
                borrower.setFirstName("John");
                borrower.setLastName("Doe");
                borrower.setKycStatus("VERIFIED");
                borrower.setCreditScore(750);
                borrower.setOrganization(org);

                officer = new User();
                officer.setId(1L);
                officer.setName("Test Officer");
                officer.setOrganization(org);

                /*
                 * IMPORTANT:
                 *
                 * Do NOT globally stub HolidayService here.
                 *
                 * Mockito strict stubbing considers that unnecessary for tests
                 * which never execute a business-day calculation.
                 */
        }

        // ============================================================
        // CREATE LOAN
        // ============================================================

        @Test
        void createLoan_shouldSaveLoan_withAllFields() {

                LoanRequest req = LoanRequest.builder()
                                .borrowerId(1L)
                                .amount(new BigDecimal("500000.00"))
                                .interestRate(new BigDecimal("5.00"))
                                .interestRateType("MONTHLY")
                                .durationMonths(6)
                                .currency("USD")
                                .startDate("2026-01-01")
                                .collateralValue(new BigDecimal("15000.00"))
                                .collateralDescription("Land title")
                                .build();

                Loan savedLoan = new Loan();

                savedLoan.setId(1L);
                savedLoan.setReferenceNumber("LN-TEST-0001");
                savedLoan.setBorrower(borrower);
                savedLoan.setOrganization(org);
                savedLoan.setAmount(new BigDecimal("500000.00"));
                savedLoan.setDurationMonths(6);
                savedLoan.setInterestRate(new BigDecimal("5.00"));
                savedLoan.setStatus(LoanStatus.PENDING);

                when(
                                organizationRepository.findById(1L)).thenReturn(
                                                Optional.of(org));

                when(
                                borrowerRepository.findById(1L)).thenReturn(
                                                Optional.of(borrower));

                when(
                                loanProductRepo.findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
                                                anyLong(),
                                                any(Loan.LoanType.class)))
                                .thenReturn(
                                                Optional.empty());

                /*
                 * The createLoan implementation evaluates existing borrower
                 * facilities for DTI/exposure. Return an empty portfolio.
                 */
                when(
                                loanRepository.findByBorrowerIdAndOrganizationId(
                                                1L,
                                                1L))
                                .thenReturn(
                                                List.of());

                when(
                                loanRepository.save(any(Loan.class))).thenReturn(
                                                savedLoan);

                when(
                                riskScoringService.score(any(Loan.class))).thenReturn(
                                                new RiskScoringService.RiskResult(
                                                                80.0,
                                                                "LOW"));

                /*
                 * createLoan can calculate/adjust dates through HolidayService.
                 *
                 * Keep this stubbing local to the test that actually executes
                 * the code path.
                 */
                lenient().when(
                                holidayService.adjustToBusinessDay(
                                                anyLong(),
                                                any(LocalDate.class)))
                                .thenAnswer(
                                                invocation -> invocation.getArgument(1));

                Loan result = loanService.createLoan(
                                req,
                                1L,
                                officer);

                assertThat(result)
                                .isNotNull();

                assertThat(result.getStatus())
                                .isEqualTo(LoanStatus.PENDING);

                verify(loanRepository, atLeastOnce())
                                .save(any(Loan.class));
        }

        // ============================================================
        // BORROWER NOT FOUND
        // ============================================================

        @Test
        void createLoan_shouldThrow_whenBorrowerNotFound() {

                LoanRequest req = LoanRequest.builder()
                                .borrowerId(99L)
                                .amount(new BigDecimal("500000.00"))
                                .interestRate(new BigDecimal("5.00"))
                                .interestRateType("MONTHLY")
                                .durationMonths(6)
                                .currency("USD")
                                .startDate("2026-01-01")
                                .build();

                when(
                                organizationRepository.findById(1L)).thenReturn(
                                                Optional.of(org));

                when(
                                borrowerRepository.findById(99L)).thenReturn(
                                                Optional.empty());

                assertThatThrownBy(
                                () -> loanService.createLoan(
                                                req,
                                                1L,
                                                officer))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("Borrower not found");
        }

        // ============================================================
        // APPROVE LOAN
        // ============================================================

        @Test
        void approveLoan_shouldSetStatusApproved_andGenerateSchedule() {

                Loan loan = new Loan();

                loan.setId(1L);
                loan.setReferenceNumber("LN-TEST-0001");
                loan.setStatus(LoanStatus.PENDING);
                loan.setAmount(new BigDecimal("500000.00"));
                loan.setInterestRate(new BigDecimal("5.00"));
                loan.setManagementFeeRate(new BigDecimal("5.00"));
                loan.setProcessingFeeRate(new BigDecimal("2.00"));
                loan.setInterestRateType("MONTHLY");
                loan.setDurationMonths(6);
                loan.setStartDate(
                                LocalDate.of(2026, 1, 1));
                loan.setBorrower(borrower);
                loan.setOrganization(org);
                loan.setCurrency("USD");

                when(
                                loanRepository.findById(1L)).thenReturn(
                                                Optional.of(loan));

                when(
                                fileService.getMissingDocumentTypes(
                                                any(Long.class),
                                                any()))
                                .thenReturn(
                                                List.of());

                when(
                                loanRepository.save(any(Loan.class))).thenAnswer(
                                                invocation -> invocation.getArgument(0));

                when(
                                paymentRepository.save(any())).thenAnswer(
                                                invocation -> invocation.getArgument(0));

                when(
                                paymentRepository.findByLoanId(1L)).thenReturn(
                                                List.of());

                /*
                 * Current LoanService generates repayment dates through
                 * HolidayService.
                 */
                lenient().when(
                                holidayService.adjustToBusinessDay(
                                                anyLong(),
                                                any(LocalDate.class)))
                                .thenAnswer(
                                                invocation -> invocation.getArgument(1));

                Loan result = loanService.approveLoan(
                                1L,
                                officer,
                                null,
                                null);

                assertThat(result)
                                .isNotNull();

                assertThat(result.getStatus())
                                .isEqualTo(LoanStatus.APPROVED);

                verify(paymentRepository, times(6))
                                .save(any());
        }

        // ============================================================
        // ALREADY APPROVED
        // ============================================================

        @Test
        void approveLoan_shouldThrow_whenAlreadyApproved() {

                Loan loan = new Loan();

                loan.setId(1L);
                loan.setReferenceNumber("LN-TEST-0001");
                loan.setStatus(LoanStatus.APPROVED);
                loan.setOrganization(org);
                loan.setBorrower(borrower);

                when(
                                loanRepository.findById(1L)).thenReturn(
                                                Optional.of(loan));

                assertThatThrownBy(
                                () -> loanService.approveLoan(
                                                1L,
                                                officer,
                                                null,
                                                null))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining(
                                                "Cannot approve a loan that is APPROVED");
        }
}
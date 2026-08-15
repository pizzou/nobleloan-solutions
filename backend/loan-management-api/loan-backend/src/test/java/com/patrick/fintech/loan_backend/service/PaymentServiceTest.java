package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.event.PaymentEventPublisher;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.AuditLogRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

        @Mock
        private PaymentRepository paymentRepo;

        @Mock
        private LoanRepository loanRepo;

        @Mock
        private AuditLogRepository auditRepo;

        @Mock
        private AuditService auditService;

        @Mock
        private UserRepository userRepo;

        @Mock
        private NotificationService notifService;

        @Mock
        private MailService mailService;

        @Mock
        private SmsService smsService;

        @Mock
        private WebhookService webhookService;

        @Mock
        private AccountingService accountingService;

        @Mock
        private PaymentEventPublisher paymentEventPublisher;

        @InjectMocks
        private PaymentService paymentService;

        @Test
        void schedulePlaceholderMustStillBeFirstInterestCalculation() {

                Payment scheduled = Payment.builder()
                                .amount(
                                                new BigDecimal("100000.00"))
                                .principalComponent(
                                                new BigDecimal("80000.00"))
                                .interestComponent(
                                                new BigDecimal("20000.00"))
                                .amountPaid(
                                                BigDecimal.ZERO)
                                .cycleInterestDue(
                                                BigDecimal.ZERO)
                                .cycleInterestRemaining(
                                                BigDecimal.ZERO)
                                .paid(false)
                                .status(
                                                Payment.PaymentStatus.PENDING)
                                .build();

                assertThat(
                                paymentService.isFirstInterestCalculation(
                                                List.of(scheduled)))
                                .isTrue();
        }

        @Test
        void actualPaymentMustEndFirstInterestCalculationState() {

                Payment paid = Payment.builder()
                                .amount(
                                                new BigDecimal("100000.00"))
                                .principalComponent(
                                                new BigDecimal("80000.00"))
                                .interestComponent(
                                                new BigDecimal("20000.00"))
                                .amountPaid(
                                                new BigDecimal("100000.00"))
                                .cycleInterestDue(
                                                new BigDecimal("20000.00"))
                                .cycleInterestRemaining(
                                                BigDecimal.ZERO)
                                .paid(true)
                                .status(
                                                Payment.PaymentStatus.COMPLETED)
                                .build();

                /*
                 * A completed payment without an interestCalculationDate
                 * is still considered the first interest calculation by the
                 * current PaymentService implementation.
                 *
                 * Therefore this test must explicitly represent the state
                 * that ends the first-interest period.
                 */
                paid.setInterestCalculationDate(
                                java.time.LocalDateTime.of(
                                                2026,
                                                8,
                                                9,
                                                10,
                                                0));

                assertThat(
                                paymentService.isFirstInterestCalculation(
                                                List.of(paid)))
                                .isFalse();
        }

        @Test
        void previouslyAccruedCycleInterestMustEndFirstInterestState() {

                Payment accrued = Payment.builder()
                                .amount(
                                                new BigDecimal("100000.00"))
                                .principalComponent(
                                                new BigDecimal("80000.00"))
                                .interestComponent(
                                                new BigDecimal("20000.00"))
                                .amountPaid(
                                                BigDecimal.ZERO)
                                .cycleInterestDue(
                                                new BigDecimal("5000.00"))
                                .cycleInterestRemaining(
                                                new BigDecimal("5000.00"))
                                .paid(false)
                                .status(
                                                Payment.PaymentStatus.PENDING)
                                .build();

                /*
                 * cycleInterestDue alone does not end the first-interest
                 * state in PaymentService.
                 *
                 * The authoritative marker is interestCalculationDate.
                 */
                accrued.setInterestCalculationDate(
                                java.time.LocalDateTime.of(
                                                2026,
                                                8,
                                                9,
                                                10,
                                                0));

                assertThat(
                                paymentService.isFirstInterestCalculation(
                                                List.of(accrued)))
                                .isFalse();
        }

        @Test
        void nullPaymentListMustBeFirstInterestCalculation() {

                assertThat(
                                paymentService.isFirstInterestCalculation(null)).isTrue();
        }

        @Test
        void emptyPaymentListMustBeFirstInterestCalculation() {

                assertThat(
                                paymentService.isFirstInterestCalculation(
                                                List.of()))
                                .isTrue();
        }

        @Test
        void paymentsWithoutInterestCalculationDateMustRemainFirstInterestCalculation() {

                Payment first = Payment.builder()
                                .amount(
                                                new BigDecimal("100000.00"))
                                .amountPaid(
                                                new BigDecimal("20000.00"))
                                .principalComponent(
                                                new BigDecimal("16000.00"))
                                .interestComponent(
                                                new BigDecimal("4000.00"))
                                .cycleInterestDue(
                                                BigDecimal.ZERO)
                                .cycleInterestRemaining(
                                                BigDecimal.ZERO)
                                .paid(false)
                                .status(
                                                Payment.PaymentStatus.PARTIALLY_PAID)
                                .build();

                Payment second = Payment.builder()
                                .amount(
                                                new BigDecimal("100000.00"))
                                .amountPaid(
                                                BigDecimal.ZERO)
                                .principalComponent(
                                                BigDecimal.ZERO)
                                .interestComponent(
                                                BigDecimal.ZERO)
                                .cycleInterestDue(
                                                BigDecimal.ZERO)
                                .cycleInterestRemaining(
                                                BigDecimal.ZERO)
                                .paid(false)
                                .status(
                                                Payment.PaymentStatus.PENDING)
                                .build();

                assertThat(
                                paymentService.isFirstInterestCalculation(
                                                List.of(
                                                                first,
                                                                second)))
                                .isTrue();
        }

        @Test
        void onePaymentWithInterestCalculationDateMustEndFirstInterestCalculationState() {

                Payment scheduled = Payment.builder()
                                .amount(
                                                new BigDecimal("100000.00"))
                                .amountPaid(
                                                BigDecimal.ZERO)
                                .principalComponent(
                                                BigDecimal.ZERO)
                                .interestComponent(
                                                BigDecimal.ZERO)
                                .cycleInterestDue(
                                                BigDecimal.ZERO)
                                .cycleInterestRemaining(
                                                BigDecimal.ZERO)
                                .paid(false)
                                .status(
                                                Payment.PaymentStatus.PENDING)
                                .build();

                scheduled.setInterestCalculationDate(
                                java.time.LocalDateTime.of(
                                                2026,
                                                8,
                                                9,
                                                10,
                                                0));

                assertThat(
                                paymentService.isFirstInterestCalculation(
                                                List.of(scheduled)))
                                .isFalse();
        }

        @Test
        void nullPaymentsInsideListMustBeIgnored() {

                Payment scheduled = Payment.builder()
                                .amount(
                                                new BigDecimal("100000.00"))
                                .amountPaid(
                                                BigDecimal.ZERO)
                                .principalComponent(
                                                BigDecimal.ZERO)
                                .interestComponent(
                                                BigDecimal.ZERO)
                                .cycleInterestDue(
                                                BigDecimal.ZERO)
                                .cycleInterestRemaining(
                                                BigDecimal.ZERO)
                                .paid(false)
                                .status(
                                                Payment.PaymentStatus.PENDING)
                                .build();

                assertThat(
                                paymentService.isFirstInterestCalculation(
                                                Arrays.asList(
                                                                null,
                                                                scheduled,
                                                                null)))
                                .isTrue();
        }

        @Test
        void nullPaymentsBeforeRecordedInterestCalculationMustStillEndFirstInterestState() {

                Payment payment = Payment.builder()
                                .amount(
                                                new BigDecimal("100000.00"))
                                .amountPaid(
                                                new BigDecimal("10000.00"))
                                .principalComponent(
                                                new BigDecimal("8000.00"))
                                .interestComponent(
                                                new BigDecimal("2000.00"))
                                .cycleInterestDue(
                                                new BigDecimal("2000.00"))
                                .cycleInterestRemaining(
                                                BigDecimal.ZERO)
                                .paid(false)
                                .status(
                                                Payment.PaymentStatus.PARTIALLY_PAID)
                                .build();

                payment.setInterestCalculationDate(
                                java.time.LocalDateTime.of(
                                                2026,
                                                8,
                                                9,
                                                10,
                                                0));

                assertThat(
                                paymentService.isFirstInterestCalculation(
                                                Arrays.asList(
                                                                null,
                                                                payment,
                                                                null)))
                                .isFalse();
        }
}
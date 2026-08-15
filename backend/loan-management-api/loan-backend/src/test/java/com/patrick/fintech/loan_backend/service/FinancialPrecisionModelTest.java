package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FinancialPrecisionModelTest {

        @Test
        void borrowerStoresFinancialValuesWithoutPrecisionLoss() {
                Borrower borrower = new Borrower();

                BigDecimal monthlyIncome = new BigDecimal("123456.789123");

                BigDecimal monthlyExpenses = new BigDecimal("45678.123456");

                BigDecimal netWorth = new BigDecimal("9876543.123456");

                borrower.setMonthlyIncome(monthlyIncome);
                borrower.setMonthlyExpenses(monthlyExpenses);
                borrower.setNetWorth(netWorth);

                assertEquals(
                                monthlyIncome,
                                borrower.getMonthlyIncomeDecimal());

                assertEquals(
                                monthlyExpenses,
                                borrower.getMonthlyExpensesDecimal());

                assertEquals(
                                netWorth,
                                borrower.getNetWorthDecimal());
        }

        @Test
        void loanStoresRatesAndAmountsAsBigDecimal() {
                Loan loan = new Loan();

                BigDecimal amount = new BigDecimal("100000.123456");

                BigDecimal interestRate = new BigDecimal("12.345678901");

                BigDecimal outstandingBalance = new BigDecimal("99999.654321");

                loan.setAmount(amount);
                loan.setInterestRate(interestRate);
                loan.setOutstandingBalance(outstandingBalance);

                assertEquals(
                                new BigDecimal("100000.12"),
                                loan.getAmountDecimal());

                assertEquals(
                                new BigDecimal("12.345678901"),
                                loan.getInterestRateDecimal());

                assertEquals(
                                new BigDecimal("99999.65"),
                                loan.getOutstandingBalanceDecimal());
        }

        @Test
        void paymentStoresAmountAsBigDecimal() {
                Payment payment = new Payment();

                BigDecimal amount = new BigDecimal("1250.55");

                payment.setAmount(amount);

                assertEquals(
                                amount,
                                payment.getAmountDecimal());

                assertEquals(
                                amount,
                                payment.getAmount());
        }

        @Test
        void nullBorrowerFinancialValuesRemainNull() {
                Borrower borrower = new Borrower();

                assertNull(
                                borrower.getMonthlyIncome());

                assertNull(
                                borrower.getMonthlyExpenses());

                assertNull(
                                borrower.getNetWorth());
        }

        @Test
        void nullLoanFinancialValuesRemainNull() {
                Loan loan = new Loan();

                assertNull(
                                loan.getAmountDecimal());

                assertEquals(
                                Loan.DEFAULT_MONTHLY_INTEREST_RATE,
                                loan.getInterestRateDecimal());

                assertEquals(
                                BigDecimal.ZERO,
                                loan.getOutstandingBalanceDecimal());
        }

        @Test
        void nullPaymentAmountRemainsNull() {
                Payment payment = new Payment();

                assertEquals(
                                BigDecimal.ZERO.setScale(2),
                                payment.getAmountDecimal());
        }

        @Test
        void bigDecimalValuesPreserveExactScaleAndValue() {
                Loan loan = new Loan();

                BigDecimal original = new BigDecimal("100000.123456789");

                loan.setAmount(original);

                assertEquals(
                                new BigDecimal("100000.12"),
                                loan.getAmountDecimal());

                assertEquals(
                                2,
                                loan.getAmountDecimal().scale());
        }

        @Test
        void loanLegacyDoubleBoundaryIsSeparateFromStoredBigDecimal() {
                Loan loan = new Loan();

                BigDecimal original = new BigDecimal("100000.123456");

                loan.setAmount(original);

                assertEquals(
                                new BigDecimal("100000.12"),
                                loan.getAmountDecimal());

                assertEquals(
                                original.doubleValue(),
                                loan.getAmountDouble(),
                                0.0000000001d);
        }

        @Test
        void paymentLegacyDoubleBoundaryIsSeparateFromStoredBigDecimal() {
                Payment payment = new Payment();

                BigDecimal original = new BigDecimal("1250.55");

                payment.setAmount(original);

                assertEquals(
                                original,
                                payment.getAmountDecimal());

                assertEquals(
                                original.doubleValue(),
                                payment.getAmountDouble(),
                                0.0000000001d);
        }
}
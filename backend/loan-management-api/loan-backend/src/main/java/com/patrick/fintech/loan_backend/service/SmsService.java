package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@Slf4j
public class SmsService {

    private final RestTemplate restTemplate =
            new RestTemplate();

    // ================================================================
    // AFRICA'S TALKING
    // ================================================================

    @Value("${app.sms.africas-talking.api-key:}")
    private String atApiKey;

    @Value("${app.sms.africas-talking.username:sandbox}")
    private String atUsername;

    @Value("${app.sms.africas-talking.sender-id:LoanSaaS}")
    private String atSenderId;

    // ================================================================
    // TWILIO
    // ================================================================

    @Value("${app.sms.twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${app.sms.twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${app.sms.twilio.from-number:}")
    private String twilioFromNumber;

    // ================================================================
    // GENERAL CONFIG
    // ================================================================

    @Value("${app.sms.enabled:false}")
    private boolean smsEnabled;

    // ================================================================
    // PLATFORM RULES
    // ================================================================

    private static final BigDecimal PROCESSING_FEE_RATE =
            new BigDecimal("2.00");

    private static final BigDecimal MONTHLY_PENALTY_RATE =
            new BigDecimal("15.00");

    // ================================================================
    // LOAN APPROVED
    // ================================================================

    @Async
    public void sendLoanApproved(
            Loan loan
    ) {

        String phone =
                phone(loan);

        if (phone == null) {
            return;
        }

        BigDecimal grossAmount =
                money(
                        loan != null
                                ? loan.getAmountDecimal()
                                : null
                );

        BigDecimal processingFee =
                money(
                        loan != null
                                ? loan.getProcessingFeeDecimal()
                                : null
                );

        BigDecimal expectedNetDisbursement =
                grossAmount
                        .subtract(
                                processingFee
                        )
                        .max(
                                BigDecimal.ZERO
                        );

        String currency =
                value(
                        loan != null
                                ? loan.getCurrency()
                                : "RWF"
                );

        send(
                phone,
                String.format(
                        Locale.ROOT,
                        "Congratulations! Loan %s approved. "
                                + "Gross amount: %s %s. "
                                + "Processing fee (2%%): %s %s. "
                                + "Net amount to be disbursed: %s %s. "
                                + "-%s",

                        value(
                                loan != null
                                        ? loan.getReferenceNumber()
                                        : null
                        ),

                        currency,
                        formatMoney(grossAmount),

                        currency,
                        formatMoney(processingFee),

                        currency,
                        formatMoney(
                                expectedNetDisbursement
                        ),

                        orgName(loan)
                )
        );
    }

    // ================================================================
    // LOAN REJECTED
    // ================================================================

    @Async
    public void sendLoanRejected(
            Loan loan
    ) {

        String phone =
                phone(loan);

        if (phone == null) {
            return;
        }

        send(
                phone,
                String.format(
                        Locale.ROOT,
                        "Your application for loan %s was not approved "
                                + "at this time. Contact us for details. -%s",

                        value(
                                loan != null
                                        ? loan.getReferenceNumber()
                                        : null
                        ),

                        orgName(loan)
                )
        );
    }

    // ================================================================
    // LOAN DISBURSED
    // ================================================================

    @Async
    public void sendLoanDisbursed(
            Loan loan,
            String method
    ) {

        String phone =
                phone(loan);

        if (phone == null) {
            return;
        }

        BigDecimal grossAmount =
                money(
                        loan != null
                                ? loan.getAmountDecimal()
                                : null
                );

        BigDecimal processingFee =
                money(
                        loan != null
                                ? loan.getProcessingFeeDecimal()
                                : null
                );

        /*
         * Platform rule:
         *
         * Gross loan principal remains the basis for interest.
         *
         * Processing fee is deducted once from the gross amount
         * before the borrower receives the funds.
         */
        BigDecimal netDisbursedAmount =
                grossAmount
                        .subtract(
                                processingFee
                        )
                        .max(
                                BigDecimal.ZERO
                        );

        String currency =
                value(
                        loan != null
                                ? loan.getCurrency()
                                : "RWF"
                );

        String reference =
                value(
                        loan != null
                                ? loan.getReferenceNumber()
                                : null
                );

        String normalizedMethod =
                method != null
                        && !method.isBlank()
                        ? method.trim()
                        : "unspecified";

        send(
                phone,
                String.format(
                        Locale.ROOT,

                        "Loan %s has been disbursed. "
                                + "Gross principal: %s %s. "
                                + "Processing fee (2%%): %s %s. "
                                + "Net amount received: %s %s "
                                + "via %s. "
                                + "Interest remains calculated on the "
                                + "gross principal. -%s",

                        reference,

                        currency,
                        formatMoney(grossAmount),

                        currency,
                        formatMoney(processingFee),

                        currency,
                        formatMoney(netDisbursedAmount),

                        normalizedMethod,

                        orgName(loan)
                )
        );
    }

    // ================================================================
    // PAYMENT DUE
    // ================================================================

    @Async
    public void sendPaymentDue(
            Loan loan,
            double amount,
            String dueDate
    ) {

        String phone =
                phone(loan);

        if (phone == null) {
            return;
        }

        send(
                phone,
                String.format(
                        Locale.ROOT,

                        "REMINDER: Payment of %s %s is due %s "
                                + "for loan %s. "
                                + "Overdue penalty is 15%% per month, "
                                + "calculated daily. "
                                + "Please pay on time. -%s",

                        value(
                                loan != null
                                        ? loan.getCurrency()
                                        : "RWF"
                        ),

                        formatMoney(amount),

                        value(dueDate),

                        value(
                                loan != null
                                        ? loan.getReferenceNumber()
                                        : null
                        ),

                        orgName(loan)
                )
        );
    }

    // ================================================================
    // PAYMENT CONFIRMED
    // ================================================================

    @Async
    public void sendPaymentConfirmed(
            Loan loan,
            double amount
    ) {

        String phone =
                phone(loan);

        if (phone == null) {
            return;
        }

        BigDecimal outstandingBalance =
                money(
                        loan != null
                                ? loan.getOutstandingBalanceDecimal()
                                : null
                );

        String currency =
                value(
                        loan != null
                                ? loan.getCurrency()
                                : "RWF"
                );

        send(
                phone,
                String.format(
                        Locale.ROOT,

                        "Payment of %s %s received for loan %s. "
                                + "Remaining principal balance: %s %s. "
                                + "-%s",

                        currency,
                        formatMoney(amount),

                        value(
                                loan != null
                                        ? loan.getReferenceNumber()
                                        : null
                        ),

                        currency,
                        formatMoney(
                                outstandingBalance
                        ),

                        orgName(loan)
                )
        );
    }

    // ================================================================
    // LOAN OVERDUE
    // ================================================================

    @Async
    public void sendLoanOverdue(
            Loan loan,
            int days
    ) {

        String phone =
                phone(loan);

        if (phone == null) {
            return;
        }

        BigDecimal outstandingBalance =
                money(
                        loan != null
                                ? loan.getOutstandingBalanceDecimal()
                                : null
                );

        String currency =
                value(
                        loan != null
                                ? loan.getCurrency()
                                : "RWF"
                );

        send(
                phone,
                String.format(
                        Locale.ROOT,

                        "URGENT: Loan %s is %d day(s) overdue. "
                                + "Outstanding principal: %s %s. "
                                + "Penalty: 15%% per month, calculated "
                                + "daily on overdue exposure. "
                                + "Please contact us immediately. -%s",

                        value(
                                loan != null
                                        ? loan.getReferenceNumber()
                                        : null
                        ),

                        Math.max(
                                0,
                                days
                        ),

                        currency,

                        formatMoney(
                                outstandingBalance
                        ),

                        orgName(loan)
                )
        );
    }

    // ================================================================
    // CUSTOM SMS
    // ================================================================

    @Async
    public void sendCustom(
            String phone,
            String message
    ) {

        send(
                phone,
                message
        );
    }

    // ================================================================
    // SEND
    // ================================================================

    private void send(
            String to,
            String msg
    ) {

        String normalized =
                normalizePhone(to);

        if (normalized == null) {

            log.warn(
                    "Skipping SMS - invalid phone number"
            );

            return;
        }

        if (msg == null
                || msg.isBlank()) {

            log.warn(
                    "Skipping SMS - message is empty"
            );

            return;
        }

        if (!smsEnabled) {

            log.info(
                    "[SMS SIMULATION] {} -> {}",
                    normalized,
                    msg
            );

            return;
        }

        if (
                trySendAT(
                        normalized,
                        msg
                )
        ) {
            return;
        }

        if (
                trySendTwilio(
                        normalized,
                        msg
                )
        ) {
            return;
        }

        log.warn(
                "All SMS providers failed for {}",
                normalized
        );
    }

    // ================================================================
    // PHONE NORMALIZATION
    // ================================================================

    private String normalizePhone(
            String raw
    ) {

        if (
                raw == null
                        || raw.isBlank()
        ) {

            return null;
        }

        String cleaned =
                raw.trim();

        boolean hasPlus =
                cleaned.startsWith("+");

        String digits =
                cleaned.replaceAll(
                        "[^0-9]",
                        ""
                );

        if (digits.isEmpty()) {
            return null;
        }

        if (hasPlus) {
            return "+" + digits;
        }

        if (
                digits.startsWith(
                        "250"
                )
        ) {
            return "+" + digits;
        }

        if (
                digits.startsWith(
                        "0"
                )
        ) {

            String national =
                    digits.substring(
                            1
                    );

            if (national.isBlank()) {
                return null;
            }

            return "+250" + national;
        }

        return "+250" + digits;
    }

    // ================================================================
    // AFRICA'S TALKING
    // ================================================================

    private boolean trySendAT(
            String to,
            String msg
    ) {

        if (
                atApiKey == null
                        || atApiKey.isBlank()
        ) {

            return false;
        }

        if (
                atUsername == null
                        || atUsername.isBlank()
        ) {

            log.warn(
                    "Africa's Talking SMS skipped because username "
                            + "is not configured"
            );

            return false;
        }

        try {

            HttpHeaders headers =
                    new HttpHeaders();

            headers.set(
                    "apiKey",
                    atApiKey
            );

            headers.setContentType(
                    MediaType.APPLICATION_FORM_URLENCODED
            );

            StringBuilder body =
                    new StringBuilder();

            body.append(
                    "username="
            );

            body.append(
                    enc(atUsername)
            );

            body.append(
                    "&to="
            );

            body.append(
                    enc(to)
            );

            body.append(
                    "&message="
            );

            body.append(
                    enc(msg)
            );

            if (
                    atSenderId != null
                            && !atSenderId.isBlank()
            ) {

                body.append(
                        "&from="
                );

                body.append(
                        enc(atSenderId)
                );
            }

            HttpEntity<String> request =
                    new HttpEntity<>(
                            body.toString(),
                            headers
                    );

            restTemplate.exchange(
                    "https://api.africastalking.com/version1/messaging",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info(
                    "SMS sent via Africa's Talking"
            );

            return true;

        } catch (Exception e) {

            log.warn(
                    "Africa's Talking SMS failed: {}",
                    e.getMessage()
            );

            return false;
        }
    }

    // ================================================================
    // TWILIO
    // ================================================================

    private boolean trySendTwilio(
            String to,
            String msg
    ) {

        if (
                twilioAccountSid == null
                        || twilioAccountSid.isBlank()
        ) {

            return false;
        }

        if (
                twilioAuthToken == null
                        || twilioAuthToken.isBlank()
        ) {

            log.warn(
                    "Twilio SMS skipped because auth token "
                            + "is not configured"
            );

            return false;
        }

        if (
                twilioFromNumber == null
                        || twilioFromNumber.isBlank()
        ) {

            log.warn(
                    "Twilio SMS skipped because from-number "
                            + "is not configured"
            );

            return false;
        }

        try {

            String url =
                    "https://api.twilio.com/2010-04-01/Accounts/"
                            + twilioAccountSid
                            + "/Messages.json";

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setBasicAuth(
                    twilioAccountSid,
                    twilioAuthToken
            );

            headers.setContentType(
                    MediaType.APPLICATION_FORM_URLENCODED
            );

            String body =
                    "To="
                            + enc(to)
                            + "&From="
                            + enc(twilioFromNumber)
                            + "&Body="
                            + enc(msg);

            HttpEntity<String> request =
                    new HttpEntity<>(
                            body,
                            headers
                    );

            restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info(
                    "SMS sent via Twilio successfully"
            );

            return true;

        } catch (Exception e) {

            log.warn(
                    "Twilio SMS failed: {}",
                    e.getMessage()
            );

            return false;
        }
    }

    // ================================================================
    // URL ENCODING
    // ================================================================

    private String enc(
            String value
    ) {

        if (value == null) {
            return "";
        }

        try {

            return URLEncoder.encode(
                    value,
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            log.warn(
                    "Unable to URL encode SMS value",
                    e
            );

            return value;
        }
    }

    // ================================================================
    // MONEY HELPERS
    // ================================================================

    private BigDecimal money(
            BigDecimal value
    ) {

        if (value == null) {

            return BigDecimal.ZERO
                    .setScale(
                            2,
                            RoundingMode.HALF_UP
                    );
        }

        return value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private String formatMoney(
            BigDecimal value
    ) {

        return String.format(
                Locale.ROOT,
                "%,.2f",
                money(value)
        );
    }

    private String formatMoney(
            double value
    ) {

        if (
                Double.isNaN(value)
                        || Double.isInfinite(value)
        ) {

            value = 0.0;
        }

        return String.format(
                Locale.ROOT,
                "%,.2f",
                value
        );
    }

    // ================================================================
    // SAFE STRING
    // ================================================================

    private String value(
            String value
    ) {

        return value == null
                || value.isBlank()
                ? ""
                : value.trim();
    }

    // ================================================================
    // BORROWER PHONE
    // ================================================================

    private String phone(
            Loan loan
    ) {

        if (loan == null
                || loan.getBorrower() == null) {

            return null;
        }

        String borrowerPhone =
                loan.getBorrower().getPhone();

        if (
                borrowerPhone == null
                        || borrowerPhone.isBlank()
        ) {

            return null;
        }

        return borrowerPhone.trim();
    }

    // ================================================================
    // ORGANIZATION NAME
    // ================================================================

    private String orgName(
            Loan loan
    ) {

        if (
                loan == null
                        || loan.getOrganization() == null
        ) {

            return "LoanSaaS";
        }

        String name =
                loan.getOrganization().getName();

        return name == null
                || name.isBlank()
                ? "LoanSaaS"
                : name.trim();
    }
}
package com.patrick.fintech.loan_backend.util;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Establishes a contractual due date for a historical monthly loan without
 * pretending that aggregated payments prove an installment was fully settled.
 *
 * Input dates are normalized ISO-8601 strings. A verified date can be supplied
 * in AC (compact workbook) / AE (full workbook). Alternatively the importer
 * accepts a verified count of completely settled ORIGINAL installments in
 * AD (compact workbook) / AF (full workbook).
 *
 * This resolver does not change money, penalties or loan status. The import
 * service classifies arrears against its import-as-of date.
 */
public final class LegacyInstallmentDueDateResolver {
    private LegacyInstallmentDueDateResolver() {}

    public record Result(String nextDueDate, String error) {
        public boolean valid() { return error == null; }
    }

    public static Result resolve(String startDate, String firstDueDate, String durationMonths,
            String verifiedOldestUnpaidDueDate, String verifiedPaidInstallments,
            boolean partiallyPaid, boolean hasOutstanding, String dateColumn, String countColumn) {
        LocalDate start = date(startDate);
        LocalDate first = date(firstDueDate);
        LocalDate verified = date(verifiedOldestUnpaidDueDate);
        if (verifiedOldestUnpaidDueDate != null && !verifiedOldestUnpaidDueDate.isBlank() && verified == null) {
            return problem("Invalid " + dateColumn + " date. Enter a real YYYY-MM-DD contractual due date.");
        }
        if (start != null && verified != null && !verified.isAfter(start)) {
            return problem(dateColumn + " must be later than the original disbursement date. "
                    + "Do not record a due date before the loan existed.");
        }
        if (verified != null && first != null && verified.isBefore(first)) {
            return problem(dateColumn + " precedes the first contractual installment date (column P). "
                    + "Correct the schedule or document the rescheduling before importing.");
        }
        String countText = verifiedPaidInstallments == null ? "" : verifiedPaidInstallments.trim();
        if (!countText.isBlank()) {
            if (!countText.matches("[0-9]{1,3}")) {
                return problem(countColumn + " must be the verified whole number of fully settled installments, not an amount.");
            }
            int count = Integer.parseInt(countText);
            int duration;
            try {
                duration = Integer.parseInt(durationMonths == null ? "" : durationMonths.trim());
            } catch (NumberFormatException e) {
                return problem("A valid loan duration is needed to calculate the unpaid installment from " + countColumn + ".");
            }
            if (duration < 1 || duration > 120 || count >= duration) {
                return problem(countColumn + " must be less than the contractual loan duration while an unpaid installment remains.");
            }
            if (start == null || first == null || !first.isAfter(start)) {
                return problem("Original disbursement and first due dates (O/P) must be valid before "
                        + countColumn + " can determine the next unpaid installment.");
            }
            LocalDate computed = first.plusMonths(count);
            if (verified != null && !verified.equals(computed)) {
                return problem("The dates disagree: " + countColumn + "=" + count + " implies " + computed
                        + ", but " + dateColumn + "=" + verified + ". Confirm payment allocation or restructuring.");
            }
            return new Result(computed.toString(), null);
        }
        if (verified != null) {
            return new Result(verified.toString(), null);
        }
        if (hasOutstanding && partiallyPaid) {
            return problem("Historical payments and outstanding balances exist, but they do not prove which "
                    + "installments were settled. Enter the verified oldest unpaid due date in " + dateColumn
                    + " OR the verified number of fully settled installments in " + countColumn
                    + ". Column P is only the first scheduled due date. Never infer installments from principal paid alone.");
        }
        if (hasOutstanding && start != null && first != null && !first.isAfter(start)) {
            return problem("First due date (P) must be later than disbursement (O). Supply the correct contractual schedule.");
        }
        return new Result(firstDueDate == null ? "" : firstDueDate, null);
    }

    private static LocalDate date(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return LocalDate.parse(raw.trim()); }
        catch (DateTimeParseException e) { return null; }
    }

    private static Result problem(String reason) { return new Result("", reason); }
}

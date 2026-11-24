import type { Loan } from "@/types";

/** Contract maturity is not the same as an unpaid installment. */
type LoanDates = Pick<Loan, "maturityDate" | "disbursedAt" | "durationMonths" | "imported" | "status" | "contractDaysOverdue" | "contractMaturityVerified">;

function isoDate(input: unknown): string | null {
  if (typeof input !== "string") return null;
  const value = input.slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const parsed = new Date(value + "T00:00:00Z");
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
    ? value : null;
}

function addCalendarMonths(start: string, months: number): string {
  const [year, month, day] = start.split("-").map(Number);
  const base = new Date(Date.UTC(year, month - 1 + months, 1));
  const last = new Date(Date.UTC(base.getUTCFullYear(), base.getUTCMonth() + 1, 0)).getUTCDate();
  base.setUTCDate(Math.min(day, last));
  return base.toISOString().slice(0, 10);
}

/** Imported records whose contract fields conflict require verification. */
export function verifiedContractMaturity(loan: LoanDates): string | null {
  if (loan.contractMaturityVerified === false) return null;
  const saved = isoDate(loan.maturityDate);
  const disbursed = isoDate(loan.disbursedAt);
  const duration = Number(loan.durationMonths);
  if (loan.imported) {
    if (!saved) return null;
    if (disbursed && Number.isInteger(duration) && duration > 0
        && addCalendarMonths(disbursed, duration) !== saved) return null;
    return saved;
  }
  if (saved) return saved;
  return disbursed && Number.isInteger(duration) && duration > 0
    ? addCalendarMonths(disbursed, duration) : null;
}

/**
 * UI-only contract days. Do not replace stored regulatory installment DPD,
 * regulatory classification, historical statuses or accounting data.
 */
export function contractDaysOverdue(loan: LoanDates, asOf = new Date()): number | null {
  // Prefer the authoritative backend metric whenever the API supplies it.
  if (loan.contractMaturityVerified === false) return null;
  if (loan.contractDaysOverdue != null &&
      Number.isFinite(loan.contractDaysOverdue)) {
    return Math.max(0, loan.contractDaysOverdue);
  }
  const maturity = verifiedContractMaturity(loan);
  if (!maturity) return null;
  if (["PAID", "CLOSED", "CANCELLED", "REJECTED", "PENDING", "APPROVED", "UNDER_REVIEW"]
    .includes(String(loan.status))) return 0;
  const today = Date.UTC(asOf.getFullYear(), asOf.getMonth(), asOf.getDate());
  return Math.max(0, Math.floor((today - Date.parse(maturity + "T00:00:00Z")) / 86400000));
}

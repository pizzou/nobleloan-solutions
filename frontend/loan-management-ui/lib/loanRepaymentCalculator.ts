/**
 * Exact client-side mirror of the backend contractual schedule calculation.
 *
 * Monetary values are calculated as integer cents and rates use the same
 * nine-decimal precision as the database. This keeps the public estimate
 * deterministic and prevents JavaScript floating-point drift.
 */

export type RateValue = number | string | null | undefined;

function parseDecimal(value: RateValue, scale: bigint): bigint {
  const text = String(value ?? "0").trim().replace(/,/g, "");

  if (!/^\d+(?:\.\d+)?$/.test(text)) {
    return 0n;
  }

  const [whole, fraction = ""] = text.split(".");
  const scaleDigits = Number(scale);
  const digits = fraction.padEnd(scaleDigits, "0").slice(0, scaleDigits);

  return BigInt(whole) * 10n ** scale + BigInt(digits || "0");
}

function halfUpDivide(numerator: bigint, denominator: bigint): bigint {
  if (denominator <= 0n) {
    throw new Error("Invalid financial denominator");
  }

  const quotient = numerator / denominator;
  const remainder = numerator % denominator;

  return remainder * 2n >= denominator ? quotient + 1n : quotient;
}

function centsToNumber(cents: bigint): number {
  return Number(cents) / 100;
}

function safeNumber(value: unknown, fallback: number): number {
  if (value === null || value === undefined || value === "") {
    return fallback;
  }

  const parsed = Number(String(value).replace(/,/g, "").trim());

  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

export function percentageCharge(
  principal: number,
  rate: RateValue,
): number {
  const principalAmount = safeNumber(principal, 0);
  const principalCents = BigInt(Math.max(0, Math.round(principalAmount * 100)));
  const rateScale = 9n;
  const rateUnits = parseDecimal(rate, rateScale);
  const denominator = 100n * 10n ** rateScale;

  return centsToNumber(
    halfUpDivide(principalCents * rateUnits, denominator),
  );
}

export function calculateContractualSchedule(
  principal: number,
  months: number,
  interestRate: RateValue,
  managementRate: RateValue,
) {
  const principalAmount = safeNumber(principal, 0);
  const installmentCount = Math.max(1, Math.trunc(safeNumber(months, 1)));
  const principalCents = BigInt(Math.max(0, Math.round(principalAmount * 100)));
  const rateScale = 9n;
  const rateDenominator = 100n * 10n ** rateScale;
  const interestRateUnits = parseDecimal(interestRate, rateScale);
  const managementRateUnits = parseDecimal(managementRate, rateScale);

  let balanceCents = principalCents;
  let totalInterestCents = 0n;
  let totalManagementCents = 0n;
  let firstInstallmentCents = 0n;
  let lastInstallmentCents = 0n;

  for (
    let installmentNumber = 1;
    installmentNumber <= installmentCount;
    installmentNumber += 1
  ) {
    const remainingInstallments =
      installmentCount - installmentNumber + 1;

    const principalComponentCents =
      remainingInstallments === 1
        ? balanceCents
        : halfUpDivide(balanceCents, BigInt(remainingInstallments));

    const interestCents = halfUpDivide(
      balanceCents * interestRateUnits,
      rateDenominator,
    );

    const managementCents = halfUpDivide(
      balanceCents * managementRateUnits,
      rateDenominator,
    );

    const installmentCents =
      principalComponentCents + interestCents + managementCents;

    totalInterestCents += interestCents;
    totalManagementCents += managementCents;

    if (installmentNumber === 1) {
      firstInstallmentCents = installmentCents;
    }

    if (installmentNumber === installmentCount) {
      lastInstallmentCents = installmentCents;
    }

    balanceCents -= principalComponentCents;
  }

  return {
    interest: centsToNumber(totalInterestCents),
    management: centsToNumber(totalManagementCents),
    total: centsToNumber(
      principalCents + totalInterestCents + totalManagementCents,
    ),
    firstInstallment: centsToNumber(firstInstallmentCents),
    lastInstallment: centsToNumber(lastInstallmentCents),
  };
}

export function safeRate(value: RateValue, fallback: number): number {
  return safeNumber(value, fallback);
}

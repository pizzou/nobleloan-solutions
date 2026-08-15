"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import type { TenantConfig, TenantService } from "../app/(site)/layout";

function numberValue(value: unknown, fallback = 0): number {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function money(value: number, currency: string) {
  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(Math.max(0, value));
}

function daysInMonth(year: number, monthIndex: number) {
  return new Date(year, monthIndex + 1, 0).getDate();
}

function periodDays(start: Date, monthOffset: number) {
  return daysInMonth(start.getFullYear(), start.getMonth() + monthOffset);
}

export default function LoanCalculator({ tenant }: { tenant: TenantConfig }) {
  const services = tenant.services ?? [];
  const currency = tenant.currency || "RWF";

  const [selectedTitle, setSelectedTitle] = useState(services[0]?.title || "");
  const selected =
    services.find((item) => item.title === selectedTitle) || services[0];

  const minAmount = numberValue(
    selected?.minAmount ?? tenant.minLoanAmount,
    500000,
  );
  const maxAmount = numberValue(selected?.maxAmount ?? tenant.maxLoanAmount, 0);
  const minTerm = Math.max(1, numberValue(selected?.minTermMonths, 1));
  const maxTerm = Math.max(minTerm, numberValue(selected?.maxTermMonths, 6));
  const interestRate = numberValue(
    selected?.monthlyInterestRate ?? tenant.monthlyInterestRate,
    5,
  );
  const managementRate = numberValue(
    selected?.monthlyManagementFeeRate ?? tenant.monthlyManagementFeeRate,
    5,
  );
  const processingRate = numberValue(
    selected?.processingFeeRate ?? tenant.processingFeeRate,
    2,
  );

  const [amount, setAmount] = useState(Math.max(minAmount, 500000));
  const [term, setTerm] = useState(maxTerm);

  const calculation = useMemo(() => {
    const principal = Math.max(minAmount, numberValue(amount, minAmount));
    const months = Math.min(
      maxTerm,
      Math.max(minTerm, numberValue(term, maxTerm)),
    );
    const principalPerMonth = principal / months;
    const now = new Date();
    let balance = principal;
    let totalInterest = 0;
    let totalManagement = 0;

    const schedule = Array.from({ length: months }, (_, index) => {
      const days = periodDays(now, index);
      const monthDays = daysInMonth(now.getFullYear(), now.getMonth() + index);
      const dailyInterest = interestRate / 100 / monthDays;
      const dailyManagement = managementRate / 100 / monthDays;
      const interest = balance * dailyInterest * days;
      const management = balance * dailyManagement * days;
      const principalPaid =
        index === months - 1 ? balance : Math.min(balance, principalPerMonth);
      const installment = principalPaid + interest + management;

      totalInterest += interest;
      totalManagement += management;
      balance = Math.max(0, balance - principalPaid);

      return {
        month: index + 1,
        days,
        principal: principalPaid,
        interest,
        management,
        installment,
        balance,
      };
    });

    const processingFee = principal * (processingRate / 100);
    const totalRepayable = principal + totalInterest + totalManagement;
    const firstInstallment = schedule[0]?.installment || 0;

    return {
      principal,
      months,
      processingFee,
      totalInterest,
      totalManagement,
      totalRepayable,
      firstInstallment,
      schedule,
      netDisbursement: principal - processingFee,
    };
  }, [
    amount,
    term,
    minAmount,
    maxTerm,
    minTerm,
    interestRate,
    managementRate,
    processingRate,
  ]);

  return (
    <section className="relative overflow-hidden rounded-[2rem] border border-slate-200 bg-white shadow-[0_24px_80px_rgba(15,23,42,0.10)]">
      <div className="absolute -right-24 -top-24 h-56 w-56 rounded-full bg-amber-200/20 blur-3xl" />
      <div className="relative grid lg:grid-cols-[1.02fr_0.98fr]">
        <div className="border-b border-slate-200 p-7 md:p-9 lg:border-b-0 lg:border-r">
          <div className="text-[11px] font-black uppercase tracking-[0.22em] text-slate-500">
            Smart loan calculator
          </div>
          <h2 className="mt-3 text-3xl font-black tracking-tight text-slate-950 md:text-4xl">
            See the numbers before you apply.
          </h2>
          <p className="mt-4 max-w-xl text-sm leading-7 text-slate-600">
            Adjust the amount and term to see an indicative repayment picture
            based on the published product terms from {tenant.name}.
          </p>

          <div className="mt-7 space-y-6">
            <div>
              <label className="text-xs font-black uppercase tracking-wider text-slate-500">
                Loan product
              </label>
              <select
                value={selected?.title || ""}
                onChange={(event) => setSelectedTitle(event.target.value)}
                className="mt-2 w-full rounded-2xl border border-slate-300 bg-white px-4 py-3.5 text-sm font-semibold text-slate-800 outline-none focus:border-slate-900"
              >
                {services.map((service) => (
                  <option key={service.title} value={service.title}>
                    {service.title}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <div className="flex items-center justify-between gap-4">
                <label className="text-xs font-black uppercase tracking-wider text-slate-500">
                  Loan amount
                </label>
                <span
                  className="text-lg font-black"
                  style={{ color: tenant.primaryColor }}
                >
                  {money(calculation.principal, currency)}
                </span>
              </div>
              <input
                type="range"
                min={minAmount}
                max={
                  maxAmount > minAmount
                    ? maxAmount
                    : Math.max(minAmount * 10, 10_000_000)
                }
                step={50_000}
                value={Math.max(minAmount, calculation.principal)}
                onChange={(event) => setAmount(Number(event.target.value))}
                className="mt-4 w-full accent-slate-900"
              />
              <div className="mt-2 flex justify-between text-[11px] font-semibold text-slate-400">
                <span>From {money(minAmount, currency)}</span>
                <span>
                  {maxAmount > 0
                    ? `Up to ${money(maxAmount, currency)}`
                    : "No published maximum"}
                </span>
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between gap-4">
                <label className="text-xs font-black uppercase tracking-wider text-slate-500">
                  Repayment term
                </label>
                <span
                  className="text-lg font-black"
                  style={{ color: tenant.primaryColor }}
                >
                  {calculation.months}{" "}
                  {calculation.months === 1 ? "month" : "months"}
                </span>
              </div>
              <input
                type="range"
                min={minTerm}
                max={maxTerm}
                step={1}
                value={calculation.months}
                onChange={(event) => setTerm(Number(event.target.value))}
                className="mt-4 w-full accent-slate-900"
              />
            </div>

            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <div className="rounded-2xl bg-slate-50 p-4">
                <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                  Interest
                </div>
                <div className="mt-2 text-lg font-black text-slate-950">
                  {interestRate.toFixed(2)}%
                </div>
                <div className="mt-1 text-[11px] text-slate-500">per month</div>
              </div>
              <div className="rounded-2xl bg-slate-50 p-4">
                <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                  Management
                </div>
                <div className="mt-2 text-lg font-black text-slate-950">
                  {managementRate.toFixed(2)}%
                </div>
                <div className="mt-1 text-[11px] text-slate-500">per month</div>
              </div>
              <div className="rounded-2xl bg-slate-50 p-4">
                <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                  Processing
                </div>
                <div className="mt-2 text-lg font-black text-slate-950">
                  {processingRate.toFixed(2)}%
                </div>
                <div className="mt-1 text-[11px] text-slate-500">once</div>
              </div>
              <div className="rounded-2xl bg-slate-50 p-4">
                <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                  Currency
                </div>
                <div className="mt-2 text-lg font-black text-slate-950">
                  {currency}
                </div>
                <div className="mt-1 text-[11px] text-slate-500">
                  loan currency
                </div>
              </div>
            </div>
          </div>
        </div>

        <div
          className="bg-slate-950 p-7 text-white md:p-9"
          style={{
            background: `linear-gradient(160deg, ${tenant.primaryColor}, #071427)`,
          }}
        >
          <div className="text-[11px] font-black uppercase tracking-[0.22em] text-white/55">
            Indicative summary
          </div>
          <div className="mt-3 text-4xl font-black tracking-tight">
            {money(calculation.firstInstallment, currency)}
          </div>
          <div className="mt-1 text-sm text-white/55">
            estimated first installment
          </div>

          <div className="mt-7 space-y-3">
            {[
              ["Principal", money(calculation.principal, currency)],
              ["Interest", money(calculation.totalInterest, currency)],
              ["Management fees", money(calculation.totalManagement, currency)],
              ["Total repayable", money(calculation.totalRepayable, currency)],
              [
                "Net disbursement",
                money(calculation.netDisbursement, currency),
              ],
            ].map(([label, value]) => (
              <div
                key={label}
                className="flex items-center justify-between border-b border-white/10 py-3 last:border-0"
              >
                <span className="text-sm text-white/60">{label}</span>
                <span className="text-sm font-black text-white">{value}</span>
              </div>
            ))}
          </div>

          <div className="mt-7 rounded-2xl border border-white/10 bg-white/[0.05] p-4">
            <div className="text-[10px] font-black uppercase tracking-[0.18em] text-white/50">
              First three installments
            </div>
            <div className="mt-3 space-y-2">
              {calculation.schedule.slice(0, 3).map((row) => (
                <div
                  key={row.month}
                  className="flex items-center justify-between text-sm"
                >
                  <span className="text-white/60">Month {row.month}</span>
                  <span className="font-bold">
                    {money(row.installment, currency)}
                  </span>
                </div>
              ))}
            </div>
          </div>

          <div className="mt-7 flex flex-wrap gap-3">
            <Link
              href={`/apply?type=${encodeURIComponent(selected?.title || "")}`}
              className="rounded-xl px-5 py-3 text-sm font-black"
              style={{
                backgroundColor: tenant.accentColor,
                color: tenant.primaryColor,
              }}
            >
              Start application
            </Link>
            <Link
              href="/services"
              className="rounded-xl border border-white/20 px-5 py-3 text-sm font-bold text-white"
            >
              Compare products
            </Link>
          </div>

          <p className="mt-5 text-[11px] leading-5 text-white/40">
            This calculator is illustrative. Final interest, management fees,
            due dates, penalties and total repayment are determined by the
            approved loan agreement and the lender's official repayment
            schedule.
          </p>
        </div>
      </div>
    </section>
  );
}

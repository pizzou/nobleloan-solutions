
'use client';

import { useEffect, useMemo, useState } from 'react';

import {
  getAllPayments,
  getOverduePayments,
} from '../../../services/paymentService';

import { Payment } from '../../../types/index';
import { PageSpinner } from '../../../components/ui/Skeleton';
import { formatCurrency } from '@/lib/utils';
import { useAuth } from '@/hooks/useAuth';

type F = 'all' | 'paid' | 'pending' | 'overdue';

const FILTERS: {
  key: F;
  label: string;
  icon: string;
}[] = [
  {
    key: 'all',
    label: 'All Payments',
    icon: '▦',
  },
  {
    key: 'paid',
    label: 'Paid',
    icon: '✓',
  },
  {
    key: 'pending',
    label: 'Pending',
    icon: '◷',
  },
  {
    key: 'overdue',
    label: 'Overdue',
    icon: '!',
  },
];

export default function PaymentsPage() {
  const { currency, locale } = useAuth();

  const fc = (n?: number) =>
    formatCurrency(n, currency, locale);

  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<F>('all');

  useEffect(() => {
    let mounted = true;

    setLoading(true);

    const request =
      filter === 'overdue'
        ? getOverduePayments()
        : getAllPayments();

    request
      .then((data) => {
        if (mounted) {
          setPayments(data);
        }
      })
      .catch((error) => {
        console.error('Failed to load payments:', error);

        if (mounted) {
          setPayments([]);
        }
      })
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, [filter]);

  const now = useMemo(() => new Date(), []);

  const visible = useMemo(() => {
    if (filter === 'overdue') {
      return payments;
    }

    return payments.filter((payment) => {
      if (filter === 'paid') {
        return payment.paid;
      }

      if (filter === 'pending') {
        return !payment.paid;
      }

      return true;
    });
  }, [payments, filter]);

  const collected = useMemo(
    () =>
      payments
        .filter((payment) => payment.paid)
        .reduce(
          (sum, payment) =>
            sum + (payment.amount ?? 0),
          0
        ),
    [payments]
  );

  const outstanding = useMemo(
    () =>
      payments
        .filter((payment) => !payment.paid)
        .reduce(
          (sum, payment) =>
            sum + (payment.amount ?? 0),
          0
        ),
    [payments]
  );

  const overdueCount = useMemo(
    () =>
      payments.filter(
        (payment) =>
          !payment.paid &&
          new Date(payment.dueDate) < now
      ).length,
    [payments, now]
  );

  const totalPayments = payments.length;

  const paidCount = payments.filter(
    (payment) => payment.paid
  ).length;

  const pendingCount =
    payments.filter(
      (payment) => !payment.paid
    ).length;

  const getStatus = (payment: Payment) => {
    const overdue =
      !payment.paid &&
      new Date(payment.dueDate) < now;

    if (payment.paid) {
      return {
        label: 'Paid',
        className:
          'border-emerald-200 bg-emerald-50 text-emerald-700',
        dot: 'bg-emerald-500',
      };
    }

    if (overdue) {
      return {
        label: 'Overdue',
        className:
          'border-red-200 bg-red-50 text-red-700',
        dot: 'bg-red-500',
      };
    }

    return {
      label: 'Pending',
      className:
        'border-amber-200 bg-amber-50 text-amber-700',
      dot: 'bg-amber-500',
    };
  };

  return (
    <div className="min-h-full space-y-7">

      {/* =====================================================
          PAGE HEADER
          ===================================================== */}

      <section className="relative overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">

        <div className="absolute right-0 top-0 h-40 w-40 translate-x-16 -translate-y-16 rounded-full bg-emerald-50 blur-2xl" />

        <div className="relative flex flex-col justify-between gap-5 px-6 py-6 sm:px-7 lg:flex-row lg:items-center">

          <div>
            <div className="mb-2 flex items-center gap-2">

              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#0B1F3A] text-sm text-white shadow-sm">
                $
              </span>

              <span className="text-[10px] font-bold uppercase tracking-[0.2em] text-emerald-600">
                Financial Operations
              </span>

            </div>

            <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
              Payments
            </h1>

            <p className="mt-1 text-sm text-slate-500">
              Monitor collections, outstanding balances,
              and repayment activity.
            </p>
          </div>

          <div className="flex items-center gap-3">

            <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-right">
              <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Payment Records
              </p>

              <p className="mt-0.5 text-lg font-extrabold text-slate-900">
                {totalPayments.toLocaleString()}
              </p>
            </div>

            <div className="hidden rounded-xl border border-emerald-100 bg-emerald-50 px-4 py-3 text-right sm:block">
              <p className="text-[10px] font-bold uppercase tracking-wider text-emerald-600">
                Collection Rate
              </p>

              <p className="mt-0.5 text-lg font-extrabold text-emerald-700">
                {totalPayments > 0
                  ? `${Math.round(
                      (paidCount / totalPayments) * 100
                    )}%`
                  : '0%'}
              </p>
            </div>

          </div>

        </div>
      </section>


      {/* =====================================================
          SUMMARY CARDS
          ===================================================== */}

      <section className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">

        {/* COLLECTED */}

        <div className="group relative overflow-hidden rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md">

          <div className="absolute right-0 top-0 h-24 w-24 translate-x-8 -translate-y-8 rounded-full bg-emerald-50" />

          <div className="relative">

            <div className="flex items-start justify-between">

              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600">
                ✓
              </div>

              <span className="rounded-full border border-emerald-100 bg-emerald-50 px-2 py-1 text-[10px] font-bold uppercase tracking-wide text-emerald-600">
                Collected
              </span>

            </div>

            <p className="mt-5 text-[11px] font-bold uppercase tracking-[0.15em] text-slate-400">
              Total Collected
            </p>

            <p className="mt-1 truncate text-xl font-extrabold tracking-tight text-slate-900">
              {fc(collected)}
            </p>

            <div className="mt-3 flex items-center gap-2 text-xs text-slate-400">
              <span className="font-semibold text-emerald-600">
                {paidCount}
              </span>
              completed payments
            </div>

          </div>
        </div>


        {/* OUTSTANDING */}

        <div className="group relative overflow-hidden rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md">

          <div className="absolute right-0 top-0 h-24 w-24 translate-x-8 -translate-y-8 rounded-full bg-amber-50" />

          <div className="relative">

            <div className="flex items-start justify-between">

              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-50 text-amber-600">
                ◷
              </div>

              <span className="rounded-full border border-amber-100 bg-amber-50 px-2 py-1 text-[10px] font-bold uppercase tracking-wide text-amber-600">
                Pending
              </span>

            </div>

            <p className="mt-5 text-[11px] font-bold uppercase tracking-[0.15em] text-slate-400">
              Outstanding
            </p>

            <p className="mt-1 truncate text-xl font-extrabold tracking-tight text-slate-900">
              {fc(outstanding)}
            </p>

            <div className="mt-3 flex items-center gap-2 text-xs text-slate-400">
              <span className="font-semibold text-amber-600">
                {pendingCount}
              </span>
              payments awaiting collection
            </div>

          </div>
        </div>


        {/* OVERDUE */}

        <div className="group relative overflow-hidden rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md">

          <div className="absolute right-0 top-0 h-24 w-24 translate-x-8 -translate-y-8 rounded-full bg-red-50" />

          <div className="relative">

            <div className="flex items-start justify-between">

              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-red-50 text-red-600">
                !
              </div>

              <span className="rounded-full border border-red-100 bg-red-50 px-2 py-1 text-[10px] font-bold uppercase tracking-wide text-red-600">
                Attention
              </span>

            </div>

            <p className="mt-5 text-[11px] font-bold uppercase tracking-[0.15em] text-slate-400">
              Overdue Payments
            </p>

            <p className="mt-1 text-2xl font-extrabold tracking-tight text-red-600">
              {overdueCount}
            </p>

            <div className="mt-3 text-xs text-slate-400">
              Requires collection follow-up
            </div>

          </div>
        </div>


        {/* TOTAL */}

        <div className="group relative overflow-hidden rounded-2xl border border-slate-200 bg-[#0B1F3A] p-5 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md">

          <div className="absolute right-0 top-0 h-32 w-32 translate-x-10 -translate-y-10 rounded-full bg-emerald-500/10" />

          <div className="relative">

            <div className="flex items-start justify-between">

              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/10 text-white">
                ▦
              </div>

              <span className="rounded-full border border-white/10 bg-white/5 px-2 py-1 text-[10px] font-bold uppercase tracking-wide text-slate-300">
                Portfolio
              </span>

            </div>

            <p className="mt-5 text-[11px] font-bold uppercase tracking-[0.15em] text-slate-400">
              Total Payments
            </p>

            <p className="mt-1 text-2xl font-extrabold tracking-tight text-white">
              {totalPayments.toLocaleString()}
            </p>

            <div className="mt-3 text-xs text-slate-400">
              All payment records in your portfolio
            </div>

          </div>
        </div>

      </section>


      {/* =====================================================
          FILTER BAR
          ===================================================== */}

      <section className="rounded-2xl border border-slate-200 bg-white p-3 shadow-sm">

        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">

          <div>
            <p className="px-2 text-sm font-bold text-slate-900">
              Payment Activity
            </p>

            <p className="px-2 text-xs text-slate-400">
              Review and monitor repayment transactions
            </p>
          </div>

          <div className="flex w-full overflow-x-auto rounded-xl bg-slate-100 p-1 lg:w-auto">

            {FILTERS.map((item) => {

              const active =
                filter === item.key;

              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() =>
                    setFilter(item.key)
                  }
                  className={`
                    flex
                    shrink-0
                    items-center
                    gap-2
                    rounded-lg
                    px-4
                    py-2
                    text-xs
                    font-bold
                    transition-all
                    duration-200

                    ${
                      active
                        ? `
                          bg-white
                          text-[#0B1F3A]
                          shadow-sm
                        `
                        : `
                          text-slate-500
                          hover:text-slate-900
                        `
                    }
                  `}
                >

                  <span
                    className={`
                      flex
                      h-5
                      w-5
                      items-center
                      justify-center
                      rounded-md
                      text-[11px]

                      ${
                        active
                          ? 'bg-emerald-50 text-emerald-600'
                          : 'text-slate-400'
                      }
                    `}
                  >
                    {item.icon}
                  </span>

                  {item.label}

                </button>
              );
            })}

          </div>

        </div>

      </section>


      {/* =====================================================
          PAYMENT TABLE
          ===================================================== */}

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">

        {/* TABLE HEADER */}

        <div className="border-b border-slate-100 px-5 py-4 sm:px-6">

          <div className="flex items-center justify-between">

            <div>
              <h2 className="text-sm font-extrabold text-slate-900">
                Payment Records
              </h2>

              <p className="mt-0.5 text-xs text-slate-400">
                {visible.length.toLocaleString()} records displayed
              </p>
            </div>

            <div className="flex items-center gap-2">

              <span className="flex h-2 w-2 rounded-full bg-emerald-500" />

              <span className="text-[11px] font-semibold text-slate-400">
                Live portfolio
              </span>

            </div>

          </div>

        </div>


        {/* LOADING */}

        {loading ? (

          <div className="flex min-h-[320px] items-center justify-center">
            <PageSpinner />
          </div>

        ) : (

          <div className="overflow-x-auto">

            <table className="w-full min-w-[900px] text-sm">

              <thead>

                <tr className="border-b border-slate-100 bg-slate-50/80">

                  <th className="px-6 py-3.5 text-left text-[10px] font-extrabold uppercase tracking-[0.12em] text-slate-400">
                    #
                  </th>

                  <th className="px-5 py-3.5 text-left text-[10px] font-extrabold uppercase tracking-[0.12em] text-slate-400">
                    Amount
                  </th>

                  <th className="px-5 py-3.5 text-left text-[10px] font-extrabold uppercase tracking-[0.12em] text-slate-400">
                    Penalty
                  </th>

                  <th className="px-5 py-3.5 text-left text-[10px] font-extrabold uppercase tracking-[0.12em] text-slate-400">
                    Due Date
                  </th>

                  <th className="px-5 py-3.5 text-left text-[10px] font-extrabold uppercase tracking-[0.12em] text-slate-400">
                    Paid Date
                  </th>

                  <th className="px-5 py-3.5 text-left text-[10px] font-extrabold uppercase tracking-[0.12em] text-slate-400">
                    Method
                  </th>

                  <th className="px-6 py-3.5 text-left text-[10px] font-extrabold uppercase tracking-[0.12em] text-slate-400">
                    Status
                  </th>

                </tr>

              </thead>


              <tbody className="divide-y divide-slate-100">

                {visible.length === 0 && (

                  <tr>

                    <td
                      colSpan={7}
                      className="px-6 py-16 text-center"
                    >

                      <div className="mx-auto flex max-w-sm flex-col items-center">

                        <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100 text-xl text-slate-400">
                          $
                        </div>

                        <p className="mt-4 text-sm font-bold text-slate-700">
                          No payments found
                        </p>

                        <p className="mt-1 text-xs text-slate-400">
                          There are no payment records matching
                          the selected filter.
                        </p>

                      </div>

                    </td>

                  </tr>

                )}


                {visible.map((payment) => {

                  const isOverdue =
                    !payment.paid &&
                    new Date(payment.dueDate) < now;

                  const status =
                    getStatus(payment);

                  return (

                    <tr
                      key={payment.id}
                      className={`
                        group
                        transition-colors
                        duration-150

                        ${
                          isOverdue
                            ? 'bg-red-50/40 hover:bg-red-50/70'
                            : 'hover:bg-slate-50/70'
                        }
                      `}
                    >

                      {/* INSTALLMENT */}

                      <td className="px-6 py-4">

                        <div className="flex items-center gap-3">

                          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100 text-xs font-bold text-slate-500 group-hover:bg-white">
                            {payment.installmentNumber ??
                              payment.id}
                          </div>

                        </div>

                      </td>


                      {/* AMOUNT */}

                      <td className="px-5 py-4">

                        <div className="font-extrabold text-slate-900">
                          {fc(payment.amount)}
                        </div>

                        <div className="mt-0.5 text-[10px] font-medium uppercase tracking-wide text-slate-400">
                          Payment amount
                        </div>

                      </td>


                      {/* PENALTY */}

                      <td className="px-5 py-4">

                        {(payment.penalty ?? 0) > 0 ? (

                          <div>

                            <span className="font-bold text-orange-600">
                              {fc(payment.penalty)}
                            </span>

                            <div className="mt-0.5 text-[10px] font-medium uppercase tracking-wide text-orange-400">
                              Penalty
                            </div>

                          </div>

                        ) : (

                          <span className="text-slate-300">
                            —
                          </span>

                        )}

                      </td>


                      {/* DUE DATE */}

                      <td className="px-5 py-4">

                        <div
                          className={`
                            font-semibold
                            ${
                              isOverdue
                                ? 'text-red-600'
                                : 'text-slate-700'
                            }
                          `}
                        >
                          {payment.dueDate}
                        </div>

                        {isOverdue && (
                          <div className="mt-0.5 text-[10px] font-bold uppercase tracking-wide text-red-400">
                            Payment overdue
                          </div>
                        )}

                      </td>


                      {/* PAID DATE */}

                      <td className="px-5 py-4">

                        <span className="text-slate-500">
                          {payment.paidDate ?? '—'}
                        </span>

                      </td>


                      {/* METHOD */}

                      <td className="px-5 py-4">

                        <span className="inline-flex rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-600">
                          {payment.paymentMethod ?? '—'}
                        </span>

                      </td>


                      {/* STATUS */}

                      <td className="px-6 py-4">

                        <span
                          className={`
                            inline-flex
                            items-center
                            gap-1.5
                            rounded-full
                            border
                            px-2.5
                            py-1
                            text-[11px]
                            font-bold
                            ${status.className}
                          `}
                        >

                          <span
                            className={`
                              h-1.5
                              w-1.5
                              rounded-full
                              ${status.dot}
                            `}
                          />

                          {status.label}

                        </span>

                      </td>

                    </tr>

                  );
                })}

              </tbody>

            </table>

          </div>

        )}

      </section>


      {/* =====================================================
          FOOTNOTE
          ===================================================== */}

      <div className="flex flex-col justify-between gap-2 px-1 text-[11px] text-slate-400 sm:flex-row sm:items-center">

        <p>
          Payment information is synchronized with your
          loan portfolio.
        </p>

        <p className="font-medium">
          Noble Loan Solutions · Financial Operations
        </p>

      </div>

    </div>
  );
}

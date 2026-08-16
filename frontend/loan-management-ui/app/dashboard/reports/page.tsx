"use client";
import Link from "next/link";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
const groups = [
  [
    "Executive intelligence",
    [
      [
        "Portfolio performance",
        "Exposure, collections, PAR and facility status",
      ],
      ["Loan performance", "Facility-level performance and repayment"],
      ["Collections", "Collection activity and delinquency"],
      ["Borrower exposure", "Customer concentration and exposure"],
    ],
  ],
  [
    "Financial control",
    [
      ["Trial Balance", "Debit and credit integrity"],
      ["Balance Sheet", "Assets, liabilities and equity"],
      ["Profit & Loss", "Income, expense and net income"],
      ["Cash Flow", "Cash movements and liquidity"],
    ],
  ],
  [
    "Operational reporting",
    [
      ["Loan portfolio", "Full facility register"],
      ["Payments", "Posted collection activity"],
      ["Borrowers", "Customer portfolio"],
      ["Overdue portfolio", "Delinquency and collection attention"],
    ],
  ],
  [
    "Regulatory",
    [
      ["BNR reporting", "Official regulatory workspace"],
      ["Credit bureau", "Credit reporting and checks"],
    ],
  ],
];
const route = (t: string) =>
  t === "BNR reporting"
    ? "/dashboard/reports/regulatory/bnr"
    : t === "Credit bureau"
      ? "/dashboard/reports/regulatory/crb"
      : "/dashboard/reports";
export default function Reports() {
  return (
    <main className="premium-page pb-14">
      <div className="mx-auto max-w-[1600px] space-y-7 px-4 py-6 sm:px-6 lg:px-8">
        <section className="premium-hero px-7 py-8 text-white sm:px-10">
          <div className="relative z-10">
            <div className="premium-kicker">Business intelligence</div>
            <h1 className="mt-2 text-4xl font-black tracking-[-.04em]">
              Reports & intelligence
            </h1>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-slate-300">
              A single executive reporting centre for portfolio quality,
              collections, financial control and regulatory reporting.
            </p>
          </div>
        </section>
        {groups.map(([group, items]: any) => (
          <section key={group}>
            <div className="mb-3">
              <div className="premium-eyebrow">{group}</div>
              <h2 className="mt-1 text-lg font-black text-[#071a2d]">
                {group}
              </h2>
            </div>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {items.map(([title, desc]: any) => (
                <Link href={route(title)} key={title}>
                  <Card className="h-full transition hover:-translate-y-0.5">
                    <CardBody>
                      <div className="grid h-10 w-10 place-items-center rounded-xl bg-[#071a2d] text-[#d2b24f]">
                        ▥
                      </div>
                      <h3 className="mt-5 text-sm font-black text-[#071a2d]">
                        {title}
                      </h3>
                      <p className="mt-2 text-[10px] leading-5 text-slate-500">
                        {desc}
                      </p>
                      <div className="mt-5 text-[10px] font-black text-[#087f74]">
                        Open workspace →
                      </div>
                    </CardBody>
                  </Card>
                </Link>
              ))}
            </div>
          </section>
        ))}
      </div>
    </main>
  );
}

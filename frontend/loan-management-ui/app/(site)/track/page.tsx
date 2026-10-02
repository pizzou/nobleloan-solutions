"use client";

import { useState } from "react";
import { useTenant } from "../layout";
import { publicApi } from "@/services/api";
import { toast } from "@/hooks/useToast";

interface StatusStep {
  label: string;
  complete: boolean;
  failed: boolean;
}

interface Comment {
  message: string;
  createdAt: string;
  from: string;
}

interface PaymentHistory {
  paymentId: number;
  paymentDate: string;
  amount: number;
  method: string;
  status: string;
}

interface UpcomingInstallment {
  installmentNumber: number;
  dueDate: string;
  amount: number;
  principal: number;
  interest: number;
  status: string;
}

interface TimelineEvent {
  label: string;
  date: string;
}

interface DocumentRequirements {
  required: string[];
  missing: string[];
  unverified: string[];
  readyToApprove: boolean;
  readyToDisburse: boolean;
}

interface UploadedDoc {
  id: number;
  documentType: string;
  fileName: string;
  fileSize: number;
  uploadedAt: string;
  verificationStatus: string;
}

interface StatusResult {
  reference: string;
  status: string;
  statusLabel: string;
  statusSteps: StatusStep[];
  progressSteps: StatusStep[];
  timeline: TimelineEvent[];
  loanType: string;
  amount: number;
  currency: string;
  submittedDate: string;
  updatedDate: string;
  rejectionReason?: string;
  maritalStatus?: string;
  documentsRequired: DocumentRequirements | null;
}

interface DashboardResult {
  loanId: number;
  referenceNumber: string;
  borrowerName: string;
  status: string;
  loanType: string;
  principal: number;
  outstandingBalance: number;
  totalPaid: number;
  totalRepayable: number;
  repaymentProgress: number;
  currency: string;
  interestRate: number;
  nextInstallmentAmount: number;
  nextPaymentDate: string;
  nextDueDate: string;
  maturityDate: string;
  missedInstallments: number;
  daysOverdue: number;
  daysUntilDue: number;
  loanOfficer: string;
  activeLoans: number;
  overdueLoans: number;
  completedLoans: number;
  recentPayments: PaymentHistory[];
  upcomingInstallments: UpcomingInstallment[];
  availablePaymentMethods: string[];
}

type TrackResult = StatusResult & Partial<DashboardResult>;

const DOC_LABELS: Record<string, string> = {
  NATIONAL_ID: "National ID",
  PASSPORT: "Passport",
  DRIVING_LICENSE: "Driving License",
  VOTER_CARD: "Voter Card",
  RESIDENCE_PERMIT: "Residence Permit",
  PROOF_OF_ADDRESS: "Proof of Address",
  BANK_STATEMENT: "Bank Statement",
  PAYSLIP: "Payslip",
  EMPLOYMENT_LETTER: "Employment Letter",
  BUSINESS_REGISTRATION: "Business Registration",
  TAX_CERTIFICATE: "Tax Certificate",
  COLLATERAL_DOCUMENT: "Collateral Document",
  MARRIAGE_CERTIFICATE: "Marriage Certificate",
  SINGLE_CERTIFICATE: "Single Status Certificate",
  SELFIE: "Selfie Photo",
  SIGNATURE: "Signature",
  OTHER: "Other Document",
};

const docLabel = (type: string) => DOC_LABELS[type] ?? type.replace(/_/g, " ");

const PAY_METHODS: {
  key: "MOBILE_MONEY" | "BANK_TRANSFER" | "CARD";
  label: string;
  icon: string;
  networks?: string[];
}[] = [
  {
    key: "MOBILE_MONEY",
    label: "MTN Mobile Money",
    icon: "📱",
    networks: ["MTN"],
  },
  {
    key: "MOBILE_MONEY",
    label: "Airtel Money",
    icon: "📱",
    networks: ["AIRTEL"],
  },
  {
    key: "BANK_TRANSFER",
    label: "Bank Transfer",
    icon: "🏦",
  },
  {
    key: "CARD",
    label: "Visa / Mastercard",
    icon: "💳",
  },
];

const RWANDA_PHONE_REGEX = /^0\d{9}$/;

const statusLabel = (status?: string) => {
  if (!status) return "Unknown";

  return status
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
};

type IconName =
  | "arrow"
  | "calendar"
  | "check"
  | "checkCircle"
  | "chevron"
  | "clock"
  | "close"
  | "document"
  | "download"
  | "file"
  | "lock"
  | "message"
  | "phone"
  | "search"
  | "shield"
  | "upload"
  | "user"
  | "wallet"
  | "bank"
  | "card"
  | "alert";

function Icon({
  name,
  size = 18,
  strokeWidth = 1.9,
}: {
  name: IconName;
  size?: number;
  strokeWidth?: number;
}) {
  const common = {
    width: size,
    height: size,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true,
  };

  const paths: Record<IconName, React.ReactNode> = {
    arrow: (
      <>
        <path d="M5 12h13" />
        <path d="m13 6 6 6-6 6" />
      </>
    ),
    calendar: (
      <>
        <rect x="3" y="4.5" width="18" height="16" rx="2.5" />
        <path d="M7 2.8v3.5M17 2.8v3.5M3 9h18" />
      </>
    ),
    check: <path d="m5 12 4 4L19 6" />,
    checkCircle: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="m8 12 2.6 2.6L16.5 9" />
      </>
    ),
    chevron: <path d="m9 18 6-6-6-6" />,
    clock: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v5l3 2" />
      </>
    ),
    close: (
      <>
        <path d="m7 7 10 10M17 7 7 17" />
      </>
    ),
    document: (
      <>
        <path d="M7 3.5h7l4 4V20a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1Z" />
        <path d="M14 3.5V8h4M9 12h6M9 16h6" />
      </>
    ),
    download: (
      <>
        <path d="M12 4v10" />
        <path d="m8 10 4 4 4-4" />
        <path d="M5 19.5h14" />
      </>
    ),
    file: (
      <>
        <path d="M7 3.5h7l4 4V20a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1Z" />
        <path d="M14 3.5V8h4" />
      </>
    ),
    lock: (
      <>
        <rect x="5" y="10" width="14" height="11" rx="2" />
        <path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" />
      </>
    ),
    message: (
      <>
        <path d="M4 5.5h16v11H8l-4 4v-15Z" />
        <path d="M8 10h8M8 13h5" />
      </>
    ),
    phone: (
      <>
        <path d="M7.5 4.5h3l1.5 4-2 1.3a15 15 0 0 0 4.2 4.2l1.3-2 4 1.5v3a2 2 0 0 1-2.3 2A15.5 15.5 0 0 1 5.5 6.8a2 2 0 0 1 2-2.3Z" />
      </>
    ),
    search: (
      <>
        <circle cx="10.8" cy="10.8" r="6.3" />
        <path d="m16 16 4 4" />
      </>
    ),
    shield: (
      <>
        <path d="M12 3.5 19 6v5.2c0 4.5-2.8 7.8-7 9.3-4.2-1.5-7-4.8-7-9.3V6l7-2.5Z" />
        <path d="m9 12 2 2 4-4" />
      </>
    ),
    upload: (
      <>
        <path d="M12 16V5" />
        <path d="m8 9 4-4 4 4" />
        <path d="M5 19.5h14" />
      </>
    ),
    user: (
      <>
        <circle cx="12" cy="8" r="3" />
        <path d="M5 20c.7-3.1 3.3-5 7-5s6.3 1.9 7 5" />
      </>
    ),
    wallet: (
      <>
        <path d="M4 6.5h15a1.5 1.5 0 0 1 1.5 1.5v10A1.5 1.5 0 0 1 19 19.5H5A2 2 0 0 1 3 17.5v-9A2 2 0 0 1 5 6.5h12" />
        <path d="M16 12h4.5v4H16a2 2 0 1 1 0-4Z" />
      </>
    ),
    bank: (
      <>
        <path d="m3 10 9-6 9 6" />
        <path d="M5 10v8M9 10v8M15 10v8M19 10v8M3 19.5h18" />
      </>
    ),
    card: (
      <>
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <path d="M3 9h18M7 14h4" />
      </>
    ),
    alert: (
      <>
        <path d="M12 3.8 21 19H3l9-15.2Z" />
        <path d="M12 9v4M12 16.5h.01" />
      </>
    ),
  };

  return <svg {...common}>{paths[name]}</svg>;
}

const statusMeta = (status?: string) => {
  switch (status) {
    case "ACTIVE":
      return {
        label: "Active",
        dot: "bg-emerald-500",
        pill: "bg-emerald-50 text-emerald-700 border-emerald-100",
      };
    case "OVERDUE":
      return {
        label: "Payment overdue",
        dot: "bg-red-500",
        pill: "bg-red-50 text-red-700 border-red-100",
      };
    case "PAID":
      return {
        label: "Paid",
        dot: "bg-sky-500",
        pill: "bg-sky-50 text-sky-700 border-sky-100",
      };
    case "CLOSED":
      return {
        label: "Closed",
        dot: "bg-slate-500",
        pill: "bg-slate-50 text-slate-700 border-slate-200",
      };
    case "APPROVED":
      return {
        label: "Approved",
        dot: "bg-emerald-500",
        pill: "bg-emerald-50 text-emerald-700 border-emerald-100",
      };
    case "REJECTED":
      return {
        label: "Decision issued",
        dot: "bg-red-500",
        pill: "bg-red-50 text-red-700 border-red-100",
      };
    default:
      return {
        label: statusLabel(status),
        dot: "bg-amber-500",
        pill: "bg-amber-50 text-amber-700 border-amber-100",
      };
  }
};

function MetricCard({
  label,
  value,
  hint,
  tone = "default",
  icon,
}: {
  label: string;
  value: string;
  hint?: string;
  tone?: "default" | "danger" | "success" | "brand";
  icon: IconName;
}) {
  const toneMap = {
    default: { icon: "bg-slate-100 text-slate-600", value: "text-slate-950" },
    danger: { icon: "bg-red-50 text-red-600", value: "text-red-700" },
    success: {
      icon: "bg-emerald-50 text-emerald-700",
      value: "text-emerald-800",
    },
    brand: { icon: "bg-[#eef5ff] text-[#163c72]", value: "text-[#102b52]" },
  } as const;
  const toneStyle = toneMap[tone];

  return (
    <div className="rounded-2xl border border-slate-200/80 bg-white p-4 sm:p-5 shadow-[0_12px_40px_rgba(15,27,61,.05)]">
      <div className="flex items-start justify-between gap-3">
        <span
          className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl ${toneStyle.icon}`}
        >
          <Icon name={icon} size={17} />
        </span>
        <span className="text-[9px] font-black uppercase tracking-[0.16em] text-slate-400">
          {label}
        </span>
      </div>
      <div
        className={`mt-4 text-lg sm:text-xl font-black tracking-tight ${toneStyle.value}`}
      >
        {value}
      </div>
      {hint ? (
        <div className="mt-1 text-[10px] font-medium leading-5 text-slate-500">
          {hint}
        </div>
      ) : null}
    </div>
  );
}

export default function TrackPage() {
  const tenant = useTenant();

  const [reference, setReference] = useState("");
  const [phone, setPhone] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const [result, setResult] = useState<TrackResult | null>(null);

  const [comments, setComments] = useState<Comment[]>([]);
  const [commentsError, setCommentsError] = useState(false);

  const [showPaySheet, setShowPaySheet] = useState(false);
  const [payChoice, setPayChoice] = useState(0);

  /**
   * Borrower-entered payment amount.
   *
   * This is the main correction.
   */
  const [paymentAmount, setPaymentAmount] = useState("");

  const [momoPhone, setMomoPhone] = useState("");

  const [paying, setPaying] = useState(false);
  const [paySuccess, setPaySuccess] = useState(false);
  const [payMessage, setPayMessage] = useState("");

  const [downloadingDoc, setDownloadingDoc] = useState<
    "agreement" | "schedule" | "receipt" | null
  >(null);

  const [uploadedDocs, setUploadedDocs] = useState<UploadedDoc[]>([]);

  const [uploadingType, setUploadingType] = useState<string | null>(null);

  const [uploadError, setUploadError] = useState("");

  const primary = tenant?.primaryColor ?? "#0F1B3D";

  const accent = "#F4C430";

  /*
   * =========================================================
   * FORMATTERS
   * =========================================================
   */

  const fmt = (n?: number) =>
    (n ?? 0).toLocaleString("en-RW", {
      minimumFractionDigits: 0,
      maximumFractionDigits: 2,
    });

  const fmtDate = (d?: string) =>
    d
      ? new Date(d).toLocaleDateString("en-RW", {
          year: "numeric",
          month: "short",
          day: "numeric",
        })
      : "—";

  const fmtDateTime = (d?: string) =>
    d
      ? new Date(d).toLocaleString("en-RW", {
          year: "numeric",
          month: "short",
          day: "numeric",
          hour: "2-digit",
          minute: "2-digit",
        })
      : "—";

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    setError("");
    setResult(null);
    setComments([]);
    setCommentsError(false);
    setPaySuccess(false);

    setLoading(true);

    const ref = reference.trim();
    const ph = phone.trim();

    if (!RWANDA_PHONE_REGEX.test(ph)) {
      setError(
        "Enter exactly 10 digits starting with 0, for example 0788123456.",
      );
      setLoading(false);
      return;
    }

    try {
      const status = (await publicApi.trackApplication(
        ref,
        ph,
      )) as StatusResult;

      let merged: TrackResult = {
        ...status,
      };

      try {
        const dashboard = (await publicApi.trackDashboard(
          ref,
          ph,
        )) as DashboardResult;

        merged = {
          ...merged,
          ...dashboard,
        };
      } catch {}

      setResult(merged);

      /*
       * Load comments independently.
       */
      publicApi
        .trackComments(ref, ph)
        .then((c) => setComments(c as Comment[]))
        .catch(() => setCommentsError(true));

      /*
       * Load uploaded documents independently.
       */
      publicApi
        .listDocuments(ref, ph)
        .then((d) => setUploadedDocs(d as UploadedDoc[]))
        .catch(() => setUploadedDocs([]));
    } catch (err: any) {
      setError(
        err?.response?.data?.error ||
          err?.message ||
          "We could not find an application matching those details.",
      );
    } finally {
      setLoading(false);
    }
  };

  /*
   * =========================================================
   * OPEN PAYMENT SHEET
   * =========================================================
   */

  const openPaySheet = () => {
    if (!result) return;

    setShowPaySheet(true);

    setPaySuccess(false);
    setPayMessage("");
    setError("");

    const defaultAmount =
      result.nextInstallmentAmount && result.nextInstallmentAmount > 0
        ? result.nextInstallmentAmount
        : (result.outstandingBalance ?? 0);

    setPaymentAmount(defaultAmount > 0 ? String(defaultAmount) : "");
  };

  /*
   * =========================================================
   * PAYMENT
   * =========================================================
   */

  const handlePayment = async () => {
    if (!result) return;

    const choice = PAY_METHODS[payChoice];

    /*
     * -----------------------------------------------
     * Validate payment amount
     * -----------------------------------------------
     */

    const normalizedAmount = paymentAmount.replace(/,/g, "").trim();

    const amount = Number(normalizedAmount);

    const outstanding = Number(result.outstandingBalance ?? 0);

    if (!normalizedAmount || !Number.isFinite(amount) || amount <= 0) {
      setError("Please enter a valid payment amount.");
      return;
    }

    if (amount > outstanding) {
      setError(
        `Payment amount cannot exceed your outstanding balance of ${
          result.currency
        } ${fmt(outstanding)}.`,
      );
      return;
    }

    /*
     * -----------------------------------------------
     * Mobile Money validation
     * -----------------------------------------------
     */

    if (choice.key === "MOBILE_MONEY" && !momoPhone.trim()) {
      setError("Please enter your mobile money number.");
      return;
    }

    setPaying(true);
    setError("");
    setPaySuccess(false);

    try {
      const payload: {
        amount: number;
        paymentMethod: "MOBILE_MONEY" | "BANK_TRANSFER" | "CARD";
        phoneNumber?: string;
        network?: string;
      } = {
        amount,
        paymentMethod: choice.key,
      };

      /*
       * Mobile Money.
       */
      if (choice.key === "MOBILE_MONEY") {
        payload.phoneNumber = momoPhone.trim();

        payload.network = choice.networks?.[0];
      }

      /*
       * Initiate payment.
       */
      const idempotencyKey =
        typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
          ? crypto.randomUUID()
          : `public-payment-${Date.now()}-${Math.random().toString(36).slice(2)}`;

      const res = (await publicApi.initiatePayment(
        result.referenceNumber || result.reference,
        phone.trim(),
        payload,
        idempotencyKey,
      )) as any;

      const data = res?.data ?? res;

      /*
       * Card payments are completed on the provider's secure hosted/tokenized
       * checkout. Raw PAN/CVV never enters Noble Loan Solutions servers.
       */
      if (
        choice.key === "CARD" &&
        typeof data?.redirectUrl === "string" &&
        data.redirectUrl.trim()
      ) {
        window.location.assign(data.redirectUrl);
        return;
      }

      setPaySuccess(true);

      setPayMessage(
        res?.message ||
          `Payment of ${
            result.currency
          } ${fmt(amount)} initiated successfully.`,
      );

      /*
       * If backend immediately recorded it,
       * close modal and show success.
       */
      if (data?.recorded) {
        setShowPaySheet(false);

        toast("success", "Payment recorded successfully.");

        /*
         * Refresh dashboard after successful
         * recorded payment.
         */
        try {
          const refreshed = (await publicApi.trackDashboard(
            result.referenceNumber || result.reference,
            phone.trim(),
          )) as DashboardResult;

          setResult((prev) => {
            if (!prev) {
              return null;
            }

            return {
              ...prev,
              ...refreshed,
            };
          });
        } catch {
          /*
           * Payment was successful even if
           * dashboard refresh fails.
           */
        }
      } else {
        toast(
          "success",
          `Payment of ${
            result.currency
          } ${fmt(amount)} initiated. Please complete the confirmation.`,
        );
      }
    } catch (err: any) {
      setError(
        err?.response?.data?.error ||
          err?.response?.data?.message ||
          err?.message ||
          "Payment request failed.",
      );
    } finally {
      setPaying(false);
    }
  };

  /*
   * =========================================================
   * DOWNLOAD DOCUMENT
   * =========================================================
   */

  const handleDownloadDoc = (
    doc: "agreement" | "schedule" | "receipt",
    label: string,
  ) => {
    if (!result) {
      return;
    }

    const ref = (result.referenceNumber || result.reference || reference)
      .trim()
      .toUpperCase();

    const ph = phone.trim();

    if (!ref || !ph) {
      toast(
        "error",
        "Your application reference and phone number are required to download this document.",
      );
      return;
    }

    const baseUrl = (
      process.env.NEXT_PUBLIC_API_URL ||
      process.env.NEXT_PUBLIC_API_BASE_URL ||
      ""
    ).replace(/\/+$/, "");

    if (!baseUrl) {
      toast(
        "error",
        "The document service is not configured. Please contact support.",
      );
      return;
    }

    const url =
      `${baseUrl}/api/public/applications/` +
      `${encodeURIComponent(ref)}/documents/` +
      `${doc}.pdf?phone=${encodeURIComponent(ph)}`;

    setDownloadingDoc(doc);

    try {
      /*
       * Public PDF endpoint:
       *
       * Open the authenticated-by-reference/phone document directly in the
       * browser instead of using Axios/XHR to read a cross-origin Blob.
       *
       * This avoids the browser-side CORS requirement for reading a binary
       * response. The backend remains responsible for validating the
       * reference + phone before returning the document.
       */
      const anchor = document.createElement("a");

      anchor.href = url;
      anchor.target = "_blank";
      anchor.rel = "noopener noreferrer";
      anchor.style.display = "none";

      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();

      toast("success", `${label} opened successfully.`);
    } catch (err: unknown) {
      console.error("[DOCUMENT] Download failed:", err);
      toast("error", `Could not open ${label}. Please retry.`);
    } finally {
      window.setTimeout(() => {
        setDownloadingDoc(null);
      }, 1000);
    }
  };

  /*
   * =========================================================
   * UPLOAD DOCUMENT
   * =========================================================
   */

  const handleUpload = async (documentType: string, file: File) => {
    if (!result) return;

    setUploadingType(documentType);

    setUploadError("");

    const ref = result.referenceNumber || result.reference;

    const ph = phone.trim();

    try {
      await publicApi.uploadDocument(ref, ph, documentType, file);

      toast("success", `${docLabel(documentType)} uploaded successfully.`);

      const [docs, status] = await Promise.all([
        publicApi.listDocuments(ref, ph) as Promise<UploadedDoc[]>,

        publicApi.trackApplication(ref, ph) as Promise<StatusResult>,
      ]);

      setUploadedDocs(docs);

      setResult((prev) => {
        if (!prev) {
          return {
            ...status,
          };
        }

        return {
          ...prev,
          ...status,
        };
      });
    } catch (err: any) {
      setUploadError(
        err?.response?.data?.error ||
          err?.response?.data?.message ||
          err?.message ||
          `Could not upload ${docLabel(documentType)}.`,
      );
    } finally {
      setUploadingType(null);
    }
  };

  /*
   * =========================================================
   * DERIVED VALUES
   * =========================================================
   */

  const canPay =
    !!result &&
    (result.status === "ACTIVE" || result.status === "OVERDUE") &&
    (result.outstandingBalance ?? 0) > 0;

  const dueNow =
    result?.nextInstallmentAmount && result.nextInstallmentAmount > 0
      ? result.nextInstallmentAmount
      : (result?.outstandingBalance ?? 0);

  const repaymentProgress = Math.min(
    100,
    Math.max(0, result?.repaymentProgress ?? 0),
  );

  const isActiveLoan =
    !!result &&
    (result.status === "ACTIVE" ||
      result.status === "OVERDUE" ||
      result.status === "PAID" ||
      result.status === "CLOSED");

  const progressSteps = result?.progressSteps ?? result?.statusSteps ?? [];

  /*
   * =========================================================
   * RENDER
   * =========================================================
   */

  return (
    <div className="min-h-screen bg-[#f6f8fb] text-slate-900 selection:bg-emerald-100 selection:text-emerald-950">
      <section className="relative overflow-hidden bg-[#071f2f] text-white">
        <div className="absolute inset-0 opacity-70" aria-hidden="true">
          <div className="absolute -left-24 -top-32 h-80 w-80 rounded-full bg-emerald-500/20 blur-3xl" />
          <div className="absolute -right-20 top-12 h-96 w-96 rounded-full bg-[#2d5b9c]/20 blur-3xl" />
          <div className="absolute bottom-0 left-1/3 h-56 w-56 rounded-full bg-amber-300/10 blur-3xl" />
          <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,.035)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.035)_1px,transparent_1px)] bg-[size:42px_42px]" />
        </div>

        <div className="relative mx-auto max-w-7xl px-4 pb-28 pt-10 sm:px-6 sm:pt-14 lg:px-8 lg:pb-36">
          <div className="flex flex-col gap-10 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-3xl">
              <div className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.07] px-3.5 py-2 text-[10px] font-black uppercase tracking-[0.18em] text-white/75 backdrop-blur">
                <span className="grid h-5 w-5 place-items-center rounded-full bg-white/10 text-amber-300">
                  <Icon name="shield" size={12} />
                </span>
                Secure borrower access
              </div>

              <h1 className="mt-6 text-4xl font-black leading-[.98] tracking-[-0.04em] sm:text-5xl lg:text-7xl">
                Your loan,
                <span className="block text-amber-300">clearly in view.</span>
              </h1>

              <p className="mt-6 max-w-2xl text-sm leading-7 text-white/65 sm:text-base">
                Track an application, stay on top of repayments, keep your
                documents current and access your loan records — all from one
                secure place.
              </p>

              <div className="mt-8 flex flex-wrap gap-3">
                <div className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.05] px-3.5 py-2 text-[10px] font-bold text-white/70">
                  <span className="h-2 w-2 rounded-full bg-emerald-400" />{" "}
                  Rwanda borrower support
                </div>
                <div className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.05] px-3.5 py-2 text-[10px] font-bold text-white/70">
                  <Icon name="lock" size={13} /> Reference + phone verification
                </div>
              </div>
            </div>

            <div className="hidden max-w-sm lg:block">
              <div className="rounded-3xl border border-white/10 bg-white/[0.06] p-5 backdrop-blur-xl shadow-2xl">
                <div className="flex items-center justify-between">
                  <div className="text-[9px] font-black uppercase tracking-[0.18em] text-white/45">
                    Borrower centre
                  </div>
                  <div className="rounded-full border border-emerald-300/20 bg-emerald-300/10 px-2.5 py-1 text-[9px] font-bold text-emerald-200">
                    Protected
                  </div>
                </div>
                <div className="mt-6 grid grid-cols-2 gap-3">
                  <div className="rounded-2xl bg-black/10 p-4 ring-1 ring-white/5">
                    <div className="text-[9px] font-black uppercase tracking-wider text-white/40">
                      Applications
                    </div>
                    <div className="mt-2 text-xl font-black">Track</div>
                  </div>
                  <div className="rounded-2xl bg-black/10 p-4 ring-1 ring-white/5">
                    <div className="text-[9px] font-black uppercase tracking-wider text-white/40">
                      Repayments
                    </div>
                    <div className="mt-2 text-xl font-black">Pay</div>
                  </div>
                </div>
                <div className="mt-3 rounded-2xl border border-white/8 bg-white/[0.04] p-4">
                  <div className="flex items-center gap-3">
                    <span className="grid h-10 w-10 place-items-center rounded-xl bg-amber-300/10 text-amber-200">
                      <Icon name="document" size={17} />
                    </span>
                    <div>
                      <div className="text-xs font-black">
                        Records & documents
                      </div>
                      <div className="mt-1 text-[10px] text-white/45">
                        Accessible after secure lookup
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <main className="relative z-10 mx-auto -mt-20 max-w-7xl px-4 pb-16 sm:-mt-24 sm:px-6 lg:px-8">
        <section className="overflow-hidden rounded-[28px] border border-white/70 bg-white shadow-[0_30px_90px_rgba(15,27,61,.14)]">
          <div className="border-b border-slate-100 bg-[linear-gradient(135deg,#ffffff_0%,#f7fbf9_100%)] p-5 sm:p-7 lg:p-8">
            <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <div className="inline-flex items-center gap-2 text-[9px] font-black uppercase tracking-[0.18em] text-emerald-700">
                  <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />{" "}
                  {tenant?.name ?? "Noble Loan Solutions"}
                </div>
                <h2 className="mt-2 text-2xl font-black tracking-tight text-slate-950 sm:text-3xl">
                  Track your application or loan
                </h2>
                <p className="mt-2 max-w-2xl text-xs leading-6 text-slate-500 sm:text-sm">
                  Use the reference number and phone number associated with the
                  application. No account password is required for this public
                  lookup.
                </p>
              </div>

              <div className="grid grid-cols-2 gap-2 sm:flex sm:items-center">
                <div className="flex items-center gap-2 rounded-2xl border border-slate-200 bg-white px-3.5 py-3 shadow-sm">
                  <span className="grid h-8 w-8 place-items-center rounded-xl bg-emerald-50 text-emerald-700">
                    <Icon name="lock" size={14} />
                  </span>
                  <div>
                    <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                      Privacy
                    </div>
                    <div className="text-[10px] font-bold text-slate-700">
                      Secure lookup
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-2 rounded-2xl border border-slate-200 bg-white px-3.5 py-3 shadow-sm">
                  <span className="grid h-8 w-8 place-items-center rounded-xl bg-slate-100 text-slate-600">
                    <Icon name="phone" size={14} />
                  </span>
                  <div>
                    <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                      Support
                    </div>
                    <div className="text-[10px] font-bold text-slate-700">
                      Loan team access
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <form
              onSubmit={handleSubmit}
              className="mt-7 grid gap-4 lg:grid-cols-[1fr_1fr_auto]"
            >
              <div>
                <label
                  htmlFor="track-reference"
                  className="mb-2 block text-[10px] font-black uppercase tracking-[0.14em] text-slate-500"
                >
                  Application reference
                </label>
                <div className="relative">
                  <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-slate-400">
                    <Icon name="file" size={17} />
                  </span>
                  <input
                    id="track-reference"
                    required
                    autoComplete="off"
                    value={reference}
                    onChange={(e) => setReference(e.target.value.toUpperCase())}
                    placeholder="GFS-2026-000123"
                    className="h-14 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-11 pr-4 text-sm font-black uppercase tracking-wide text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-emerald-600 focus:bg-white focus:ring-4 focus:ring-emerald-600/10"
                  />
                </div>
              </div>

              <div>
                <label
                  htmlFor="track-phone"
                  className="mb-2 block text-[10px] font-black uppercase tracking-[0.14em] text-slate-500"
                >
                  Phone number
                </label>
                <div className="relative">
                  <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-slate-400">
                    <Icon name="phone" size={17} />
                  </span>
                  <input
                    id="track-phone"
                    required
                    type="tel"
                    inputMode="numeric"
                    autoComplete="tel"
                    value={phone}
                    onChange={(e) =>
                      setPhone(e.target.value.replace(/\D/g, "").slice(0, 10))
                    }
                    placeholder="0788 123 456"
                    className="h-14 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-11 pr-4 text-sm font-semibold text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-emerald-600 focus:bg-white focus:ring-4 focus:ring-emerald-600/10"
                  />
                </div>
                <div className="mt-2 text-[10px] font-medium text-slate-400">
                  Enter the 10-digit number used for the application.
                </div>
              </div>

              <div className="flex items-end">
                <button
                  type="submit"
                  disabled={loading}
                  className="flex h-14 w-full items-center justify-center gap-2 rounded-2xl bg-[#0b3b2a] px-7 text-xs font-black text-white shadow-[0_14px_34px_rgba(11,59,42,.22)] transition hover:-translate-y-0.5 hover:bg-[#0a3325] disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"
                >
                  {loading ? (
                    "Checking…"
                  ) : (
                    <>
                      Track securely <Icon name="arrow" size={16} />
                    </>
                  )}
                </button>
              </div>
            </form>

            {error && !showPaySheet ? (
              <div
                className="mt-4 flex items-start gap-3 rounded-2xl border border-red-100 bg-red-50 p-4 text-xs font-semibold leading-5 text-red-700"
                role="alert"
              >
                <span className="mt-0.5 grid h-6 w-6 shrink-0 place-items-center rounded-lg bg-red-100 text-red-600">
                  <Icon name="alert" size={14} />
                </span>
                <span>{error}</span>
              </div>
            ) : null}
          </div>

          <div className="grid gap-3 border-t border-slate-100 bg-slate-950/[0.018] p-4 sm:grid-cols-3 sm:p-5">
            <div className="flex items-center gap-3 rounded-2xl px-3 py-2.5">
              <span className="grid h-9 w-9 place-items-center rounded-xl bg-white text-emerald-700 shadow-sm">
                <Icon name="shield" size={16} />
              </span>
              <div>
                <div className="text-[10px] font-black text-slate-800">
                  Private by design
                </div>
                <div className="text-[10px] text-slate-500">
                  Verified with reference + phone
                </div>
              </div>
            </div>
            <div className="flex items-center gap-3 rounded-2xl px-3 py-2.5">
              <span className="grid h-9 w-9 place-items-center rounded-xl bg-white text-slate-600 shadow-sm">
                <Icon name="document" size={16} />
              </span>
              <div>
                <div className="text-[10px] font-black text-slate-800">
                  Your records
                </div>
                <div className="text-[10px] text-slate-500">
                  Documents and repayment history
                </div>
              </div>
            </div>
            <div className="flex items-center gap-3 rounded-2xl px-3 py-2.5">
              <span className="grid h-9 w-9 place-items-center rounded-xl bg-white text-amber-600 shadow-sm">
                <Icon name="wallet" size={16} />
              </span>
              <div>
                <div className="text-[10px] font-black text-slate-800">
                  Repayment access
                </div>
                <div className="text-[10px] text-slate-500">
                  Pay when a payment is due
                </div>
              </div>
            </div>
          </div>
        </section>

        {result &&
          (() => {
            const meta = statusMeta(result.status);
            const completedCount = progressSteps.filter(
              (step) => step.complete,
            ).length;
            const totalSteps = progressSteps.length;
            const progressPercent = totalSteps
              ? Math.round((completedCount / totalSteps) * 100)
              : 0;
            const nextPayment =
              dueNow > 0 ? dueNow : (result.outstandingBalance ?? 0);

            return (
              <div className="mt-7 space-y-5">
                <section className="overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-[0_18px_55px_rgba(15,27,61,.07)]">
                  <div className="border-b border-slate-100 p-5 sm:p-7">
                    <div className="flex flex-col gap-6 xl:flex-row xl:items-start xl:justify-between">
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <span
                            className={`inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-[9px] font-black uppercase tracking-[0.14em] ${meta.pill}`}
                          >
                            <span
                              className={`h-1.5 w-1.5 rounded-full ${meta.dot}`}
                            />{" "}
                            {meta.label}
                          </span>
                          {result.loanType ? (
                            <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-[9px] font-black uppercase tracking-[0.14em] text-slate-500">
                              {result.loanType}
                            </span>
                          ) : null}
                        </div>

                        <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-end sm:gap-4">
                          <div>
                            <div className="text-[9px] font-black uppercase tracking-[0.16em] text-slate-400">
                              Reference
                            </div>
                            <div className="mt-1 break-all font-mono text-xl font-black tracking-tight text-slate-950">
                              {result.referenceNumber || result.reference}
                            </div>
                          </div>
                          {result.borrowerName ? (
                            <div className="hidden h-8 w-px bg-slate-200 sm:block" />
                          ) : null}
                          {result.borrowerName ? (
                            <div className="flex items-center gap-2 text-xs font-semibold text-slate-600">
                              <span className="grid h-8 w-8 place-items-center rounded-xl bg-slate-100 text-slate-600">
                                <Icon name="user" size={14} />
                              </span>
                              {result.borrowerName}
                            </div>
                          ) : null}
                        </div>

                        <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-[10px] text-slate-500">
                          <span>
                            <span className="font-black text-slate-700">
                              Submitted:
                            </span>{" "}
                            {fmtDate(result.submittedDate)}
                          </span>
                          <span>
                            <span className="font-black text-slate-700">
                              Updated:
                            </span>{" "}
                            {fmtDateTime(result.updatedDate)}
                          </span>
                          {result.loanOfficer ? (
                            <span>
                              <span className="font-black text-slate-700">
                                Loan officer:
                              </span>{" "}
                              {result.loanOfficer}
                            </span>
                          ) : null}
                        </div>
                      </div>

                      {isActiveLoan ? (
                        <div className="grid min-w-0 gap-2 sm:grid-cols-2 xl:w-[320px]">
                          <div className="rounded-2xl bg-slate-950 p-4 text-white shadow-sm">
                            <div className="text-[9px] font-black uppercase tracking-[0.14em] text-white/45">
                              Outstanding
                            </div>
                            <div className="mt-1 text-xl font-black tracking-tight">
                              {result.currency} {fmt(result.outstandingBalance)}
                            </div>
                          </div>
                          <div className="rounded-2xl bg-emerald-50 p-4 text-emerald-950 ring-1 ring-emerald-100">
                            <div className="text-[9px] font-black uppercase tracking-[0.14em] text-emerald-700/65">
                              Next payment
                            </div>
                            <div className="mt-1 text-xl font-black tracking-tight">
                              {result.currency} {fmt(nextPayment)}
                            </div>
                            <div className="mt-1 text-[9px] font-bold text-emerald-700/70">
                              Due {fmtDate(result.nextDueDate)}
                            </div>
                          </div>
                        </div>
                      ) : null}
                    </div>
                  </div>

                  {totalSteps ? (
                    <div className="border-b border-slate-100 p-5 sm:p-7">
                      <div className="flex items-end justify-between gap-4">
                        <div>
                          <div className="text-[9px] font-black uppercase tracking-[0.16em] text-emerald-700">
                            Application progress
                          </div>
                          <div className="mt-1 text-sm font-black text-slate-950">
                            {completedCount} of {totalSteps} milestones complete
                          </div>
                        </div>
                        <div className="text-right">
                          <div className="text-2xl font-black text-slate-950">
                            {progressPercent}%
                          </div>
                          <div className="text-[9px] font-bold uppercase tracking-wider text-slate-400">
                            Complete
                          </div>
                        </div>
                      </div>
                      <div className="mt-5 h-2 overflow-hidden rounded-full bg-slate-100">
                        <div
                          className="h-full rounded-full bg-[linear-gradient(90deg,#0c5b3d,#1a7a57)] transition-all"
                          style={{ width: `${progressPercent}%` }}
                        />
                      </div>
                      <div className="mt-6 grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-6">
                        {progressSteps.map((step, i) => (
                          <div
                            key={`${step.label}-${i}`}
                            className="rounded-2xl border border-slate-100 bg-slate-50 p-3"
                          >
                            <div
                              className={`grid h-8 w-8 place-items-center rounded-xl text-[10px] font-black ${step.failed ? "bg-red-100 text-red-700" : step.complete ? "bg-emerald-100 text-emerald-700" : "bg-white text-slate-400 border border-slate-200"}`}
                            >
                              {step.failed ? (
                                "!"
                              ) : step.complete ? (
                                <Icon name="check" size={14} />
                              ) : (
                                i + 1
                              )}
                            </div>
                            <div className="mt-2 text-[9px] font-black uppercase leading-4 tracking-wider text-slate-500">
                              {step.label}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  ) : null}
                </section>

                {result.rejectionReason ? (
                  <section className="flex flex-col gap-4 rounded-[24px] border border-red-100 bg-red-50 p-5 sm:flex-row sm:items-start sm:p-6">
                    <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-white text-red-600 shadow-sm">
                      <Icon name="alert" size={19} />
                    </span>
                    <div>
                      <div className="text-[10px] font-black uppercase tracking-[0.16em] text-red-700">
                        Application decision
                      </div>
                      <div className="mt-1 text-sm font-black text-red-950">
                        A decision has been recorded on this application.
                      </div>
                      <p className="mt-2 text-xs leading-6 text-red-800">
                        {result.rejectionReason}
                      </p>
                    </div>
                  </section>
                ) : null}

                {isActiveLoan ? (
                  <>
                    <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                      <MetricCard
                        label="Original principal"
                        value={`${result.currency} ${fmt(result.principal)}`}
                        hint="Amount originally disbursed"
                        icon="wallet"
                        tone="brand"
                      />
                      <MetricCard
                        label="Outstanding"
                        value={`${result.currency} ${fmt(result.outstandingBalance)}`}
                        hint="Current balance remaining"
                        icon="bank"
                        tone="danger"
                      />
                      <MetricCard
                        label="Total repaid"
                        value={`${result.currency} ${fmt(result.totalPaid)}`}
                        hint={`${Math.round(repaymentProgress)}% of scheduled repayment`}
                        icon="checkCircle"
                        tone="success"
                      />
                      <MetricCard
                        label="Repayable total"
                        value={`${result.currency} ${fmt(result.totalRepayable)}`}
                        hint="Scheduled total over the loan"
                        icon="calendar"
                      />
                    </section>

                    <section className="overflow-hidden rounded-[28px] bg-[#082d25] text-white shadow-[0_20px_70px_rgba(8,45,37,.16)]">
                      <div className="relative overflow-hidden p-5 sm:p-7 lg:p-8">
                        <div
                          className="absolute -right-20 -top-20 h-64 w-64 rounded-full bg-emerald-300/10 blur-3xl"
                          aria-hidden="true"
                        />
                        <div className="relative grid gap-8 xl:grid-cols-[1.15fr_.85fr] xl:items-center">
                          <div>
                            <div className="text-[9px] font-black uppercase tracking-[0.18em] text-white/45">
                              Repayment health
                            </div>
                            <div className="mt-2 flex items-end gap-3">
                              <span className="text-5xl font-black tracking-[-0.05em] sm:text-6xl">
                                {Math.round(repaymentProgress)}%
                              </span>
                              <span className="pb-2 text-xs text-white/45">
                                repaid
                              </span>
                            </div>
                            <div className="mt-6 h-3 overflow-hidden rounded-full bg-white/10">
                              <div
                                className="h-full rounded-full bg-amber-300 transition-all"
                                style={{ width: `${repaymentProgress}%` }}
                              />
                            </div>
                            <div className="mt-2 flex justify-between text-[10px] font-bold text-white/45">
                              <span>
                                Paid {result.currency} {fmt(result.totalPaid)}
                              </span>
                              <span>
                                Remaining {result.currency}{" "}
                                {fmt(result.outstandingBalance)}
                              </span>
                            </div>
                          </div>

                          <div className="rounded-[24px] border border-white/10 bg-white/[0.06] p-5 sm:p-6">
                            <div className="flex items-start justify-between gap-4">
                              <div>
                                <div className="text-[9px] font-black uppercase tracking-[0.15em] text-white/40">
                                  Next payment
                                </div>
                                <div className="mt-2 text-3xl font-black tracking-tight">
                                  {result.currency} {fmt(nextPayment)}
                                </div>
                                <div className="mt-1 text-xs text-white/50">
                                  Due {fmtDate(result.nextDueDate)}
                                </div>
                              </div>
                              <span className="grid h-11 w-11 place-items-center rounded-2xl bg-amber-300/10 text-amber-200">
                                <Icon name="calendar" size={19} />
                              </span>
                            </div>
                            {canPay ? (
                              <button
                                type="button"
                                onClick={openPaySheet}
                                className="mt-5 flex h-12 w-full items-center justify-center gap-2 rounded-2xl bg-amber-300 px-4 text-xs font-black text-[#123727] shadow-lg transition hover:-translate-y-0.5 hover:bg-amber-200"
                              >
                                Pay securely <Icon name="arrow" size={15} />
                              </button>
                            ) : null}
                            <div className="mt-3 text-center text-[9px] font-medium text-white/40">
                              Never share your PIN or OTP with anyone.
                            </div>
                          </div>
                        </div>
                      </div>
                    </section>

                    {result.status === "OVERDUE" &&
                    (result.daysOverdue ?? 0) > 0 ? (
                      <section className="flex flex-col gap-4 rounded-[24px] border border-red-100 bg-white p-5 shadow-sm sm:flex-row sm:items-center sm:justify-between sm:p-6">
                        <div className="flex items-center gap-3">
                          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-red-50 text-red-600">
                            <Icon name="alert" size={18} />
                          </span>
                          <div>
                            <div className="text-xs font-black text-slate-950">
                              Payment overdue
                            </div>
                            <div className="mt-1 text-[10px] text-slate-500">
                              This payment is {result.daysOverdue} day
                              {result.daysOverdue === 1 ? "" : "s"} overdue.
                              Paying promptly can help keep your account up to
                              date.
                            </div>
                          </div>
                        </div>
                        {canPay ? (
                          <button
                            type="button"
                            onClick={openPaySheet}
                            className="h-11 rounded-xl bg-red-600 px-5 text-[10px] font-black text-white shadow-sm transition hover:bg-red-700"
                          >
                            Make payment
                          </button>
                        ) : null}
                      </section>
                    ) : null}

                    <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                      {[
                        [
                          "Interest rate",
                          result.interestRate != null
                            ? `${result.interestRate}%`
                            : "—",
                          "Rate on your loan",
                        ],
                        [
                          "Next due date",
                          fmtDate(result.nextDueDate),
                          result.daysUntilDue != null
                            ? `${Math.max(result.daysUntilDue, 0)} days from today`
                            : "Scheduled payment date",
                        ],
                        [
                          "Maturity date",
                          fmtDate(result.maturityDate),
                          "Final scheduled date",
                        ],
                        [
                          "Missed installments",
                          String(result.missedInstallments ?? 0),
                          "Recorded missed payments",
                        ],
                      ].map(([label, value, hint]) => (
                        <div
                          key={label}
                          className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
                        >
                          <div className="text-[9px] font-black uppercase tracking-[0.15em] text-slate-400">
                            {label}
                          </div>
                          <div className="mt-2 text-sm font-black text-slate-950">
                            {value}
                          </div>
                          <div className="mt-1 text-[10px] leading-5 text-slate-500">
                            {hint}
                          </div>
                        </div>
                      ))}
                    </section>
                  </>
                ) : null}

                <div className="grid gap-5 xl:grid-cols-[1.2fr_.8fr]">
                  <div className="space-y-5">
                    {result.documentsRequired ? (
                      <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm sm:p-7">
                        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                          <div>
                            <div className="text-[9px] font-black uppercase tracking-[0.16em] text-emerald-700">
                              Verification centre
                            </div>
                            <h3 className="mt-1 text-lg font-black text-slate-950">
                              Documents
                            </h3>
                            <p className="mt-1 text-xs leading-6 text-slate-500">
                              Keep your required records complete. Uploaded
                              files remain tied to this application.
                            </p>
                          </div>
                          <div className="rounded-2xl bg-slate-50 px-4 py-3 text-right">
                            <div className="text-xl font-black text-slate-950">
                              {result.documentsRequired.required.length -
                                result.documentsRequired.missing.length}
                              /{result.documentsRequired.required.length}
                            </div>
                            <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                              submitted
                            </div>
                          </div>
                        </div>

                        {uploadError ? (
                          <div className="mt-5 rounded-2xl border border-red-100 bg-red-50 p-4 text-xs font-semibold text-red-700">
                            {uploadError}
                          </div>
                        ) : null}

                        <div className="mt-6 grid gap-3 sm:grid-cols-2">
                          {result.documentsRequired.missing.map((docType) => (
                            <div
                              key={docType}
                              className="rounded-2xl border border-amber-100 bg-amber-50/60 p-4"
                            >
                              <div className="flex items-start justify-between gap-3">
                                <div>
                                  <div className="text-xs font-black text-slate-950">
                                    {docLabel(docType)}
                                  </div>
                                  <div className="mt-1 text-[10px] text-amber-800/70">
                                    Required to continue
                                  </div>
                                </div>
                                <span className="rounded-full bg-white px-2 py-1 text-[8px] font-black uppercase tracking-wider text-amber-700 shadow-sm">
                                  Missing
                                </span>
                              </div>
                              <label className="mt-4 inline-flex cursor-pointer items-center gap-2 rounded-xl bg-[#0b3b2a] px-3.5 py-2.5 text-[10px] font-black text-white shadow-sm transition hover:bg-[#092f22]">
                                <input
                                  type="file"
                                  accept="image/*,application/pdf"
                                  className="hidden"
                                  disabled={uploadingType === docType}
                                  onChange={(e) => {
                                    const file = e.target.files?.[0];
                                    if (file) handleUpload(docType, file);
                                    e.target.value = "";
                                  }}
                                />{" "}
                                <Icon name="upload" size={14} />{" "}
                                {uploadingType === docType
                                  ? "Uploading…"
                                  : "Upload document"}
                              </label>
                            </div>
                          ))}

                          {uploadedDocs
                            .filter(
                              (doc) =>
                                !result.documentsRequired!.missing.includes(
                                  doc.documentType,
                                ),
                            )
                            .map((doc) => {
                              const rejected =
                                doc.verificationStatus === "REJECTED" ||
                                doc.verificationStatus ===
                                  "REPLACEMENT_REQUESTED";
                              const verified =
                                doc.verificationStatus === "VERIFIED";
                              return (
                                <div
                                  key={doc.id}
                                  className={`rounded-2xl border p-4 ${rejected ? "border-red-100 bg-red-50/60" : "border-slate-200 bg-slate-50/50"}`}
                                >
                                  <div className="flex items-start justify-between gap-3">
                                    <div className="min-w-0">
                                      <div className="text-xs font-black text-slate-950">
                                        {docLabel(doc.documentType)}
                                      </div>
                                      <div className="mt-1 truncate text-[10px] text-slate-400">
                                        {doc.fileName}
                                      </div>
                                    </div>
                                    <span
                                      className={`rounded-full px-2 py-1 text-[8px] font-black uppercase tracking-wider ${verified ? "bg-emerald-100 text-emerald-700" : rejected ? "bg-red-100 text-red-700" : "bg-sky-100 text-sky-700"}`}
                                    >
                                      {verified
                                        ? "Verified"
                                        : rejected
                                          ? "Replace"
                                          : "Reviewing"}
                                    </span>
                                  </div>
                                  {rejected ? (
                                    <label className="mt-4 inline-flex cursor-pointer items-center gap-2 rounded-xl bg-red-600 px-3.5 py-2.5 text-[10px] font-black text-white shadow-sm">
                                      <input
                                        type="file"
                                        accept="image/*,application/pdf"
                                        className="hidden"
                                        disabled={
                                          uploadingType === doc.documentType
                                        }
                                        onChange={(e) => {
                                          const file = e.target.files?.[0];
                                          if (file)
                                            handleUpload(
                                              doc.documentType,
                                              file,
                                            );
                                          e.target.value = "";
                                        }}
                                      />{" "}
                                      <Icon name="upload" size={14} />{" "}
                                      {uploadingType === doc.documentType
                                        ? "Uploading…"
                                        : "Upload replacement"}
                                    </label>
                                  ) : null}
                                </div>
                              );
                            })}
                        </div>
                      </section>
                    ) : null}

                    {result.upcomingInstallments &&
                    result.upcomingInstallments.length > 0 ? (
                      <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm sm:p-7">
                        <div className="mb-6">
                          <div className="text-[9px] font-black uppercase tracking-[0.16em] text-emerald-700">
                            Repayment plan
                          </div>
                          <h3 className="mt-1 text-lg font-black text-slate-950">
                            Upcoming installments
                          </h3>
                          <p className="mt-1 text-xs leading-6 text-slate-500">
                            Your next scheduled payments based on the current
                            loan record.
                          </p>
                        </div>
                        <div className="overflow-hidden rounded-2xl border border-slate-100">
                          {result.upcomingInstallments.map((inst, i) => (
                            <div
                              key={inst.installmentNumber}
                              className={`grid grid-cols-[auto_1fr_auto] gap-3 p-4 sm:grid-cols-[44px_1fr_auto_auto] sm:items-center ${i < result.upcomingInstallments!.length - 1 ? "border-b border-slate-100" : ""}`}
                            >
                              <div className="grid h-9 w-9 place-items-center rounded-xl bg-emerald-50 text-[10px] font-black text-emerald-700">
                                {inst.installmentNumber}
                              </div>
                              <div>
                                <div className="text-xs font-black text-slate-900">
                                  Installment #{inst.installmentNumber}
                                </div>
                                <div className="mt-1 text-[10px] text-slate-400">
                                  Due {fmtDate(inst.dueDate)}
                                </div>
                              </div>
                              <div className="hidden sm:block">
                                <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                                  Principal
                                </div>
                                <div className="mt-1 text-xs font-bold text-slate-700">
                                  {result.currency} {fmt(inst.principal)}
                                </div>
                              </div>
                              <div className="text-right">
                                <div className="text-xs font-black text-slate-950">
                                  {result.currency} {fmt(inst.amount)}
                                </div>
                                <div className="mt-1 text-[9px] font-black uppercase tracking-wider text-amber-600">
                                  {statusLabel(inst.status)}
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      </section>
                    ) : null}

                    {result.recentPayments &&
                    result.recentPayments.length > 0 ? (
                      <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm sm:p-7">
                        <div className="mb-6">
                          <div className="text-[9px] font-black uppercase tracking-[0.16em] text-emerald-700">
                            Account activity
                          </div>
                          <h3 className="mt-1 text-lg font-black text-slate-950">
                            Recent payments
                          </h3>
                        </div>
                        <div className="divide-y divide-slate-100">
                          {result.recentPayments.map((payment) => (
                            <div
                              key={payment.paymentId}
                              className="flex items-center justify-between gap-4 py-4 first:pt-0 last:pb-0"
                            >
                              <div className="flex min-w-0 items-center gap-3">
                                <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-emerald-50 text-emerald-700">
                                  <Icon name="checkCircle" size={16} />
                                </span>
                                <div className="min-w-0">
                                  <div className="text-xs font-black text-slate-900">
                                    Payment received
                                  </div>
                                  <div className="mt-1 truncate text-[10px] text-slate-400">
                                    {fmtDate(payment.paymentDate)} ·{" "}
                                    {payment.method}
                                  </div>
                                </div>
                              </div>
                              <div className="shrink-0 text-right">
                                <div className="text-xs font-black text-slate-950">
                                  {result.currency} {fmt(payment.amount)}
                                </div>
                                <div
                                  className={`mt-1 text-[9px] font-black uppercase tracking-wider ${payment.status === "COMPLETED" ? "text-emerald-600" : "text-slate-400"}`}
                                >
                                  {statusLabel(payment.status)}
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      </section>
                    ) : null}
                  </div>

                  <aside className="space-y-5">
                    {isActiveLoan ? (
                      <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm sm:p-7">
                        <div className="text-[9px] font-black uppercase tracking-[0.16em] text-emerald-700">
                          Your loan records
                        </div>
                        <h3 className="mt-1 text-lg font-black text-slate-950">
                          Official documents
                        </h3>
                        <p className="mt-1 text-xs leading-6 text-slate-500">
                          Open official PDFs using the secure reference and
                          phone verification already provided.
                        </p>
                        <div className="mt-5 space-y-2">
                          {[
                            {
                              key: "agreement" as const,
                              label: "Loan agreement",
                              icon: "file" as IconName,
                            },
                            {
                              key: "schedule" as const,
                              label: "Repayment schedule",
                              icon: "calendar" as IconName,
                            },
                            {
                              key: "receipt" as const,
                              label: "Disbursement receipt",
                              icon: "document" as IconName,
                            },
                          ].map((doc) => (
                            <button
                              key={doc.key}
                              type="button"
                              disabled={downloadingDoc === doc.key}
                              onClick={() =>
                                handleDownloadDoc(doc.key, doc.label)
                              }
                              className="group flex w-full items-center justify-between rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-left transition hover:border-emerald-200 hover:bg-emerald-50/50 disabled:opacity-50"
                            >
                              <span className="flex min-w-0 items-center gap-3">
                                <span className="grid h-9 w-9 place-items-center rounded-xl bg-white text-slate-600 shadow-sm group-hover:text-emerald-700">
                                  <Icon name={doc.icon} size={15} />
                                </span>
                                <span>
                                  <span className="block text-xs font-black text-slate-900">
                                    {doc.label}
                                  </span>
                                  <span className="mt-0.5 block text-[9px] text-slate-400">
                                    Official PDF
                                  </span>
                                </span>
                              </span>
                              <Icon name="download" size={15} />
                            </button>
                          ))}
                        </div>
                      </section>
                    ) : null}

                    {result.timeline && result.timeline.length > 0 ? (
                      <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm sm:p-7">
                        <div className="text-[9px] font-black uppercase tracking-[0.16em] text-emerald-700">
                          Application history
                        </div>
                        <h3 className="mt-1 text-lg font-black text-slate-950">
                          Timeline
                        </h3>
                        <div className="relative mt-6 pl-2">
                          <div className="absolute bottom-2 left-[15px] top-2 w-px bg-slate-200" />
                          <div className="space-y-5">
                            {result.timeline.map((event, i) => (
                              <div key={i} className="relative flex gap-4">
                                <span className="relative z-10 grid h-8 w-8 shrink-0 place-items-center rounded-full border-4 border-white bg-[#0b3b2a] shadow-sm">
                                  <span className="h-1.5 w-1.5 rounded-full bg-white" />
                                </span>
                                <div className="pt-0.5">
                                  <div className="text-xs font-black text-slate-900">
                                    {event.label}
                                  </div>
                                  <div className="mt-1 text-[10px] text-slate-400">
                                    {fmtDateTime(event.date)}
                                  </div>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      </section>
                    ) : null}

                    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm sm:p-7">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <div className="text-[9px] font-black uppercase tracking-[0.16em] text-emerald-700">
                            Communication
                          </div>
                          <h3 className="mt-1 text-lg font-black text-slate-950">
                            Messages from your team
                          </h3>
                        </div>
                        <span className="grid h-10 w-10 place-items-center rounded-xl bg-slate-100 text-slate-600">
                          <Icon name="message" size={16} />
                        </span>
                      </div>
                      <p className="mt-2 text-xs leading-6 text-slate-500">
                        Important updates and document feedback from{" "}
                        {tenant?.name ?? "our team"}.
                      </p>
                      <div className="mt-5">
                        {commentsError ? (
                          <div className="rounded-2xl border border-amber-100 bg-amber-50 p-4 text-xs font-semibold leading-5 text-amber-800">
                            We could not load your messages right now. Please
                            refresh and try again.
                          </div>
                        ) : comments.length === 0 ? (
                          <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 p-6 text-center">
                            <div className="mx-auto grid h-11 w-11 place-items-center rounded-2xl bg-white text-slate-400 shadow-sm">
                              <Icon name="message" size={17} />
                            </div>
                            <div className="mt-3 text-xs font-black text-slate-700">
                              No messages yet
                            </div>
                            <div className="mt-1 text-[10px] leading-5 text-slate-400">
                              Your loan team will post updates here when needed.
                            </div>
                          </div>
                        ) : (
                          <div className="space-y-3">
                            {comments.map((comment, i) => (
                              <div
                                key={i}
                                className="rounded-2xl border border-slate-100 bg-slate-50/70 p-4"
                              >
                                <div className="flex items-start gap-3">
                                  <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-[#0b3b2a] text-xs font-black text-white">
                                    {(comment.from || "L")
                                      .charAt(0)
                                      .toUpperCase()}
                                  </span>
                                  <div className="min-w-0">
                                    <div className="text-xs font-black text-slate-900">
                                      {comment.from || "Loan Officer"}
                                    </div>
                                    <div className="mt-0.5 text-[10px] text-slate-400">
                                      {fmtDateTime(comment.createdAt)}
                                    </div>
                                    <p className="mt-3 text-xs leading-6 text-slate-700">
                                      {comment.message}
                                    </p>
                                  </div>
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    </section>
                  </aside>
                </div>

                <section className="rounded-[28px] border border-slate-200 bg-[#eef7f2] p-5 sm:p-7">
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                    <div className="flex items-center gap-3">
                      <span className="grid h-11 w-11 place-items-center rounded-2xl bg-white text-emerald-700 shadow-sm">
                        <Icon name="phone" size={18} />
                      </span>
                      <div>
                        <div className="text-xs font-black text-slate-950">
                          Need help with this application?
                        </div>
                        <div className="mt-1 text-[10px] leading-5 text-slate-500">
                          Contact your loan team and quote your application
                          reference so we can assist faster.
                        </div>
                      </div>
                    </div>
                    <div className="rounded-xl bg-white px-4 py-2.5 font-mono text-[10px] font-black text-slate-700 shadow-sm">
                      {result.referenceNumber || result.reference}
                    </div>
                  </div>
                </section>
              </div>
            );
          })()}
      </main>

      {showPaySheet && result ? (
        <div
          className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/70 p-3 backdrop-blur-md sm:p-5"
          role="dialog"
          aria-modal="true"
          aria-labelledby="payment-modal-title"
        >
          <div className="flex max-h-[calc(100dvh-1.5rem)] w-full max-w-xl flex-col overflow-hidden rounded-[28px] border border-white/10 bg-white shadow-[0_40px_120px_rgba(0,0,0,.35)] sm:max-h-[calc(100dvh-2.5rem)]">
            <div className="relative overflow-hidden bg-[#082d25] px-5 py-5 text-white sm:px-7 sm:py-6">
              <div
                className="absolute -right-10 -top-16 h-44 w-44 rounded-full bg-amber-300/10 blur-3xl"
                aria-hidden="true"
              />
              <div className="relative flex items-start justify-between gap-4">
                <div>
                  <div className="text-[9px] font-black uppercase tracking-[0.18em] text-white/40">
                    Secure repayment
                  </div>
                  <h3
                    id="payment-modal-title"
                    className="mt-1 text-xl font-black tracking-tight"
                  >
                    Make a payment
                  </h3>
                  <p className="mt-1 text-xs text-white/50">
                    Choose an amount and your preferred payment method.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setShowPaySheet(false)}
                  aria-label="Close payment window"
                  className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-white/10 text-white/60 transition hover:bg-white/15 hover:text-white"
                >
                  <Icon name="close" size={17} />
                </button>
              </div>
              <div className="relative mt-5 grid grid-cols-2 gap-2">
                <div className="rounded-2xl border border-white/10 bg-white/[0.06] p-4">
                  <div className="text-[9px] font-black uppercase tracking-wider text-white/40">
                    Outstanding
                  </div>
                  <div className="mt-1 text-lg font-black">
                    {result.currency} {fmt(result.outstandingBalance)}
                  </div>
                </div>
                <div className="rounded-2xl border border-white/10 bg-white/[0.06] p-4">
                  <div className="text-[9px] font-black uppercase tracking-wider text-white/40">
                    Reference
                  </div>
                  <div className="mt-1 truncate font-mono text-xs font-black">
                    {result.referenceNumber || result.reference}
                  </div>
                </div>
              </div>
            </div>

            <div
              className="flex-1 min-h-0 overflow-y-auto p-5 sm:p-7"
              style={{ WebkitOverflowScrolling: "touch" }}
            >
              <div className="space-y-6">
                <div>
                  <div className="flex items-center justify-between gap-3">
                    <label
                      htmlFor="payment-amount"
                      className="text-[10px] font-black uppercase tracking-[0.14em] text-slate-500"
                    >
                      Amount to pay
                    </label>
                    <span className="text-[9px] font-bold text-slate-400">
                      Max {result.currency} {fmt(result.outstandingBalance)}
                    </span>
                  </div>
                  <div className="relative mt-2">
                    <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-sm font-black text-slate-400">
                      {result.currency}
                    </span>
                    <input
                      id="payment-amount"
                      type="number"
                      min="1"
                      max={result.outstandingBalance ?? undefined}
                      step="1"
                      value={paymentAmount}
                      onChange={(e) => {
                        setPaymentAmount(e.target.value);
                        if (error) setError("");
                      }}
                      placeholder="Enter amount"
                      inputMode="decimal"
                      className="h-14 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-20 pr-4 text-lg font-black text-slate-950 outline-none transition focus:border-emerald-600 focus:bg-white focus:ring-4 focus:ring-emerald-600/10"
                    />
                  </div>
                  <div className="mt-2 flex flex-wrap gap-2">
                    <button
                      type="button"
                      onClick={() =>
                        setPaymentAmount(
                          String(result.nextInstallmentAmount ?? 0),
                        )
                      }
                      className="rounded-xl bg-slate-100 px-3 py-2 text-[9px] font-black text-slate-600 transition hover:bg-slate-200"
                    >
                      Next installment
                    </button>
                    <button
                      type="button"
                      onClick={() =>
                        setPaymentAmount(String(result.outstandingBalance ?? 0))
                      }
                      className="rounded-xl bg-slate-100 px-3 py-2 text-[9px] font-black text-slate-600 transition hover:bg-slate-200"
                    >
                      Full balance
                    </button>
                  </div>
                </div>

                <div>
                  <div className="text-[10px] font-black uppercase tracking-[0.14em] text-slate-500">
                    Payment method
                  </div>
                  <div className="mt-2 grid gap-2 sm:grid-cols-2">
                    {PAY_METHODS.map((method, i) => (
                      <button
                        key={`${method.label}-${i}`}
                        type="button"
                        onClick={() => setPayChoice(i)}
                        className={`rounded-2xl border p-4 text-left transition ${payChoice === i ? "border-emerald-400 bg-emerald-50 shadow-[0_10px_30px_rgba(16,185,129,.08)]" : "border-slate-200 bg-slate-50 hover:bg-white"}`}
                      >
                        <div className="flex items-start justify-between gap-3">
                          <span className="grid h-9 w-9 place-items-center rounded-xl bg-white text-slate-600 shadow-sm">
                            {method.key === "MOBILE_MONEY" ? (
                              <Icon name="phone" size={15} />
                            ) : method.key === "BANK_TRANSFER" ? (
                              <Icon name="bank" size={15} />
                            ) : (
                              <Icon name="card" size={15} />
                            )}
                          </span>
                          <span
                            className={`h-4 w-4 rounded-full border-2 ${payChoice === i ? "border-emerald-600 bg-emerald-600 ring-2 ring-emerald-100" : "border-slate-300"}`}
                          />
                        </div>
                        <div className="mt-3 text-xs font-black text-slate-900">
                          {method.label}
                        </div>
                        {method.key === "MOBILE_MONEY" ? (
                          <div className="mt-1 text-[9px] text-slate-400">
                            {method.networks?.[0] ?? "Mobile network"}
                          </div>
                        ) : null}
                      </button>
                    ))}
                  </div>
                </div>

                {PAY_METHODS[payChoice]?.key === "MOBILE_MONEY" ? (
                  <div>
                    <label
                      htmlFor="momo-phone"
                      className="text-[10px] font-black uppercase tracking-[0.14em] text-slate-500"
                    >
                      Mobile money number
                    </label>
                    <input
                      id="momo-phone"
                      type="tel"
                      inputMode="numeric"
                      value={momoPhone}
                      onChange={(e) => setMomoPhone(e.target.value)}
                      placeholder="07XXXXXXXX"
                      autoComplete="tel"
                      className="mt-2 h-12 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 text-sm font-bold outline-none transition focus:border-emerald-600 focus:bg-white focus:ring-4 focus:ring-emerald-600/10"
                    />
                    <div className="mt-2 text-[9px] text-slate-400">
                      Network:{" "}
                      <span className="font-black text-slate-600">
                        {PAY_METHODS[payChoice]?.networks?.[0] ?? "—"}
                      </span>
                    </div>
                  </div>
                ) : PAY_METHODS[payChoice]?.key === "BANK_TRANSFER" ? (
                  <div className="rounded-2xl border border-sky-100 bg-sky-50 p-4 text-xs leading-6 text-sky-800">
                    After you continue, you will receive the bank transfer
                    instructions required to settle this payment.
                  </div>
                ) : PAY_METHODS[payChoice]?.key === "CARD" ? (
                  <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs leading-6 text-slate-600">
                    Your card payment opens the provider's secure hosted
                    checkout. Noble Loan Solutions does not collect or store
                    your card number or CVV.
                  </div>
                ) : null}

                {paySuccess ? (
                  <div className="flex items-start gap-3 rounded-2xl border border-emerald-100 bg-emerald-50 p-4 text-xs font-bold leading-5 text-emerald-800">
                    <Icon name="checkCircle" size={18} />{" "}
                    <span>{payMessage}</span>
                  </div>
                ) : null}
                {error && showPaySheet ? (
                  <div className="rounded-2xl border border-red-100 bg-red-50 p-4 text-xs font-semibold leading-5 text-red-700">
                    {error}
                  </div>
                ) : null}

                <button
                  type="button"
                  onClick={handlePayment}
                  disabled={paying}
                  className="flex h-12 min-h-12 w-full items-center justify-center gap-2 rounded-2xl bg-[#0b3b2a] px-5 text-xs font-black text-white shadow-[0_14px_34px_rgba(11,59,42,.22)] transition hover:-translate-y-0.5 hover:bg-[#092f22] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {paying
                    ? "Processing payment…"
                    : `Pay ${result.currency} ${paymentAmount ? fmt(Number(paymentAmount)) : "0"}`}{" "}
                  <Icon name="arrow" size={15} />
                </button>
                <div className="flex items-center justify-center gap-2 text-center text-[9px] leading-5 text-slate-400">
                  <Icon name="lock" size={12} /> Payments are processed
                  securely. Never share your PIN or OTP.
                </div>
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

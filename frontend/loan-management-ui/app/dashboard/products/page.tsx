"use client";

import { useEffect, useMemo, useState } from "react";

import { get, post, put, del } from "../../../services/api";
import { toast } from "../../../hooks/useToast";
import { PageSpinner } from "../../../components/ui/Skeleton";
import { Pill } from "../../../components/ui/Badge";
import { LOAN_TYPE_META } from "../../../lib/utils";

interface Product {
  id?: number;
  name: string;
  icon?: string;
  description?: string;
  loanType: string;
  interestRate: number;
  interestRateType: "MONTHLY";
  managementFeePercent: number;
  processingFeePercent: number;
  penaltyPercent?: number;
  minAmount: number;
  maxAmount: number | null;
  minTermMonths: number;
  maxTermMonths: number;
  active: boolean;
  displayOrder?: number;
}

/**
 * Form model.
 *
 * `id` is optional because:
 *
 * - new product => no id yet
 * - existing product => id is present
 *
 * Keeping one editing type removes the TypeScript union problem
 * and makes create/update logic type-safe.
 */
interface ProductForm {
  id?: number;
  name: string;
  icon: string;
  description: string;
  loanType: string;
  interestRate: number;
  interestRateType: "MONTHLY";
  managementFeePercent: number;
  processingFeePercent: number;
  penaltyPercent: number;
  minAmount: number;
  maxAmount: number | null;
  minTermMonths: number;
  maxTermMonths: number;
  active: boolean;
  displayOrder?: number;
}

const LOAN_TYPES = [
  "PERSONAL",
  "BUSINESS",
  "MORTGAGE",
  "AUTO",
  "STUDENT",
  "EMERGENCY",
  "ASSET_FINANCE",
  "SALARY_ADVANCE",
  "MICROFINANCE",
  "AGRICULTURAL",
  "TRADE_FINANCE",
  "GROUP",
] as const;

/**
 * These are defaults for creating a new product.
 *
 * They are NOT platform-enforced pricing rules.
 * The organization admin may change these before saving.
 *
 * Noble's current default:
 * 5% monthly interest
 * 5% monthly management fee
 * 2% application fee
 */
const DEFAULT_INTEREST_RATE = 5;
const DEFAULT_MANAGEMENT_FEE = 5;
const DEFAULT_PROCESSING_FEE = 2;
const DEFAULT_PENALTY_RATE = 15;
const DEFAULT_MIN_AMOUNT = 500000;
const DEFAULT_MIN_TERM = 1;
const DEFAULT_MAX_TERM = 6;

const emptyForm = (): ProductForm => ({
  id: undefined,
  name: "",
  icon: "💰",
  description: "",
  loanType: "PERSONAL",
  interestRate: DEFAULT_INTEREST_RATE,
  interestRateType: "MONTHLY",
  managementFeePercent: DEFAULT_MANAGEMENT_FEE,
  processingFeePercent: DEFAULT_PROCESSING_FEE,
  penaltyPercent: DEFAULT_PENALTY_RATE,
  minAmount: DEFAULT_MIN_AMOUNT,
  maxAmount: null,
  minTermMonths: DEFAULT_MIN_TERM,
  maxTermMonths: DEFAULT_MAX_TERM,
  active: true,
  displayOrder: 0,
});

const getMsg = (error: unknown): string => {
  if (error instanceof Error) {
    return error.message;
  }

  return "Something went wrong. Please try again.";
};

const numberValue = (value: unknown, fallback = 0): number => {
  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : fallback;
};

const money = (value: number | null | undefined): string => {
  if (value == null) {
    return "Unlimited";
  }

  return new Intl.NumberFormat("en-RW", {
    maximumFractionDigits: 0,
  }).format(value);
};

export default function LoanProductsPage() {
  const [products, setProducts] = useState<Product[]>([]);

  const [loading, setLoading] = useState(true);

  /**
   * Single consistent editing model.
   *
   * This fixes:
   *
   * Property 'id' does not exist on type 'ProductForm | ...'
   */
  const [editing, setEditing] = useState<ProductForm | null>(null);

  const [saving, setSaving] = useState(false);

  const [loadingAction, setLoadingAction] = useState<number | null>(null);

  // ============================================================
  // LOAD PRODUCTS
  // ============================================================

  const load = async (): Promise<void> => {
    setLoading(true);

    try {
      const response = await get("/loan-products");

      const list = Array.isArray(response) ? response : [];

      setProducts(
        list.filter(
          (item): item is Product =>
            item != null &&
            typeof item === "object" &&
            typeof item.name === "string" &&
            typeof item.loanType === "string",
        ),
      );
    } catch (error) {
      toast("error", getMsg(error));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  // ============================================================
  // CREATE
  // ============================================================

  const openNew = (): void => {
    setEditing(emptyForm());
  };

  // ============================================================
  // EDIT
  // ============================================================

  const openEdit = (product: Product): void => {
    setEditing({
      id: product.id,

      name: product.name ?? "",

      icon: product.icon ?? "💰",

      description: product.description ?? "",

      loanType: product.loanType ?? "PERSONAL",

      interestRate: numberValue(product.interestRate, DEFAULT_INTEREST_RATE),

      interestRateType: "MONTHLY",

      managementFeePercent: numberValue(
        product.managementFeePercent,
        DEFAULT_MANAGEMENT_FEE,
      ),

      processingFeePercent: numberValue(
        product.processingFeePercent,
        DEFAULT_PROCESSING_FEE,
      ),

      penaltyPercent: numberValue(product.penaltyPercent, DEFAULT_PENALTY_RATE),

      minAmount: numberValue(product.minAmount, DEFAULT_MIN_AMOUNT),

      maxAmount:
        product.maxAmount == null ? null : numberValue(product.maxAmount),

      minTermMonths: numberValue(product.minTermMonths, DEFAULT_MIN_TERM),

      maxTermMonths: numberValue(product.maxTermMonths, DEFAULT_MAX_TERM),

      active: product.active !== false,

      displayOrder: product.displayOrder ?? 0,
    });
  };

  // ============================================================
  // UPDATE FORM
  // ============================================================

  const updateField = <K extends keyof ProductForm>(
    key: K,
    value: ProductForm[K],
  ): void => {
    setEditing((current) => {
      if (!current) {
        return current;
      }

      return {
        ...current,
        [key]: value,
      };
    });
  };

  // ============================================================
  // VALIDATION
  // ============================================================

  const validateForm = (): string | null => {
    if (!editing) {
      return "Product form is not open.";
    }

    if (!editing.name.trim()) {
      return "Product name is required.";
    }

    if (!Number.isFinite(editing.interestRate) || editing.interestRate < 0) {
      return "Interest rate cannot be negative.";
    }

    if (
      !Number.isFinite(editing.managementFeePercent) ||
      editing.managementFeePercent < 0
    ) {
      return "Management fee cannot be negative.";
    }

    if (
      !Number.isFinite(editing.processingFeePercent) ||
      editing.processingFeePercent < 0
    ) {
      return "Processing fee cannot be negative.";
    }

    if (
      !Number.isFinite(editing.penaltyPercent) ||
      editing.penaltyPercent < 0
    ) {
      return "Penalty rate cannot be negative.";
    }

    if (
      !Number.isFinite(editing.minAmount) ||
      editing.minAmount < DEFAULT_MIN_AMOUNT
    ) {
      return `Minimum loan amount cannot be below ${money(
        DEFAULT_MIN_AMOUNT,
      )} RWF.`;
    }

    if (
      editing.maxAmount != null &&
      (!Number.isFinite(editing.maxAmount) ||
        editing.maxAmount < editing.minAmount)
    ) {
      return "Maximum loan amount cannot be below the minimum amount.";
    }

    if (
      !Number.isFinite(editing.minTermMonths) ||
      editing.minTermMonths < DEFAULT_MIN_TERM
    ) {
      return "Minimum term must be at least 1 month.";
    }

    if (
      !Number.isFinite(editing.maxTermMonths) ||
      editing.maxTermMonths > DEFAULT_MAX_TERM
    ) {
      return "Maximum term cannot exceed 6 months.";
    }

    if (editing.maxTermMonths < editing.minTermMonths) {
      return "Maximum term cannot be below the minimum term.";
    }

    return null;
  };

  // ============================================================
  // SAVE
  // ============================================================

  const handleSave = async (): Promise<void> => {
    if (!editing) {
      return;
    }

    const validationError = validateForm();

    if (validationError) {
      toast("error", validationError);

      return;
    }

    setSaving(true);

    /*
     * Only send the fields supported by the
     * backend product API.
     *
     * Do not send the optional frontend-only id
     * back inside the JSON payload.
     */
    const payload = {
      name: editing.name.trim(),

      icon: editing.icon.trim() || "💰",

      description: editing.description.trim(),

      loanType: editing.loanType,

      interestRate: numberValue(editing.interestRate),

      interestRateType: "MONTHLY" as const,

      managementFeePercent: numberValue(editing.managementFeePercent),

      processingFeePercent: numberValue(editing.processingFeePercent),

      penaltyPercent: numberValue(editing.penaltyPercent),

      minAmount: numberValue(editing.minAmount),

      maxAmount:
        editing.maxAmount == null ? null : numberValue(editing.maxAmount),

      minTermMonths: Math.round(numberValue(editing.minTermMonths)),

      maxTermMonths: Math.round(numberValue(editing.maxTermMonths)),

      active: editing.active !== false,

      displayOrder: numberValue(editing.displayOrder, 0),
    };

    try {
      if (editing.id != null) {
        await put(`/loan-products/${editing.id}`, payload);

        toast("success", "Loan product updated successfully.");
      } else {
        await post("/loan-products", payload);

        toast("success", "Loan product created successfully.");
      }

      setEditing(null);

      await load();
    } catch (error) {
      toast("error", getMsg(error));
    } finally {
      setSaving(false);
    }
  };

  // ============================================================
  // TOGGLE
  // ============================================================

  const handleToggle = async (product: Product): Promise<void> => {
    if (product.id == null) {
      toast("error", "This product has no valid ID.");

      return;
    }

    setLoadingAction(product.id);

    try {
      await post(`/loan-products/${product.id}/toggle`);

      toast(
        "success",
        product.active
          ? "Product disabled for new loans."
          : "Product activated for new loans.",
      );

      await load();
    } catch (error) {
      toast("error", getMsg(error));
    } finally {
      setLoadingAction(null);
    }
  };

  // ============================================================
  // DELETE
  // ============================================================

  const handleDelete = async (product: Product): Promise<void> => {
    if (product.id == null) {
      toast("error", "This product has no valid ID.");

      return;
    }

    const confirmed = window.confirm(
      `Delete "${product.name}"? This should only be used for products that are not referenced by production loans.`,
    );

    if (!confirmed) {
      return;
    }

    setLoadingAction(product.id);

    try {
      await del(`/loan-products/${product.id}`);

      toast("success", "Loan product deleted.");

      await load();
    } catch (error) {
      toast("error", getMsg(error));
    } finally {
      setLoadingAction(null);
    }
  };

  // ============================================================
  // COUNTERS
  // ============================================================

  const activeCount = useMemo(
    () => products.filter((product) => product.active).length,
    [products],
  );

  // ============================================================
  // LOADING
  // ============================================================

  if (loading) {
    return <PageSpinner />;
  }

  // ============================================================
  // PAGE
  // ============================================================

  return (
    <div className="space-y-6 pb-10">
      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-teal-600">
              Organization pricing
            </p>

            <h1 className="mt-1 text-2xl font-black tracking-tight text-slate-950">
              Loan Products
            </h1>

            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
              Configure the pricing your organization offers. Interest and
              management fees are stored on each product and become the
              contractual rates for new loans. Existing loans keep their saved
              pricing.
            </p>
          </div>

          <button
            type="button"
            onClick={openNew}
            className="rounded-xl bg-teal-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-teal-700"
          >
            + New Product
          </button>
        </div>

        <div className="mt-5 flex flex-wrap gap-2 text-xs font-semibold">
          <span className="rounded-full bg-slate-100 px-3 py-1.5 text-slate-600">
            {products.length} product
            {products.length === 1 ? "" : "s"}
          </span>

          <span className="rounded-full bg-teal-50 px-3 py-1.5 text-teal-700">
            {activeCount} active
          </span>

          <span className="rounded-full bg-amber-50 px-3 py-1.5 text-amber-700">
            Monthly pricing only
          </span>
        </div>
      </section>

      {products.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-16 text-center shadow-sm">
          <div className="text-4xl">💰</div>

          <p className="mt-3 font-semibold text-slate-800">
            No loan products configured
          </p>

          <p className="mx-auto mt-1 max-w-lg text-sm text-slate-500">
            Create the products your organization actually offers. The same
            product configuration powers new-loan validation and public website
            pricing.
          </p>

          <button
            type="button"
            onClick={openNew}
            className="mt-5 rounded-xl bg-teal-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-teal-700"
          >
            Create your first product
          </button>
        </div>
      ) : (
        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="min-w-[1100px] w-full text-sm">
              <thead className="bg-slate-50 text-[10px] font-bold uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-3 text-left">Product</th>

                  <th className="px-4 py-3 text-left">Type</th>

                  <th className="px-4 py-3 text-right">Interest</th>

                  <th className="px-4 py-3 text-right">Management</th>

                  <th className="px-4 py-3 text-right">Processing</th>

                  <th className="px-4 py-3 text-right">Amount</th>

                  <th className="px-4 py-3 text-right">Term</th>

                  <th className="px-4 py-3 text-left">Status</th>

                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>

              <tbody className="divide-y divide-slate-100">
                {products.map((product) => (
                  <tr
                    key={product.id ?? `${product.loanType}-${product.name}`}
                    className={`transition hover:bg-slate-50 ${
                      !product.active ? "opacity-60" : ""
                    }`}
                  >
                    <td className="px-4 py-4">
                      <div className="flex items-center gap-3">
                        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-slate-100 text-lg">
                          {product.icon || "💰"}
                        </div>

                        <div>
                          <div className="font-semibold text-slate-800">
                            {product.name}
                          </div>

                          <div className="mt-0.5 text-xs text-slate-400">
                            {product.description || "No description"}
                          </div>
                        </div>
                      </div>
                    </td>

                    <td className="px-4 py-4 text-xs text-slate-500">
                      {LOAN_TYPE_META[product.loanType]?.label ??
                        product.loanType.replace(/_/g, " ")}
                    </td>

                    <td className="px-4 py-4 text-right">
                      <span className="font-bold text-blue-700">
                        {product.interestRate}%
                      </span>

                      <span className="ml-1 text-xs text-slate-400">/mo</span>
                    </td>

                    <td className="px-4 py-4 text-right">
                      <span className="font-bold text-purple-700">
                        {product.managementFeePercent}%
                      </span>

                      <span className="ml-1 text-xs text-slate-400">/mo</span>
                    </td>

                    <td className="px-4 py-4 text-right text-slate-600">
                      {product.processingFeePercent}%
                    </td>

                    <td className="px-4 py-4 text-right text-xs text-slate-600">
                      {money(product.minAmount)} – {money(product.maxAmount)}
                    </td>

                    <td className="px-4 py-4 text-right text-xs text-slate-600">
                      {product.minTermMonths}–{product.maxTermMonths} mo
                    </td>

                    <td className="px-4 py-4">
                      <button
                        type="button"
                        disabled={loadingAction === product.id}
                        onClick={() => void handleToggle(product)}
                      >
                        <Pill
                          label={product.active ? "Active" : "Inactive"}
                          color={product.active ? "green" : "gray"}
                        />
                      </button>
                    </td>

                    <td className="px-4 py-4 text-right">
                      <div className="flex justify-end gap-3">
                        <button
                          type="button"
                          onClick={() => openEdit(product)}
                          className="text-xs font-semibold text-teal-700 hover:underline"
                        >
                          Edit pricing
                        </button>

                        <button
                          type="button"
                          disabled={loadingAction === product.id}
                          onClick={() => void handleDelete(product)}
                          className="text-xs font-semibold text-red-600 hover:underline disabled:opacity-50"
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {editing && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4 backdrop-blur-sm"
          onMouseDown={() => setEditing(null)}
        >
          <div
            className="max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-3xl bg-white p-6 shadow-2xl"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-teal-600">
                  Contract pricing
                </p>

                <h2 className="mt-1 text-xl font-black text-slate-950">
                  {editing.id != null
                    ? "Edit loan product"
                    : "Create loan product"}
                </h2>

                <p className="mt-1 text-xs leading-5 text-slate-500">
                  These settings apply to new loans created from this product.
                  Changing them does not reprice existing loans.
                </p>
              </div>

              <button
                type="button"
                onClick={() => setEditing(null)}
                className="rounded-lg px-2 py-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                aria-label="Close"
              >
                ×
              </button>
            </div>

            <div className="mt-6 space-y-5">
              <div className="grid grid-cols-4 gap-3">
                <input
                  className="col-span-1 rounded-xl border border-slate-200 p-3 text-center text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                  placeholder="Icon"
                  value={editing.icon ?? ""}
                  onChange={(event) => updateField("icon", event.target.value)}
                />

                <input
                  className="col-span-3 rounded-xl border border-slate-200 p-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                  placeholder="Product name"
                  value={editing.name ?? ""}
                  onChange={(event) => updateField("name", event.target.value)}
                />
              </div>

              <textarea
                className="w-full resize-none rounded-xl border border-slate-200 p-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                rows={3}
                placeholder="Description shown on the public website"
                value={editing.description ?? ""}
                onChange={(event) =>
                  updateField("description", event.target.value)
                }
              />

              <div>
                <label className="text-xs font-bold uppercase tracking-wider text-slate-500">
                  Loan Type
                </label>

                <select
                  className="mt-1 w-full rounded-xl border border-slate-200 p-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                  value={editing.loanType ?? "PERSONAL"}
                  onChange={(event) =>
                    updateField("loanType", event.target.value)
                  }
                >
                  {LOAN_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {LOAN_TYPE_META[type]?.label ?? type.replace(/_/g, " ")}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div className="rounded-2xl border border-blue-100 bg-blue-50/60 p-4">
                  <label className="text-[10px] font-bold uppercase tracking-wider text-blue-600">
                    Monthly Interest Rate
                  </label>

                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    className="mt-2 w-full rounded-xl border border-blue-200 bg-white p-3 text-lg font-bold outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                    value={editing.interestRate ?? 0}
                    onChange={(event) =>
                      updateField(
                        "interestRate",
                        numberValue(event.target.value),
                      )
                    }
                  />

                  <p className="mt-2 text-xs text-blue-700">
                    Example: 5 means 5% per month. This is
                    organization-specific.
                  </p>
                </div>

                <div className="rounded-2xl border border-purple-100 bg-purple-50/60 p-4">
                  <label className="text-[10px] font-bold uppercase tracking-wider text-purple-600">
                    Monthly Management Fee
                  </label>

                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    className="mt-2 w-full rounded-xl border border-purple-200 bg-white p-3 text-lg font-bold outline-none focus:border-purple-500 focus:ring-2 focus:ring-purple-100"
                    value={editing.managementFeePercent ?? 0}
                    onChange={(event) =>
                      updateField(
                        "managementFeePercent",
                        numberValue(event.target.value),
                      )
                    }
                  />

                  <p className="mt-2 text-xs text-purple-700">
                    Example: 5 means 5% per month. It is charged separately from
                    interest.
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                <div>
                  <label className="text-xs font-bold text-slate-500">
                    Processing Fee (%)
                  </label>

                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    className="mt-1 w-full rounded-xl border border-slate-200 p-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                    value={editing.processingFeePercent ?? 0}
                    onChange={(event) =>
                      updateField(
                        "processingFeePercent",
                        numberValue(event.target.value),
                      )
                    }
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-500">
                    Penalty (%)
                  </label>

                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    className="mt-1 w-full rounded-xl border border-slate-200 p-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                    value={editing.penaltyPercent ?? DEFAULT_PENALTY_RATE}
                    onChange={(event) =>
                      updateField(
                        "penaltyPercent",
                        numberValue(event.target.value),
                      )
                    }
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-500">
                    Minimum Amount
                  </label>

                  <input
                    type="number"
                    min={DEFAULT_MIN_AMOUNT}
                    className="mt-1 w-full rounded-xl border border-slate-200 p-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                    value={editing.minAmount ?? DEFAULT_MIN_AMOUNT}
                    onChange={(event) =>
                      updateField("minAmount", numberValue(event.target.value))
                    }
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label className="text-xs font-bold text-slate-500">
                    Maximum Amount
                  </label>

                  <input
                    type="number"
                    min={DEFAULT_MIN_AMOUNT}
                    disabled={editing.maxAmount === null}
                    className="mt-1 w-full rounded-xl border border-slate-200 p-3 text-sm outline-none disabled:bg-slate-100 disabled:text-slate-400 focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                    value={editing.maxAmount == null ? "" : editing.maxAmount}
                    placeholder={editing.maxAmount == null ? "Unlimited" : ""}
                    onChange={(event) =>
                      updateField(
                        "maxAmount",
                        event.target.value === ""
                          ? null
                          : numberValue(event.target.value),
                      )
                    }
                  />

                  <label className="mt-2 flex cursor-pointer items-center gap-2 text-xs text-slate-500">
                    <input
                      type="checkbox"
                      checked={editing.maxAmount === null}
                      onChange={(event) =>
                        updateField(
                          "maxAmount",
                          event.target.checked ? null : DEFAULT_MIN_AMOUNT,
                        )
                      }
                    />
                    Unlimited maximum amount
                  </label>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs font-bold text-slate-500">
                      Minimum Term
                    </label>

                    <input
                      type="number"
                      min={DEFAULT_MIN_TERM}
                      max={DEFAULT_MAX_TERM}
                      className="mt-1 w-full rounded-xl border border-slate-200 p-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                      value={editing.minTermMonths ?? DEFAULT_MIN_TERM}
                      onChange={(event) =>
                        updateField(
                          "minTermMonths",
                          Math.round(numberValue(event.target.value)),
                        )
                      }
                    />
                  </div>

                  <div>
                    <label className="text-xs font-bold text-slate-500">
                      Maximum Term
                    </label>

                    <input
                      type="number"
                      min={DEFAULT_MIN_TERM}
                      max={DEFAULT_MAX_TERM}
                      className="mt-1 w-full rounded-xl border border-slate-200 p-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                      value={editing.maxTermMonths ?? DEFAULT_MAX_TERM}
                      onChange={(event) =>
                        updateField(
                          "maxTermMonths",
                          Math.round(numberValue(event.target.value)),
                        )
                      }
                    />
                  </div>
                </div>
              </div>

              <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
                <input
                  type="checkbox"
                  checked={editing.active !== false}
                  onChange={(event) =>
                    updateField("active", event.target.checked)
                  }
                />
                Active — visible on the public website and available for new
                loans
              </label>

              <div className="flex flex-col-reverse gap-2 border-t border-slate-100 pt-4 sm:flex-row">
                <button
                  type="button"
                  onClick={() => setEditing(null)}
                  className="rounded-xl border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>

                <button
                  type="button"
                  onClick={() => void handleSave()}
                  disabled={saving}
                  className="flex-1 rounded-xl bg-teal-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-teal-700 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {saving
                    ? "Saving…"
                    : editing.id != null
                      ? "Save Product Changes"
                      : "Create Product"}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

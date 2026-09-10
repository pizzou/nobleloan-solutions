"use client";

import React, { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";

import { getBorrowerById, updateBorrower } from "../../../../../services/borrowerService";
import { toast } from "../../../../../hooks/useToast";
import { Borrower } from "../../../../../types";
import { Card } from "../../../../../components/ui/Card";
import { Button } from "../../../../../components/ui/Button";

const RWANDA_DEFAULT_NATIONALITY = "Rwandan";
const RWANDA_DEFAULT_COUNTRY = "Rwanda";
const RWANDA_PHONE_PATTERN = /^(?:\+250\s?7\d{2}\s?\d{3}\s?\d{3}|07\d{2}\s?\d{3}\s?\d{3})$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const NAME_PATTERN = /^[A-Za-zÀ-ÿ' -]+$/;

type FormState = {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  alternatePhone: string;
  nationalId: string;
  passportNumber: string;
  taxIdentificationNumber: string;
  dateOfBirth: string;
  gender: string;
  maritalStatus: string;
  nationality: string;
  placeOfBirth: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  stateProvince: string;
  physicalAddressProvince: string;
  physicalAddressDistrict: string;
  physicalAddressSector: string;
  physicalAddressCell: string;
  physicalAddressVillage: string;
  postalCode: string;
  country: string;
  employerName: string;
  employmentType: string;
  jobTitle: string;
  monthlyIncome: string;
  monthlyExpenses: string;
  netWorth: string;
  creditScore: string;
  creditBureau: string;
  bankName: string;
  bankAccountNumber: string;
  bankBranch: string;
};

type Errors = Record<string, string>;

const emptyForm: FormState = {
  firstName: "",
  lastName: "",
  email: "",
  phone: "",
  alternatePhone: "",
  nationalId: "",
  passportNumber: "",
  taxIdentificationNumber: "",
  dateOfBirth: "",
  gender: "",
  maritalStatus: "",
  nationality: RWANDA_DEFAULT_NATIONALITY,
  placeOfBirth: "",
  addressLine1: "",
  addressLine2: "",
  city: "",
  stateProvince: "",
  physicalAddressProvince: "",
  physicalAddressDistrict: "",
  physicalAddressSector: "",
  physicalAddressCell: "",
  physicalAddressVillage: "",
  postalCode: "",
  country: RWANDA_DEFAULT_COUNTRY,
  employerName: "",
  employmentType: "",
  jobTitle: "",
  monthlyIncome: "",
  monthlyExpenses: "",
  netWorth: "",
  creditScore: "",
  creditBureau: "",
  bankName: "",
  bankAccountNumber: "",
  bankBranch: "",
};

function value(v: unknown): string {
  return v == null ? "" : String(v);
}

function toDateInput(v: unknown): string {
  const s = value(v);
  return s.length >= 10 ? s.slice(0, 10) : s;
}

function normalizeNationalId(v: string) {
  return v.replace(/\D/g, "").slice(0, 16);
}

function normalizePhone(v: string) {
  const cleaned = v.replace(/[^\d+]/g, "");
  if (cleaned.startsWith("+250")) return `+250${cleaned.slice(4)}`;
  return cleaned;
}

function fromBorrower(b: Borrower): FormState {
  return {
    firstName: value(b.firstName),
    lastName: value(b.lastName),
    email: value(b.email),
    phone: value(b.phone),
    alternatePhone: value(b.alternatePhone),
    nationalId: value(b.nationalId),
    passportNumber: value(b.passportNumber),
    taxIdentificationNumber: value(b.taxIdentificationNumber),
    dateOfBirth: toDateInput(b.dateOfBirth),
    gender: value(b.gender),
    maritalStatus: value(b.maritalStatus),
    nationality: value(b.nationality) || RWANDA_DEFAULT_NATIONALITY,
    placeOfBirth: value(b.placeOfBirth),
    addressLine1: value(b.addressLine1 || b.address),
    addressLine2: value(b.addressLine2),
    city: value(b.city),
    stateProvince: value(b.stateProvince),
    physicalAddressProvince: value(b.physicalAddressProvince || b.stateProvince),
    physicalAddressDistrict: value(b.physicalAddressDistrict),
    physicalAddressSector: value(b.physicalAddressSector),
    physicalAddressCell: value(b.physicalAddressCell),
    physicalAddressVillage: value(b.physicalAddressVillage),
    postalCode: value(b.postalCode),
    country: value(b.country) || RWANDA_DEFAULT_COUNTRY,
    employerName: value(b.employerName),
    employmentType: value(b.employmentType),
    jobTitle: value(b.jobTitle),
    monthlyIncome: value(b.monthlyIncome),
    monthlyExpenses: value(b.monthlyExpenses),
    netWorth: value(b.netWorth),
    creditScore: value(b.creditScore),
    creditBureau: value(b.creditBureau),
    bankName: value(b.bankName),
    bankAccountNumber: value(b.bankAccountNumber),
    bankBranch: value(b.bankBranch),
  };
}

function Field({
  label,
  required,
  error,
  children,
}: {
  label: string;
  required?: boolean;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-bold text-slate-700">
        {label} {required && <span className="text-red-600">*</span>}
      </span>
      {children}
      {error && <span className="mt-1 block text-xs font-semibold text-red-600">{error}</span>}
    </label>
  );
}

function inputClass(error?: string) {
  return `w-full rounded-xl border bg-white px-3.5 py-2.5 text-sm text-slate-900 outline-none transition focus:ring-2 focus:ring-teal-500/20 ${error ? "border-red-400" : "border-slate-200"}`;
}

export default function EditBorrowerPage() {
  const params = useParams();
  const router = useRouter();
  const borrowerId = Number(params?.id);

  const [form, setForm] = useState<FormState>(emptyForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [original, setOriginal] = useState<Borrower | null>(null);

  useEffect(() => {
    if (!borrowerId || Number.isNaN(borrowerId)) {
      setError("Invalid borrower ID.");
      setLoading(false);
      return;
    }

    let cancelled = false;
    (async () => {
      try {
        const borrower = await getBorrowerById(borrowerId);
        if (!cancelled) {
          setOriginal(borrower);
          setForm(fromBorrower(borrower));
        }
      } catch (e: any) {
        if (!cancelled) setError(e?.message || "Unable to load borrower profile.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [borrowerId]);

  const set = (key: keyof FormState, next: string) => {
    setForm((current) => ({ ...current, [key]: next }));
    if (errors[key]) setErrors((current) => ({ ...current, [key]: "" }));
  };

  const dirty = useMemo(() => {
    if (!original) return false;
    return JSON.stringify(form) !== JSON.stringify(fromBorrower(original));
  }, [form, original]);

  const validate = () => {
    const e: Errors = {};
    const first = form.firstName.trim();
    const last = form.lastName.trim();

    if (!first) e.firstName = "First name is required.";
    else if (!NAME_PATTERN.test(first)) e.firstName = "First name contains invalid characters.";
    if (!last) e.lastName = "Last name is required.";
    else if (!NAME_PATTERN.test(last)) e.lastName = "Last name contains invalid characters.";
    if (!/^\d{16}$/.test(normalizeNationalId(form.nationalId))) e.nationalId = "National ID must contain exactly 16 digits.";
    if (!form.phone.trim() || !RWANDA_PHONE_PATTERN.test(form.phone.trim())) e.phone = "Enter a valid Rwanda phone number.";
    if (!form.alternatePhone.trim()) e.alternatePhone = "Alternate phone is required by the borrower profile policy.";
    if (form.email.trim() && !EMAIL_PATTERN.test(form.email.trim())) e.email = "Enter a valid email address.";
    if (!form.dateOfBirth) e.dateOfBirth = "Date of birth is required.";
    if (!form.gender.trim()) e.gender = "Gender is required.";
    if (!form.maritalStatus.trim()) e.maritalStatus = "Marital status is required.";
    if (!form.placeOfBirth.trim()) e.placeOfBirth = "Place of birth is required for CRB reporting.";
    if (!form.addressLine1.trim()) e.addressLine1 = "Physical address line 1 is required.";
    if (!form.physicalAddressProvince.trim()) e.physicalAddressProvince = "Province is required for CRB reporting.";
    if (!form.physicalAddressDistrict.trim()) e.physicalAddressDistrict = "District is required for CRB reporting.";
    if (!form.physicalAddressSector.trim()) e.physicalAddressSector = "Sector is required for CRB reporting.";
    if (!form.physicalAddressCell.trim()) e.physicalAddressCell = "Cell is required for CRB reporting.";
    if (!form.country.trim()) e.country = "Country is required.";

    if (form.creditScore.trim()) {
      const score = Number(form.creditScore);
      if (!Number.isInteger(score) || score < 0 || score > 1000) e.creditScore = "Credit score must be between 0 and 1000.";
    }

    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    setError("");
    if (!validate() || saving) return;

    setSaving(true);
    try {
      await updateBorrower(borrowerId, {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        phone: normalizePhone(form.phone),
        alternatePhone: normalizePhone(form.alternatePhone),
        nationalId: normalizeNationalId(form.nationalId),
        passportNumber: form.passportNumber.trim() || undefined,
        taxIdentificationNumber: form.taxIdentificationNumber.trim() || undefined,
        dateOfBirth: form.dateOfBirth,
        gender: form.gender.trim(),
        maritalStatus: form.maritalStatus.trim(),
        nationality: form.nationality.trim() || RWANDA_DEFAULT_NATIONALITY,
        placeOfBirth: form.placeOfBirth.trim(),
        address: form.addressLine1.trim(),
        addressLine1: form.addressLine1.trim(),
        addressLine2: form.addressLine2.trim() || undefined,
        city: form.city.trim() || undefined,
        stateProvince: form.stateProvince.trim() || undefined,
        physicalAddressProvince: form.physicalAddressProvince.trim(),
        physicalAddressDistrict: form.physicalAddressDistrict.trim(),
        physicalAddressSector: form.physicalAddressSector.trim(),
        physicalAddressCell: form.physicalAddressCell.trim(),
        physicalAddressVillage: form.physicalAddressVillage.trim() || undefined,
        postalCode: form.postalCode.trim() || undefined,
        country: form.country.trim() || RWANDA_DEFAULT_COUNTRY,
        employerName: form.employerName.trim() || undefined,
        employmentType: form.employmentType.trim() || undefined,
        jobTitle: form.jobTitle.trim() || undefined,
        monthlyIncome: form.monthlyIncome.trim() ? Number(form.monthlyIncome) : undefined,
        monthlyExpenses: form.monthlyExpenses.trim() ? Number(form.monthlyExpenses) : undefined,
        netWorth: form.netWorth.trim() ? Number(form.netWorth) : undefined,
        creditScore: form.creditScore.trim() ? Number(form.creditScore) : undefined,
        creditBureau: form.creditBureau.trim() || undefined,
        bankName: form.bankName.trim() || undefined,
        bankAccountNumber: form.bankAccountNumber.trim() || undefined,
        bankBranch: form.bankBranch.trim() || undefined,
      });

      toast("success", "Borrower profile updated successfully.");
      router.push(`/dashboard/borrowers/${borrowerId}`);
    } catch (e: any) {
      setError(e?.message || "Unable to update borrower profile.");
      window.scrollTo({ top: 0, behavior: "smooth" });
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="min-h-[500px] flex items-center justify-center text-sm font-semibold text-slate-500">Loading borrower profile...</div>;

  if (error && !original) {
    return <div className="mx-auto max-w-2xl p-8"><Card><div className="p-8 text-center"><h1 className="text-xl font-bold text-slate-900">Unable to load borrower</h1><p className="mt-2 text-sm text-red-600">{error}</p><div className="mt-6"><Button variant="secondary" onClick={() => router.back()}>Back</Button></div></div></Card></div>;
  }

  return (
    <main className="min-h-full bg-slate-50 px-4 py-6 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <button className="text-sm font-bold text-slate-500 hover:text-teal-700" onClick={() => router.push(`/dashboard/borrowers/${borrowerId}`)}>← Borrower Profile</button>
            <h1 className="mt-2 text-2xl font-extrabold tracking-tight text-slate-950">Edit Borrower Profile</h1>
            <p className="mt-1 text-sm text-slate-500">Maintain the complete customer identity, CRB address and financial profile before loan creation.</p>
          </div>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => router.push(`/dashboard/borrowers/${borrowerId}`)}>Cancel</Button>
            <Button type="submit" form="borrower-edit-form" loading={saving} disabled={!dirty}>Save Changes</Button>
          </div>
        </div>

        {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">{error}</div>}

        <form id="borrower-edit-form" onSubmit={save} className="space-y-6">
          <Card><div className="border-b border-slate-100 px-6 py-5"><h2 className="font-extrabold text-slate-900">Identity & Contact</h2><p className="mt-1 text-xs text-slate-500">Core borrower identity used throughout lending and credit-bureau reporting.</p></div><div className="grid gap-5 p-6 sm:grid-cols-2 lg:grid-cols-3">
            <Field label="First Name" required error={errors.firstName}><input value={form.firstName} onChange={e => set("firstName", e.target.value)} className={inputClass(errors.firstName)} /></Field>
            <Field label="Last Name" required error={errors.lastName}><input value={form.lastName} onChange={e => set("lastName", e.target.value)} className={inputClass(errors.lastName)} /></Field>
            <Field label="National ID" required error={errors.nationalId}><input value={form.nationalId} onChange={e => set("nationalId", normalizeNationalId(e.target.value))} maxLength={16} className={inputClass(errors.nationalId)} /></Field>
            <Field label="Phone" required error={errors.phone}><input value={form.phone} onChange={e => set("phone", e.target.value)} className={inputClass(errors.phone)} /></Field>
            <Field label="Alternate Phone" required error={errors.alternatePhone}><input value={form.alternatePhone} onChange={e => set("alternatePhone", e.target.value)} className={inputClass(errors.alternatePhone)} /></Field>
            <Field label="Email" error={errors.email}><input type="email" value={form.email} onChange={e => set("email", e.target.value)} className={inputClass(errors.email)} /></Field>
            <Field label="Date of Birth" required error={errors.dateOfBirth}><input type="date" value={form.dateOfBirth} onChange={e => set("dateOfBirth", e.target.value)} className={inputClass(errors.dateOfBirth)} /></Field>
            <Field label="Gender" required error={errors.gender}><select value={form.gender} onChange={e => set("gender", e.target.value)} className={inputClass(errors.gender)}><option value="">Select gender</option><option value="MALE">Male</option><option value="FEMALE">Female</option><option value="OTHER">Other</option></select></Field>
            <Field label="Marital Status" required error={errors.maritalStatus}><select value={form.maritalStatus} onChange={e => set("maritalStatus", e.target.value)} className={inputClass(errors.maritalStatus)}><option value="">Select status</option><option value="SINGLE">Single</option><option value="MARRIED">Married</option><option value="DIVORCED">Divorced</option><option value="WIDOWED">Widowed</option></select></Field>
            <Field label="Nationality" required><input value={form.nationality} onChange={e => set("nationality", e.target.value)} placeholder="Rwandan" className={inputClass()} /></Field>
            <Field label="Place of Birth" required error={errors.placeOfBirth}><input value={form.placeOfBirth} onChange={e => set("placeOfBirth", e.target.value)} placeholder="City / District / Country" className={inputClass(errors.placeOfBirth)} /></Field>
            <Field label="Passport Number"><input value={form.passportNumber} onChange={e => set("passportNumber", e.target.value)} className={inputClass()} /></Field>
            <Field label="Tax Identification Number"><input value={form.taxIdentificationNumber} onChange={e => set("taxIdentificationNumber", e.target.value)} className={inputClass()} /></Field>
          </div></Card>

          <Card><div className="border-b border-slate-100 px-6 py-5"><h2 className="font-extrabold text-slate-900">Physical Address & CRB Data</h2><p className="mt-1 text-xs text-slate-500">These fields are maintained explicitly so regulatory exports do not depend on ambiguous free-text addresses.</p></div><div className="grid gap-5 p-6 sm:grid-cols-2 lg:grid-cols-3">
            <Field label="Physical Address Line 1" required error={errors.addressLine1}><input value={form.addressLine1} onChange={e => set("addressLine1", e.target.value)} className={inputClass(errors.addressLine1)} /></Field>
            <Field label="Physical Address Line 2"><input value={form.addressLine2} onChange={e => set("addressLine2", e.target.value)} className={inputClass()} /></Field>
            <Field label="City"><input value={form.city} onChange={e => set("city", e.target.value)} className={inputClass()} /></Field>
            <Field label="Province" required error={errors.physicalAddressProvince}><input value={form.physicalAddressProvince} onChange={e => set("physicalAddressProvince", e.target.value)} className={inputClass(errors.physicalAddressProvince)} /></Field>
            <Field label="District" required error={errors.physicalAddressDistrict}><input value={form.physicalAddressDistrict} onChange={e => set("physicalAddressDistrict", e.target.value)} className={inputClass(errors.physicalAddressDistrict)} /></Field>
            <Field label="Sector" required error={errors.physicalAddressSector}><input value={form.physicalAddressSector} onChange={e => set("physicalAddressSector", e.target.value)} className={inputClass(errors.physicalAddressSector)} /></Field>
            <Field label="Cell" required error={errors.physicalAddressCell}><input value={form.physicalAddressCell} onChange={e => set("physicalAddressCell", e.target.value)} className={inputClass(errors.physicalAddressCell)} /></Field>
            <Field label="Village"><input value={form.physicalAddressVillage} onChange={e => set("physicalAddressVillage", e.target.value)} className={inputClass()} /></Field>
            <Field label="Postal Code"><input value={form.postalCode} onChange={e => set("postalCode", e.target.value)} className={inputClass()} /></Field>
            <Field label="Country" required error={errors.country}><input value={form.country} onChange={e => set("country", e.target.value)} className={inputClass(errors.country)} /></Field>
            <Field label="State / Province (legacy)"><input value={form.stateProvince} onChange={e => set("stateProvince", e.target.value)} className={inputClass()} /></Field>
          </div></Card>

          <Card><div className="border-b border-slate-100 px-6 py-5"><h2 className="font-extrabold text-slate-900">Employment, Credit & Banking</h2><p className="mt-1 text-xs text-slate-500">Optional servicing data used for affordability, credit and customer-360 workflows.</p></div><div className="grid gap-5 p-6 sm:grid-cols-2 lg:grid-cols-3">
            <Field label="Employer"><input value={form.employerName} onChange={e => set("employerName", e.target.value)} className={inputClass()} /></Field>
            <Field label="Employment Type"><input value={form.employmentType} onChange={e => set("employmentType", e.target.value)} className={inputClass()} /></Field>
            <Field label="Job Title"><input value={form.jobTitle} onChange={e => set("jobTitle", e.target.value)} className={inputClass()} /></Field>
            <Field label="Monthly Income"><input type="number" min="0" step="0.01" value={form.monthlyIncome} onChange={e => set("monthlyIncome", e.target.value)} className={inputClass()} /></Field>
            <Field label="Monthly Expenses"><input type="number" min="0" step="0.01" value={form.monthlyExpenses} onChange={e => set("monthlyExpenses", e.target.value)} className={inputClass()} /></Field>
            <Field label="Net Worth"><input type="number" min="0" step="0.01" value={form.netWorth} onChange={e => set("netWorth", e.target.value)} className={inputClass()} /></Field>
            <Field label="Credit Score" error={errors.creditScore}><input type="number" min="0" max="1000" value={form.creditScore} onChange={e => set("creditScore", e.target.value)} className={inputClass(errors.creditScore)} /></Field>
            <Field label="Credit Bureau"><input value={form.creditBureau} onChange={e => set("creditBureau", e.target.value)} className={inputClass()} /></Field>
            <Field label="Bank Name"><input value={form.bankName} onChange={e => set("bankName", e.target.value)} className={inputClass()} /></Field>
            <Field label="Bank Account Number"><input value={form.bankAccountNumber} onChange={e => set("bankAccountNumber", e.target.value)} className={inputClass()} /></Field>
            <Field label="Bank Branch"><input value={form.bankBranch} onChange={e => set("bankBranch", e.target.value)} className={inputClass()} /></Field>
          </div></Card>

          <div className="flex justify-end gap-3 pb-8"><Button variant="secondary" type="button" onClick={() => router.push(`/dashboard/borrowers/${borrowerId}`)}>Cancel</Button><Button type="submit" loading={saving}>Save Changes</Button></div>
        </form>
      </div>
    </main>
  );
}

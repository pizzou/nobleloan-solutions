"use client";

import { useEffect, useState } from "react";
import { get, put } from "@/services/api";
import { toast } from "@/hooks/useToast";
import { hasRole } from "@/services/authService";
import { PageSpinner } from "@/components/ui/Skeleton";

interface Organization {
  id: number;
  name: string;
  industry?: string;
  contactEmail?: string;
  contactPhone?: string;
  address?: string;
  defaultCurrency?: string;
  timezone?: string;
  website?: string;
  logoUrl?: string;
  primaryColor?: string;
  accentColor?: string;
  tagline?: string;
  mission?: string;
  vision?: string;
}

export default function OrganizationsPage() {
  const isAdmin = hasRole("ADMIN");

  const [organization, setOrganization] = useState<Organization | null>(null);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [name, setName] = useState("");
  const [industry, setIndustry] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [address, setAddress] = useState("");
  const [defaultCurrency, setDefaultCurrency] = useState("RWF");
  const [timezone, setTimezone] = useState("Africa/Kigali");
  const [website, setWebsite] = useState("");
  const [logoUrl, setLogoUrl] = useState("");
  const [primaryColor, setPrimaryColor] = useState("");
  const [accentColor, setAccentColor] = useState("");
  const [tagline, setTagline] = useState("");
  const [mission, setMission] = useState("");
  const [vision, setVision] = useState("");

  const loadOrganization = async () => {
    setLoading(true);

    try {
      const result = (await get("/organizations/me")) as Organization;

      setOrganization(result);

      setName(result.name ?? "");
      setIndustry(result.industry ?? "");
      setContactEmail(result.contactEmail ?? "");
      setContactPhone(result.contactPhone ?? "");
      setAddress(result.address ?? "");
      setDefaultCurrency(result.defaultCurrency ?? "RWF");
      setTimezone(result.timezone ?? "Africa/Kigali");
      setWebsite(result.website ?? "");
      setLogoUrl(result.logoUrl ?? "");
      setPrimaryColor(result.primaryColor ?? "");
      setAccentColor(result.accentColor ?? "");
      setTagline(result.tagline ?? "");
      setMission(result.mission ?? "");
      setVision(result.vision ?? "");
    } catch (error) {
      console.error(error);

      toast(
        "error",
        error instanceof Error
          ? error.message
          : "Unable to load organization settings.",
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadOrganization();
  }, []);

  const handleSave = async (event: React.FormEvent) => {
    event.preventDefault();

    if (!isAdmin) {
      toast(
        "error",
        "Only organization administrators can update organization settings.",
      );
      return;
    }

    if (!name.trim()) {
      toast("error", "Organization name is required.");
      return;
    }

    setSaving(true);

    try {
      const updated = (await put("/organizations/me", {
        name: name.trim(),
        contactEmail: contactEmail.trim() || undefined,
        contactPhone: contactPhone.trim() || undefined,
        address: address.trim() || undefined,
        defaultCurrency: defaultCurrency.trim() || "RWF",
        timezone: timezone.trim() || "Africa/Kigali",
        website: website.trim() || undefined,
        logoUrl: logoUrl.trim() || undefined,
        primaryColor: primaryColor.trim() || undefined,
        accentColor: accentColor.trim() || undefined,
        tagline: tagline.trim() || undefined,
        mission: mission.trim() || undefined,
        vision: vision.trim() || undefined,
      })) as Organization;

      setOrganization(updated);

      toast("success", "Organization settings updated successfully.");
    } catch (error) {
      console.error(error);

      toast(
        "error",
        error instanceof Error
          ? error.message
          : "Unable to update organization settings.",
      );
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <PageSpinner />;
  }

  if (!organization) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-6">
        <h1 className="text-lg font-bold text-red-800">
          Organization unavailable
        </h1>

        <p className="mt-2 text-sm text-red-700">
          The current user's organization could not be loaded.
        </p>

        <button
          type="button"
          onClick={loadOrganization}
          className="mt-4 rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700"
        >
          Try again
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold text-gray-900">
          Organization Settings
        </h1>

        <p className="mt-1 text-sm text-gray-500">
          Manage the organization associated with your account.
        </p>
      </div>

      <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <div className="mb-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-gray-400">
            Organization
          </p>

          <p className="mt-1 text-sm text-gray-500">
            Organization ID: #{organization.id}
          </p>
        </div>

        <form onSubmit={handleSave} className="space-y-6">
          <div className="grid gap-5 md:grid-cols-2">
            <Field
              label="Organization Name"
              value={name}
              onChange={setName}
              required
              disabled={!isAdmin}
            />

            <Field
              label="Industry"
              value={industry}
              onChange={setIndustry}
              disabled={!isAdmin}
            />

            <Field
              label="Contact Email"
              type="email"
              value={contactEmail}
              onChange={setContactEmail}
              disabled={!isAdmin}
            />

            <Field
              label="Contact Phone"
              value={contactPhone}
              onChange={setContactPhone}
              disabled={!isAdmin}
            />

            <Field
              label="Default Currency"
              value={defaultCurrency}
              onChange={setDefaultCurrency}
              disabled={!isAdmin}
            />

            <Field
              label="Timezone"
              value={timezone}
              onChange={setTimezone}
              disabled={!isAdmin}
            />

            <Field
              label="Website"
              value={website}
              onChange={setWebsite}
              disabled={!isAdmin}
            />

            <Field
              label="Logo URL"
              value={logoUrl}
              onChange={setLogoUrl}
              disabled={!isAdmin}
            />

            <Field
              label="Primary Color"
              value={primaryColor}
              onChange={setPrimaryColor}
              disabled={!isAdmin}
            />

            <Field
              label="Accent Color"
              value={accentColor}
              onChange={setAccentColor}
              disabled={!isAdmin}
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">
              Address
            </label>

            <textarea
              value={address}
              onChange={(event) => setAddress(event.target.value)}
              disabled={!isAdmin}
              rows={3}
              className="w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">
              Tagline
            </label>

            <input
              value={tagline}
              onChange={(event) => setTagline(event.target.value)}
              disabled={!isAdmin}
              className="w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">
              Mission
            </label>

            <textarea
              value={mission}
              onChange={(event) => setMission(event.target.value)}
              disabled={!isAdmin}
              rows={4}
              className="w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">
              Vision
            </label>

            <textarea
              value={vision}
              onChange={(event) => setVision(event.target.value)}
              disabled={!isAdmin}
              rows={4}
              className="w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:text-gray-500"
            />
          </div>

          {isAdmin ? (
            <div className="flex justify-end border-t border-gray-100 pt-5">
              <button
                type="submit"
                disabled={saving}
                className="rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {saving ? "Saving..." : "Save Changes"}
              </button>
            </div>
          ) : (
            <div className="rounded-lg bg-gray-50 p-4 text-sm text-gray-500">
              You can view organization information, but only an organization
              administrator can make changes.
            </div>
          )}
        </form>
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
  required = false,
  disabled = false,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  required?: boolean;
  disabled?: boolean;
}) {
  return (
    <div>
      <label className="mb-1 block text-sm font-medium text-gray-700">
        {label}
        {required ? " *" : ""}
      </label>

      <input
        type={type}
        value={value}
        required={required}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        className="w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:text-gray-500"
      />
    </div>
  );
}

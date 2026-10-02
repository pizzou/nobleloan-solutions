"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { get, put } from "../../../../services/api";
import { toast } from "../../../../hooks/useToast";
import { PageSpinner } from "../../../../components/ui/Skeleton";
import {
  DEFAULT_WEBSITE_CONTENT,
  mergeWebsiteContent,
  type WebsiteContent,
  type WebsitePageContent,
  type WebsiteSectionContent,
} from "../../../../lib/websiteContent";

interface Stat {
  icon: string;
  value: string;
  label: string;
}
interface Service {
  title: string;
  icon: string;
  rate: string;
  maxAmount: string;
  term: string;
  description: string;
}
interface Testimonial {
  name: string;
  role: string;
  text: string;
  rating: number;
}
interface TeamMember {
  name: string;
  role: string;
  initials: string;
}

interface OrgData {
  id: number;
  slug?: string;
  name: string;
  logoUrl?: string;
  primaryColor?: string;
  accentColor?: string;
  website?: string;
  contactEmail?: string;
  contactPhone?: string;
  address?: string;
  registrationNumber?: string;
  tagline?: string;
  mission?: string;
  vision?: string;
  foundedYear?: number;
  mapUrl?: string;
  facebookUrl?: string;
  instagramUrl?: string;
  linkedinUrl?: string;
  twitterUrl?: string;
  whatsappUrl?: string;
  hero?: { headline: string; subtext: string };
  stats?: Stat[];
  services?: Service[];
  testimonials?: Testimonial[];
  team?: TeamMember[];
  websiteContent?: WebsiteContent;
  [key: string]: unknown;
}

const DEFAULT_STATS: Stat[] = [];
const DEFAULT_TESTIMONIALS: Testimonial[] = [];
const DEFAULT_TEAM: TeamMember[] = [];

const PAGE_ORDER = [
  "home",
  "services",
  "calculators",
  "loan-calculator",
  "payment-calculator",
  "interest-calculator",
  "inflation-calculator",
  "how-it-works",
  "about",
  "our-story",
  "leadership",
  "careers",
  "affiliates",
  "learn",
  "faq",
  "help",
  "news",
  "borrower-stories",
  "contact",
  "track",
  "apply",
  "privacy",
  "privacy-choices",
  "data-collection",
  "terms",
  "accessibility",
  "state-licenses",
  "personal-loans",
  "business-loans",
  "vehicle-loans",
  "salary-advance",
  "agriculture-loans",
  "credit-score",
  "credit-card-consolidation",
  "debt-consolidation",
  "home-improvement-loans",
  "medical-loans",
  "moving-loans",
  "short-term-relief",
  "wedding-loans",
];

const PAGE_LABELS: Record<string, string> = {
  home: "Home",
  services: "Loan solutions",
  calculators: "Calculators",
  "loan-calculator": "Loan calculator",
  "payment-calculator": "Payment calculator",
  "interest-calculator": "Interest calculator",
  "inflation-calculator": "Inflation calculator",
  "how-it-works": "How it works",
  about: "About Noble",
  "our-story": "Our story",
  leadership: "Leadership",
  careers: "Careers",
  affiliates: "Partners",
  learn: "Learn",
  faq: "FAQs",
  help: "Help centre",
  news: "News & updates",
  "borrower-stories": "Borrower stories",
  contact: "Contact",
  track: "Track application",
  apply: "Application",
  privacy: "Privacy",
  "privacy-choices": "Privacy choices",
  "data-collection": "Data collection",
  terms: "Terms",
  accessibility: "Accessibility",
  "state-licenses": "Regulatory information",
  "personal-loans": "Personal Loan",
  "business-loans": "Business Finance",
  "vehicle-loans": "Vehicle Finance",
  "salary-advance": "Salary Advance",
  "agriculture-loans": "Agriculture Loan",
  "credit-score": "Legacy financial education",
  "credit-card-consolidation": "Legacy lending page",
  "debt-consolidation": "Legacy lending page",
  "home-improvement-loans": "Legacy lending page",
  "medical-loans": "Legacy lending page",
  "moving-loans": "Legacy lending page",
  "short-term-relief": "Legacy lending page",
  "wedding-loans": "Legacy lending page",
};

const inputClass =
  "w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-900 outline-none transition focus:border-slate-400 focus:ring-4 focus:ring-slate-900/5";
const textareaClass = `${inputClass} min-h-28 resize-y leading-6`;

function clonePage(page: WebsitePageContent): WebsitePageContent {
  return JSON.parse(JSON.stringify(page)) as WebsitePageContent;
}

function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="text-[10px] font-black uppercase tracking-[.16em] text-slate-400">
        {label}
      </span>
      <span className="mt-2 block">{children}</span>
      {hint ? (
        <span className="mt-2 block text-[11px] leading-5 text-slate-400">
          {hint}
        </span>
      ) : null}
    </label>
  );
}

export default function WebsiteSettingsPage() {
  const [org, setOrg] = useState<OrgData | null>(null);
  const [websiteContent, setWebsiteContent] = useState<WebsiteContent>(
    DEFAULT_WEBSITE_CONTENT,
  );
  const [activePage, setActivePage] = useState("home");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);

  useEffect(() => {
    get("/organizations/me")
      .then((data) => {
        const value = data as OrgData;
        setOrg({
          ...value,
          hero: value.hero ?? { headline: "", subtext: "" },
          stats: value.stats ?? DEFAULT_STATS,
          testimonials: value.testimonials ?? DEFAULT_TESTIMONIALS,
          team: value.team ?? DEFAULT_TEAM,
        });
        setWebsiteContent(mergeWebsiteContent(value.websiteContent));
      })
      .catch((error) =>
        toast(
          "error",
          error instanceof Error
            ? error.message
            : "Could not load website settings",
        ),
      )
      .finally(() => setLoading(false));
  }, []);

  const keys = useMemo(() => {
    const stored = Object.keys(websiteContent.pages);
    return [
      ...PAGE_ORDER,
      ...stored.filter((key) => !PAGE_ORDER.includes(key)),
    ];
  }, [websiteContent.pages]);

  const currentPage =
    websiteContent.pages[activePage] ??
    DEFAULT_WEBSITE_CONTENT.pages[activePage];

  const updateOrg = (patch: Partial<OrgData>) => {
    setOrg((current) => (current ? { ...current, ...patch } : current));
    setDirty(true);
  };

  const updatePage = (patch: Partial<WebsitePageContent>) => {
    setWebsiteContent((current) => ({
      ...current,
      pages: {
        ...current.pages,
        [activePage]: {
          ...current.pages[activePage],
          ...patch,
        },
      },
    }));
    setDirty(true);
  };

  const updateSection = (
    index: number,
    patch: Partial<WebsiteSectionContent>,
  ) => {
    const sections = currentPage.sections.map((section, i) =>
      i === index ? { ...section, ...patch } : section,
    );
    updatePage({ sections });
  };

  const addSection = () => {
    updatePage({
      sections: [
        ...currentPage.sections,
        { title: "New section", body: "Add the public copy for this section." },
      ],
    });
  };

  const removeSection = (index: number) => {
    updatePage({
      sections: currentPage.sections.filter((_, i) => i !== index),
    });
  };

  const resetCurrentPage = () => {
    const fallback = DEFAULT_WEBSITE_CONTENT.pages[activePage];
    if (!fallback) return;
    setWebsiteContent((current) => ({
      ...current,
      pages: { ...current.pages, [activePage]: clonePage(fallback) },
    }));
    setDirty(true);
    toast(
      "success",
      `${PAGE_LABELS[activePage] ?? activePage} restored to the published default`,
    );
  };

  const handleSave = async () => {
    if (!org) return;
    setSaving(true);
    try {
      const payload = { ...org, websiteContent };
      await put("/organizations/me", payload);
      setDirty(false);
      toast("success", "Website published successfully");
    } catch (error) {
      toast(
        "error",
        error instanceof Error
          ? error.message
          : "Could not save website changes",
      );
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <PageSpinner />;
  if (!org)
    return (
      <div className="rounded-3xl border border-red-100 bg-red-50 p-6 text-sm text-red-700">
        Could not load website settings.
      </div>
    );

  return (
    <div className="min-h-screen pb-12">
      <div className="sticky top-0 z-30 -mx-4 border-b border-slate-200 bg-[#f6f8fb]/95 px-4 py-4 backdrop-blur-xl lg:-mx-6 lg:px-6">
        <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
          <div>
            <div className="flex items-center gap-3">
              <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-[#0F1B3D] text-lg text-white">
                ✦
              </span>
              <div>
                <h1 className="text-2xl font-black tracking-tight text-slate-950">
                  Website Studio
                </h1>
                <p className="text-xs text-slate-500">
                  Edit public copy, visibility, calls-to-action and content
                  sections without touching code.
                </p>
              </div>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {dirty ? (
              <span className="rounded-full bg-amber-50 px-3 py-2 text-[10px] font-black uppercase tracking-[.14em] text-amber-700">
                Unsaved changes
              </span>
            ) : (
              <span className="rounded-full bg-emerald-50 px-3 py-2 text-[10px] font-black uppercase tracking-[.14em] text-emerald-700">
                Published
              </span>
            )}
            <Link
              href="/"
              target="_blank"
              className="rounded-2xl border border-slate-200 bg-white px-4 py-3 text-xs font-black text-slate-700 hover:border-slate-300"
            >
              View live site ↗
            </Link>
            <button
              onClick={handleSave}
              disabled={saving}
              className="rounded-2xl bg-[#0F1B3D] px-5 py-3 text-xs font-black text-white shadow-lg shadow-slate-900/15 transition hover:-translate-y-0.5 disabled:opacity-50"
            >
              {saving ? "Publishing…" : "Publish website"}
            </button>
          </div>
        </div>
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-[280px_minmax(0,1fr)]">
        <aside className="rounded-[28px] border border-slate-200 bg-white p-3 shadow-sm xl:sticky xl:top-28 xl:max-h-[calc(100vh-8rem)] xl:overflow-auto">
          <div className="px-3 pb-3 pt-2">
            <div className="text-[10px] font-black uppercase tracking-[.18em] text-slate-400">
              Public pages
            </div>
            <div className="mt-1 text-xs text-slate-500">
              {keys.length} editable routes
            </div>
          </div>
          <div className="space-y-1">
            {keys.map((key) => {
              const page = websiteContent.pages[key];
              const active = key === activePage;
              return (
                <button
                  key={key}
                  type="button"
                  onClick={() => setActivePage(key)}
                  className={`w-full rounded-2xl px-3 py-3 text-left transition ${active ? "bg-[#0F1B3D] text-white shadow-lg" : "text-slate-600 hover:bg-slate-50"}`}
                >
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-xs font-black">
                      {PAGE_LABELS[key] ?? key}
                    </span>
                    <span
                      className={`h-2 w-2 rounded-full ${page?.visible === false ? "bg-slate-300" : active ? "bg-[#C9A227]" : "bg-emerald-500"}`}
                    />
                  </div>
                  <span
                    className={`mt-1 block truncate text-[9px] ${active ? "text-white/45" : "text-slate-400"}`}
                  >
                    /{key === "home" ? "" : key}
                  </span>
                </button>
              );
            })}
          </div>
        </aside>

        <main className="min-w-0 space-y-6">
          <section className="overflow-hidden rounded-[32px] border border-slate-200 bg-white shadow-sm">
            <div className="bg-[linear-gradient(135deg,#061326,#0F1B3D)] p-7 text-white sm:p-9">
              <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
                <div className="max-w-3xl">
                  <div className="text-[10px] font-black uppercase tracking-[.2em] text-[#C9A227]">
                    Page editor
                  </div>
                  <h2 className="mt-3 text-3xl font-black tracking-tight sm:text-4xl">
                    {PAGE_LABELS[activePage] ?? activePage}
                  </h2>
                  <p className="mt-3 text-sm leading-7 text-white/55">
                    These fields control the public hero and page content.
                    Changes are published to the public website through the same
                    organisation record used by the lending platform.
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <span
                    className={`rounded-full border px-3 py-2 text-[10px] font-black uppercase tracking-[.14em] ${currentPage.visible ? "border-emerald-300/20 bg-emerald-300/10 text-emerald-200" : "border-white/10 bg-white/5 text-white/45"}`}
                  >
                    {currentPage.visible ? "Visible" : "Hidden"}
                  </span>
                  <button
                    type="button"
                    onClick={resetCurrentPage}
                    className="rounded-2xl border border-white/10 bg-white/5 px-4 py-2.5 text-[10px] font-black text-white/65 hover:bg-white/10"
                  >
                    Restore default
                  </button>
                </div>
              </div>
            </div>

            <div className="space-y-7 p-6 sm:p-8">
              <div className="flex items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-slate-50 p-4">
                <div>
                  <div className="text-xs font-black text-slate-900">
                    Publish this page
                  </div>
                  <div className="mt-1 text-[11px] text-slate-500">
                    Hidden pages remain in the system but show a branded
                    unavailable state publicly.
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => updatePage({ visible: !currentPage.visible })}
                  className={`relative h-7 w-12 rounded-full transition ${currentPage.visible ? "bg-[#0F1B3D]" : "bg-slate-300"}`}
                  aria-label="Toggle page visibility"
                >
                  <span
                    className={`absolute top-1 h-5 w-5 rounded-full bg-white shadow transition ${currentPage.visible ? "left-6" : "left-1"}`}
                  />
                </button>
              </div>

              <div className="grid gap-5 lg:grid-cols-2">
                <Field label="Eyebrow">
                  <input
                    className={inputClass}
                    value={currentPage.eyebrow}
                    onChange={(e) => updatePage({ eyebrow: e.target.value })}
                  />
                </Field>
                <Field label="Page title">
                  <input
                    className={inputClass}
                    value={currentPage.title}
                    onChange={(e) => updatePage({ title: e.target.value })}
                  />
                </Field>
              </div>
              <Field
                label="Page description"
                hint="This is the main introductory copy beneath the page title."
              >
                <textarea
                  className={textareaClass}
                  value={currentPage.description}
                  onChange={(e) => updatePage({ description: e.target.value })}
                />
              </Field>

              <div className="rounded-[26px] border border-slate-200 p-5 sm:p-6">
                <div className="mb-5">
                  <div className="text-xs font-black text-slate-900">
                    Page actions
                  </div>
                  <div className="mt-1 text-[11px] text-slate-400">
                    Optional call-to-action buttons shown by supported public
                    page templates.
                  </div>
                </div>
                <div className="grid gap-5 lg:grid-cols-2">
                  <Field label="Primary button label">
                    <input
                      className={inputClass}
                      value={currentPage.primaryLabel ?? ""}
                      onChange={(e) =>
                        updatePage({ primaryLabel: e.target.value })
                      }
                      placeholder="Start an application"
                    />
                  </Field>
                  <Field label="Primary button link">
                    <input
                      className={inputClass}
                      value={currentPage.primaryHref ?? ""}
                      onChange={(e) =>
                        updatePage({ primaryHref: e.target.value })
                      }
                      placeholder="/apply"
                    />
                  </Field>
                  <Field label="Secondary button label">
                    <input
                      className={inputClass}
                      value={currentPage.secondaryLabel ?? ""}
                      onChange={(e) =>
                        updatePage({ secondaryLabel: e.target.value })
                      }
                      placeholder="Use a calculator"
                    />
                  </Field>
                  <Field label="Secondary button link">
                    <input
                      className={inputClass}
                      value={currentPage.secondaryHref ?? ""}
                      onChange={(e) =>
                        updatePage({ secondaryHref: e.target.value })
                      }
                      placeholder="/loan-calculator"
                    />
                  </Field>
                </div>
              </div>

              <div className="rounded-[26px] border border-slate-200 p-5 sm:p-6">
                <div className="flex items-center justify-between gap-4">
                  <div>
                    <div className="text-xs font-black text-slate-900">
                      Content sections
                    </div>
                    <div className="mt-1 text-[11px] text-slate-400">
                      Edit the rich content blocks displayed on the page.
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={addSection}
                    className="rounded-2xl bg-[#0F1B3D] px-4 py-2.5 text-[10px] font-black text-white"
                  >
                    + Add section
                  </button>
                </div>

                <div className="mt-5 space-y-4">
                  {currentPage.sections.map((section, index) => (
                    <div
                      key={`${activePage}-${index}`}
                      className="rounded-2xl border border-slate-200 bg-slate-50/70 p-4 sm:p-5"
                    >
                      <div className="flex items-center justify-between gap-4">
                        <span className="text-[10px] font-black uppercase tracking-[.16em] text-slate-400">
                          Section {String(index + 1).padStart(2, "0")}
                        </span>
                        <button
                          type="button"
                          onClick={() => removeSection(index)}
                          className="rounded-xl px-3 py-1.5 text-[10px] font-black text-red-600 hover:bg-red-50"
                        >
                          Remove
                        </button>
                      </div>
                      <div className="mt-4 grid gap-4">
                        <Field label="Section title">
                          <input
                            className={inputClass}
                            value={section.title}
                            onChange={(e) =>
                              updateSection(index, { title: e.target.value })
                            }
                          />
                        </Field>
                        <Field label="Section body">
                          <textarea
                            className={textareaClass}
                            value={section.body}
                            onChange={(e) =>
                              updateSection(index, { body: e.target.value })
                            }
                          />
                        </Field>
                        <Field
                          label="Bullet points"
                          hint="Optional. Put one bullet per line."
                        >
                          <textarea
                            className={`${textareaClass} min-h-24`}
                            value={(section.bullets ?? []).join("\n")}
                            onChange={(e) =>
                              updateSection(index, {
                                bullets: e.target.value
                                  .split(/\r?\n/)
                                  .map((item) => item.trim())
                                  .filter(Boolean),
                              })
                            }
                          />
                        </Field>
                      </div>
                    </div>
                  ))}
                  {currentPage.sections.length === 0 ? (
                    <div className="rounded-2xl border border-dashed border-slate-300 p-7 text-center text-sm text-slate-400">
                      No sections yet. Add a section to create public page
                      content.
                    </div>
                  ) : null}
                </div>
              </div>
            </div>
          </section>

          <section className="rounded-[30px] border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
            <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <div className="text-[10px] font-black uppercase tracking-[.18em] text-[#C9A227]">
                  Global website settings
                </div>
                <h2 className="mt-2 text-xl font-black tracking-tight">
                  Brand, contact and trust information
                </h2>
                <p className="mt-2 text-xs leading-6 text-slate-500">
                  These values are shared across the public navigation, footer
                  and contact experience.
                </p>
              </div>
              <Link
                href="/dashboard/products"
                className="rounded-2xl border border-slate-200 px-4 py-3 text-xs font-black text-slate-700"
              >
                Manage lending products →
              </Link>
            </div>

            <div className="mt-7 grid gap-5 lg:grid-cols-2">
              <Field label="Organization name">
                <input
                  className={inputClass}
                  value={org.name ?? ""}
                  onChange={(e) => updateOrg({ name: e.target.value })}
                />
              </Field>
              <Field label="Tagline">
                <input
                  className={inputClass}
                  value={org.tagline ?? ""}
                  onChange={(e) => updateOrg({ tagline: e.target.value })}
                />
              </Field>
              <Field label="Primary brand colour">
                <div className="flex gap-3">
                  <input
                    type="color"
                    value={org.primaryColor ?? "#0F1B3D"}
                    onChange={(e) =>
                      updateOrg({ primaryColor: e.target.value })
                    }
                    className="h-12 w-14 cursor-pointer rounded-xl border border-slate-200 p-1"
                  />
                  <input
                    className={inputClass}
                    value={org.primaryColor ?? ""}
                    onChange={(e) =>
                      updateOrg({ primaryColor: e.target.value })
                    }
                  />
                </div>
              </Field>
              <Field label="Accent colour">
                <div className="flex gap-3">
                  <input
                    type="color"
                    value={org.accentColor ?? "#C9A227"}
                    onChange={(e) => updateOrg({ accentColor: e.target.value })}
                    className="h-12 w-14 cursor-pointer rounded-xl border border-slate-200 p-1"
                  />
                  <input
                    className={inputClass}
                    value={org.accentColor ?? ""}
                    onChange={(e) => updateOrg({ accentColor: e.target.value })}
                  />
                </div>
              </Field>
              <Field label="Logo URL">
                <input
                  className={inputClass}
                  value={org.logoUrl ?? ""}
                  onChange={(e) => updateOrg({ logoUrl: e.target.value })}
                  placeholder="https://…"
                />
              </Field>
              <Field label="Website URL">
                <input
                  className={inputClass}
                  value={org.website ?? ""}
                  onChange={(e) => updateOrg({ website: e.target.value })}
                  placeholder="https://…"
                />
              </Field>
              <Field label="Contact email">
                <input
                  className={inputClass}
                  value={org.contactEmail ?? ""}
                  onChange={(e) => updateOrg({ contactEmail: e.target.value })}
                />
              </Field>
              <Field label="Contact phone">
                <input
                  className={inputClass}
                  value={org.contactPhone ?? ""}
                  onChange={(e) => updateOrg({ contactPhone: e.target.value })}
                />
              </Field>
              <Field label="Office address">
                <input
                  className={inputClass}
                  value={org.address ?? ""}
                  onChange={(e) => updateOrg({ address: e.target.value })}
                />
              </Field>
              <Field label="Registration number">
                <input
                  className={inputClass}
                  value={org.registrationNumber ?? ""}
                  onChange={(e) =>
                    updateOrg({ registrationNumber: e.target.value })
                  }
                />
              </Field>
              <Field label="Founded year">
                <input
                  type="number"
                  className={inputClass}
                  value={org.foundedYear ?? ""}
                  onChange={(e) =>
                    updateOrg({
                      foundedYear: Number(e.target.value) || undefined,
                    })
                  }
                />
              </Field>
              <Field label="Google Maps URL">
                <input
                  className={inputClass}
                  value={org.mapUrl ?? ""}
                  onChange={(e) => updateOrg({ mapUrl: e.target.value })}
                />
              </Field>
            </div>

            <div className="mt-6 grid gap-5 lg:grid-cols-2">
              <Field label="Mission">
                <textarea
                  className={textareaClass}
                  value={org.mission ?? ""}
                  onChange={(e) => updateOrg({ mission: e.target.value })}
                />
              </Field>
              <Field label="Vision">
                <textarea
                  className={textareaClass}
                  value={org.vision ?? ""}
                  onChange={(e) => updateOrg({ vision: e.target.value })}
                />
              </Field>
            </div>

            <div className="mt-6 grid gap-5 lg:grid-cols-2">
              <Field label="Facebook">
                <input
                  className={inputClass}
                  value={org.facebookUrl ?? ""}
                  onChange={(e) => updateOrg({ facebookUrl: e.target.value })}
                />
              </Field>
              <Field label="Instagram">
                <input
                  className={inputClass}
                  value={org.instagramUrl ?? ""}
                  onChange={(e) => updateOrg({ instagramUrl: e.target.value })}
                />
              </Field>
              <Field label="LinkedIn">
                <input
                  className={inputClass}
                  value={org.linkedinUrl ?? ""}
                  onChange={(e) => updateOrg({ linkedinUrl: e.target.value })}
                />
              </Field>
              <Field label="X / Twitter">
                <input
                  className={inputClass}
                  value={org.twitterUrl ?? ""}
                  onChange={(e) => updateOrg({ twitterUrl: e.target.value })}
                />
              </Field>
              <Field label="WhatsApp">
                <input
                  className={inputClass}
                  value={org.whatsappUrl ?? ""}
                  onChange={(e) => updateOrg({ whatsappUrl: e.target.value })}
                />
              </Field>
            </div>

            <div className="mt-8 rounded-[28px] border border-slate-200 bg-slate-50/60 p-5 sm:p-6">
              <div>
                <div className="text-xs font-black text-slate-900">
                  Homepage verified content
                </div>
                <p className="mt-1 text-[11px] leading-5 text-slate-500">
                  Manage public statistics, leadership cards and approved
                  borrower stories. Publish only information you are authorised
                  to share.
                </p>
              </div>

              <div className="mt-6 space-y-6">
                <div>
                  <div className="flex items-center justify-between gap-4">
                    <div className="text-[10px] font-black uppercase tracking-[.16em] text-slate-400">
                      Statistics
                    </div>
                    <button
                      type="button"
                      onClick={() =>
                        updateOrg({
                          stats: [
                            ...(org.stats ?? []),
                            { icon: "", value: "", label: "" },
                          ],
                        })
                      }
                      className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-[10px] font-black"
                    >
                      + Add
                    </button>
                  </div>
                  <div className="mt-3 space-y-2">
                    {(org.stats ?? []).map((item, index) => (
                      <div
                        key={`stat-${index}`}
                        className="grid gap-2 sm:grid-cols-[70px_1fr_1.5fr_auto]"
                      >
                        <input
                          className={inputClass}
                          value={item.icon}
                          onChange={(e) =>
                            updateOrg({
                              stats: (org.stats ?? []).map((x, i) =>
                                i === index
                                  ? { ...x, icon: e.target.value }
                                  : x,
                              ),
                            })
                          }
                          placeholder="Icon"
                        />
                        <input
                          className={inputClass}
                          value={item.value}
                          onChange={(e) =>
                            updateOrg({
                              stats: (org.stats ?? []).map((x, i) =>
                                i === index
                                  ? { ...x, value: e.target.value }
                                  : x,
                              ),
                            })
                          }
                          placeholder="Value"
                        />
                        <input
                          className={inputClass}
                          value={item.label}
                          onChange={(e) =>
                            updateOrg({
                              stats: (org.stats ?? []).map((x, i) =>
                                i === index
                                  ? { ...x, label: e.target.value }
                                  : x,
                              ),
                            })
                          }
                          placeholder="Label"
                        />
                        <button
                          type="button"
                          onClick={() =>
                            updateOrg({
                              stats: (org.stats ?? []).filter(
                                (_, i) => i !== index,
                              ),
                            })
                          }
                          className="rounded-xl px-3 text-[10px] font-black text-red-600 hover:bg-red-50"
                        >
                          Remove
                        </button>
                      </div>
                    ))}
                  </div>
                </div>

                <div>
                  <div className="flex items-center justify-between gap-4">
                    <div className="text-[10px] font-black uppercase tracking-[.16em] text-slate-400">
                      Leadership team
                    </div>
                    <button
                      type="button"
                      onClick={() =>
                        updateOrg({
                          team: [
                            ...(org.team ?? []),
                            { name: "", role: "", initials: "" },
                          ],
                        })
                      }
                      className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-[10px] font-black"
                    >
                      + Add
                    </button>
                  </div>
                  <div className="mt-3 grid gap-3 lg:grid-cols-2">
                    {(org.team ?? []).map((item, index) => (
                      <div
                        key={`team-${index}`}
                        className="rounded-2xl border border-slate-200 bg-white p-4"
                      >
                        <div className="grid gap-2 sm:grid-cols-[1fr_1fr_65px_auto]">
                          <input
                            className={inputClass}
                            value={item.name}
                            onChange={(e) =>
                              updateOrg({
                                team: (org.team ?? []).map((x, i) =>
                                  i === index
                                    ? { ...x, name: e.target.value }
                                    : x,
                                ),
                              })
                            }
                            placeholder="Name"
                          />
                          <input
                            className={inputClass}
                            value={item.role}
                            onChange={(e) =>
                              updateOrg({
                                team: (org.team ?? []).map((x, i) =>
                                  i === index
                                    ? { ...x, role: e.target.value }
                                    : x,
                                ),
                              })
                            }
                            placeholder="Role"
                          />
                          <input
                            className={inputClass}
                            value={item.initials}
                            onChange={(e) =>
                              updateOrg({
                                team: (org.team ?? []).map((x, i) =>
                                  i === index
                                    ? {
                                        ...x,
                                        initials: e.target.value
                                          .toUpperCase()
                                          .slice(0, 2),
                                      }
                                    : x,
                                ),
                              })
                            }
                            placeholder="AB"
                          />
                          <button
                            type="button"
                            onClick={() =>
                              updateOrg({
                                team: (org.team ?? []).filter(
                                  (_, i) => i !== index,
                                ),
                              })
                            }
                            className="rounded-xl px-3 text-[10px] font-black text-red-600 hover:bg-red-50"
                          >
                            Remove
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div>
                  <div className="flex items-center justify-between gap-4">
                    <div className="text-[10px] font-black uppercase tracking-[.16em] text-slate-400">
                      Approved borrower stories
                    </div>
                    <button
                      type="button"
                      onClick={() =>
                        updateOrg({
                          testimonials: [
                            ...(org.testimonials ?? []),
                            { name: "", role: "", text: "", rating: 5 },
                          ],
                        })
                      }
                      className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-[10px] font-black"
                    >
                      + Add
                    </button>
                  </div>
                  <div className="mt-3 grid gap-3 lg:grid-cols-2">
                    {(org.testimonials ?? []).map((item, index) => (
                      <div
                        key={`testimonial-${index}`}
                        className="rounded-2xl border border-slate-200 bg-white p-4"
                      >
                        <div className="grid gap-2 sm:grid-cols-2">
                          <input
                            className={inputClass}
                            value={item.name}
                            onChange={(e) =>
                              updateOrg({
                                testimonials: (org.testimonials ?? []).map(
                                  (x, i) =>
                                    i === index
                                      ? { ...x, name: e.target.value }
                                      : x,
                                ),
                              })
                            }
                            placeholder="Client name"
                          />
                          <input
                            className={inputClass}
                            value={item.role}
                            onChange={(e) =>
                              updateOrg({
                                testimonials: (org.testimonials ?? []).map(
                                  (x, i) =>
                                    i === index
                                      ? { ...x, role: e.target.value }
                                      : x,
                                ),
                              })
                            }
                            placeholder="Role / context"
                          />
                          <textarea
                            className={`${textareaClass} sm:col-span-2`}
                            value={item.text}
                            onChange={(e) =>
                              updateOrg({
                                testimonials: (org.testimonials ?? []).map(
                                  (x, i) =>
                                    i === index
                                      ? { ...x, text: e.target.value }
                                      : x,
                                ),
                              })
                            }
                            placeholder="Approved testimonial"
                          />
                          <div className="flex items-center justify-between gap-3 sm:col-span-2">
                            <select
                              className={inputClass}
                              value={item.rating}
                              onChange={(e) =>
                                updateOrg({
                                  testimonials: (org.testimonials ?? []).map(
                                    (x, i) =>
                                      i === index
                                        ? {
                                            ...x,
                                            rating: Number(e.target.value),
                                          }
                                        : x,
                                  ),
                                })
                              }
                            >
                              {[5, 4, 3, 2, 1].map((n) => (
                                <option key={n} value={n}>
                                  {n} stars
                                </option>
                              ))}
                            </select>
                            <button
                              type="button"
                              onClick={() =>
                                updateOrg({
                                  testimonials: (org.testimonials ?? []).filter(
                                    (_, i) => i !== index,
                                  ),
                                })
                              }
                              className="rounded-xl px-3 py-2 text-[10px] font-black text-red-600 hover:bg-red-50"
                            >
                              Remove
                            </button>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>

            <div className="mt-7 rounded-[28px] border border-slate-200 bg-slate-50/60 p-5 sm:p-6">
              <div className="text-xs font-black text-slate-900">
                Homepage hero
              </div>
              <p className="mt-1 text-[11px] text-slate-500">
                This legacy structured hero remains available for compatibility.
                The Website Studio Home page copy above is the primary editor.
              </p>
              <div className="mt-5 grid gap-4">
                <Field label="Hero headline">
                  <input
                    className={inputClass}
                    value={org.hero?.headline ?? ""}
                    onChange={(e) =>
                      updateOrg({
                        hero: {
                          headline: e.target.value,
                          subtext: org.hero?.subtext ?? "",
                        },
                      })
                    }
                  />
                </Field>
                <Field label="Hero supporting text">
                  <textarea
                    className={textareaClass}
                    value={org.hero?.subtext ?? ""}
                    onChange={(e) =>
                      updateOrg({
                        hero: {
                          headline: org.hero?.headline ?? "",
                          subtext: e.target.value,
                        },
                      })
                    }
                  />
                </Field>
              </div>
            </div>

            <div className="mt-7 rounded-[26px] border border-slate-200 bg-slate-50 p-5 sm:p-6">
              <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <div className="text-xs font-black text-slate-900">
                    Homepage structured content
                  </div>
                  <p className="mt-1 text-[11px] leading-5 text-slate-500">
                    Stats, team and testimonials remain controlled here for
                    verified content. Do not publish invented figures or
                    customer stories.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={handleSave}
                  disabled={saving}
                  className="rounded-2xl bg-[#0F1B3D] px-5 py-3 text-xs font-black text-white disabled:opacity-50"
                >
                  {saving ? "Publishing…" : "Save all website changes"}
                </button>
              </div>
            </div>
          </section>
        </main>
      </div>
    </div>
  );
}

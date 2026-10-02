import { DEFAULT_WEBSITE_CONTENT, type WebsiteContent } from "./websiteContent";

export interface TenantConfig {
  name: string;
  slug: string;
  country: string;
  currency: string;
  primaryColor: string;
  accentColor: string;
  logoUrl?: string;
  contactEmail?: string;
  contactPhone?: string;
  website?: string;
  address?: string;
  tagline?: string;
  mission?: string;
  vision?: string;
  founded?: string;
  registrationNumber?: string;

  socialMedia?: {
    facebook?: string;
    instagram?: string;
    linkedin?: string;
    twitter?: string;
    whatsapp?: string;
    youtube?: string;
  };

  mapUrl?: string;

  monthlyInterestRate?: string | number;
  monthlyManagementFeeRate?: string | number;
  applicationFeeRate?: string | number;

  minLoanDurationMonths?: number;
  maxLoanDurationMonths?: number;

  services: {
    title: string;
    description: string;
    icon: string;
    loanType?: string;
    rate: string | number;
    rateType?: string;
    interestRate?: string | number;
    applicationFeeRate?: string | number;
    managementFeeRate?: string | number;
    minAmount?: string | number;
    maxAmount?: string | number | null;
    minTermMonths?: number;
    maxTermMonths?: number;
    term: string;
  }[];

  hero: {
    headline: string;
    subtext: string;
  };

  stats: {
    icon: string;
    value: string;
    label: string;
  }[];

  testimonials: {
    name: string;
    role: string;
    text: string;
    rating: number;
  }[];

  team: {
    name: string;
    role: string;
    initials: string;
  }[];

  websiteContent: WebsiteContent;
}

const value = (candidate: string | undefined, fallback: string): string => {
  return candidate && candidate.trim() ? candidate.trim() : fallback;
};

const products: TenantConfig["services"] = [
  {
    title: "Personal Loan",
    description:
      "Personal financing for approved household and individual needs.",
    icon: "👤",
    loanType: "PERSONAL",
    rate: 5,
    interestRate: 5,
    rateType: "MONTHLY",
    applicationFeeRate: 2,
    managementFeeRate: 5,
    minAmount: 500000,
    maxAmount: null,
    minTermMonths: 1,
    maxTermMonths: 6,
    term: "1 to 6 months",
  },

  {
    title: "Business Finance",
    description: "Working capital and business expansion financing.",
    icon: "🏢",
    loanType: "BUSINESS",
    rate: 5,
    interestRate: 5,
    rateType: "MONTHLY",
    applicationFeeRate: 2,
    managementFeeRate: 5,
    minAmount: 500000,
    maxAmount: null,
    minTermMonths: 1,
    maxTermMonths: 6,
    term: "1 to 6 months",
  },

  {
    title: "Vehicle Finance",
    description: "Financing for approved vehicle purchases.",
    icon: "🚗",
    loanType: "AUTO",
    rate: 5,
    interestRate: 5,
    rateType: "MONTHLY",
    applicationFeeRate: 2,
    managementFeeRate: 5,
    minAmount: 500000,
    maxAmount: null,
    minTermMonths: 1,
    maxTermMonths: 6,
    term: "1 to 6 months",
  },

  {
    title: "Salary Advance Loan",
    description: "Short-term financing against verified salary income.",
    icon: "💵",
    loanType: "SALARY_ADVANCE",
    rate: 5,
    interestRate: 5,
    rateType: "MONTHLY",
    applicationFeeRate: 2,
    managementFeeRate: 5,
    minAmount: 500000,
    maxAmount: null,
    minTermMonths: 1,
    maxTermMonths: 6,
    term: "1 to 6 months",
  },

  {
    title: "Agriculture Loan",
    description:
      "Financing for approved agricultural and agribusiness activities.",
    icon: "🌾",
    loanType: "AGRICULTURAL",
    rate: 5,
    interestRate: 5,
    rateType: "MONTHLY",
    applicationFeeRate: 2,
    managementFeeRate: 5,
    minAmount: 500000,
    maxAmount: null,
    minTermMonths: 1,
    maxTermMonths: 6,
    term: "1 to 6 months",
  },
];

export const SITE_CONTENT: TenantConfig = {
  name: value(process.env.NEXT_PUBLIC_SITE_NAME, "Noble Loan Solutions Ltd"),

  slug: value(process.env.NEXT_PUBLIC_SITE_SLUG, "nobleloansolutions"),

  country: value(process.env.NEXT_PUBLIC_SITE_COUNTRY, "Rwanda"),

  currency: value(process.env.NEXT_PUBLIC_SITE_CURRENCY, "RWF"),

  primaryColor: value(process.env.NEXT_PUBLIC_SITE_PRIMARY_COLOR, "#0F1B3D"),

  accentColor: value(process.env.NEXT_PUBLIC_SITE_ACCENT_COLOR, "#C9A227"),

  logoUrl: value(
    process.env.NEXT_PUBLIC_SITE_LOGO_URL,
    "/noble-loan-solutions-logo.svg",
  ),

  contactEmail: value(
    process.env.NEXT_PUBLIC_SITE_CONTACT_EMAIL,
    "info@nobleloansolutions.rw",
  ),

  contactPhone: value(
    process.env.NEXT_PUBLIC_SITE_CONTACT_PHONE,
    "+250 788 123 456",
  ),

  website: value(
    process.env.NEXT_PUBLIC_SITE_WEBSITE,
    "https://nobleloan-solutions.vercel.app",
  ),

  address: value(process.env.NEXT_PUBLIC_SITE_ADDRESS, "Kigali, Rwanda"),

  registrationNumber: value(
    process.env.NEXT_PUBLIC_SITE_REGISTRATION_NUMBER,
    "",
  ),

  tagline: "Trusted lending for the moments that matter",

  mission:
    "To provide honest, fairly-priced credit to individuals and businesses across Rwanda, delivered with integrity, transparency, and respect for every client.",

  vision:
    "To be Rwanda's most trusted name in lending — synonymous with fairness, transparency, and financial dignity for every client we serve.",

  hero: {
    headline: "Real support. Bigger dreams.",

    subtext:
      "Flexible loans for personal needs, business growth, vehicles, salary advances and agriculture—with clear terms and support at every step.",
  },

  founded: value(process.env.NEXT_PUBLIC_SITE_FOUNDED_YEAR, ""),

  socialMedia: {
    facebook: value(process.env.NEXT_PUBLIC_SITE_FACEBOOK_URL, ""),

    instagram: value(process.env.NEXT_PUBLIC_SITE_INSTAGRAM_URL, ""),

    linkedin: value(process.env.NEXT_PUBLIC_SITE_LINKEDIN_URL, ""),

    twitter: value(process.env.NEXT_PUBLIC_SITE_TWITTER_URL, ""),

    whatsapp: value(process.env.NEXT_PUBLIC_SITE_WHATSAPP_URL, ""),
    youtube: value(process.env.NEXT_PUBLIC_SITE_YOUTUBE_URL, ""),
  },

  mapUrl: value(process.env.NEXT_PUBLIC_SITE_MAP_URL, ""),

  monthlyInterestRate: 5,
  monthlyManagementFeeRate: 5,
  applicationFeeRate: 2,

  minLoanDurationMonths: 1,
  maxLoanDurationMonths: 6,

  services: products,

  /*
   * Keep these empty until verified company statistics
   * are supplied. Do not publish invented numbers.
   */
  stats: [],

  /*
   * Keep these empty until verified customer testimonials
   * are supplied.
   */
  testimonials: [],

  /*
   * Keep these empty until verified staff information
   * is supplied.
   */
  team: [],

  websiteContent: DEFAULT_WEBSITE_CONTENT,
};

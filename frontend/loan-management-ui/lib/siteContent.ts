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
  hero: { headline: string; subtext: string };
  stats: { icon: string; value: string; label: string }[];
  testimonials: { name: string; role: string; text: string; rating: number }[];
  team: { name: string; role: string; initials: string }[];
}

const value = (candidate: string | undefined, fallback: string) =>
  candidate && candidate.trim() ? candidate.trim() : fallback;

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
  contactEmail: value(process.env.NEXT_PUBLIC_SITE_CONTACT_EMAIL, ""),
  contactPhone: value(process.env.NEXT_PUBLIC_SITE_CONTACT_PHONE, ""),
  website: value(
    process.env.NEXT_PUBLIC_SITE_WEBSITE,
    "https://nobleloansolutions.rw",
  ),
  address: value(process.env.NEXT_PUBLIC_SITE_ADDRESS, ""),
  registrationNumber: value(
    process.env.NEXT_PUBLIC_SITE_REGISTRATION_NUMBER,
    "",
  ),
  tagline: "Your Trusted Partner in Financial Support",
  mission:
    "To provide honest, fairly-priced credit to individuals and businesses across Rwanda, delivered with integrity, transparency, and respect for every client.",
  vision:
    "To be Rwanda's most trusted name in lending — synonymous with fairness, transparency, and financial dignity for every client we serve.",
  hero: {
    headline: "Need Cash Fast? We've Got You Covered!",
    subtext:
      "Your trusted partner in financial support — personal, business, vehicle, salary advance, and agriculture loans, backed by a secure, fully compliant lending platform.",
  },
  founded: value(process.env.NEXT_PUBLIC_SITE_FOUNDED_YEAR, ""),
  socialMedia: {
    facebook: value(process.env.NEXT_PUBLIC_SITE_FACEBOOK_URL, ""),
    instagram: value(process.env.NEXT_PUBLIC_SITE_INSTAGRAM_URL, ""),
    linkedin: value(process.env.NEXT_PUBLIC_SITE_LINKEDIN_URL, ""),
    twitter: value(process.env.NEXT_PUBLIC_SITE_TWITTER_URL, ""),
    whatsapp: value(process.env.NEXT_PUBLIC_SITE_WHATSAPP_URL, ""),
  },
  mapUrl: value(process.env.NEXT_PUBLIC_SITE_MAP_URL, ""),
  monthlyInterestRate: 5,
  monthlyManagementFeeRate: 5,
  applicationFeeRate: 2,
  minLoanDurationMonths: 1,
  maxLoanDurationMonths: 6,
  services: products,
  stats: [
    { icon: "👥", value: "5,000+", label: "Happy Clients" },
    { icon: "💰", value: "RWF 2B+", label: "Loans Disbursed" },
    { icon: "⚡", value: "24 hrs", label: "Average Approval" },
    { icon: "⭐", value: "98%", label: "Client Satisfaction" },
  ],
  testimonials: [
    {
      name: "Joseph G.",
      role: "Small Business Owner",
      rating: 5,
      text: "Noble Loan Solutions helped me expand my shop with a business loan.",
    },
    {
      name: "Olivier M.",
      role: "Farmer",
      rating: 5,
      text: "I received agricultural financing to expand my farming operation.",
    },
    {
      name: "Grace U.",
      role: "Teacher",
      rating: 5,
      text: "The salary advance process was simple and convenient.",
    },
  ],
  team: [
    { name: "Emmanuel R.", role: "Chief Executive Officer", initials: "ER" },
    { name: "Alice U.", role: "Chief Finance Officer", initials: "AU" },
    { name: "Patrick M.", role: "Head of Credit", initials: "PM" },
    { name: "Alice K.", role: "Head of Operations", initials: "AK" },
  ],
};

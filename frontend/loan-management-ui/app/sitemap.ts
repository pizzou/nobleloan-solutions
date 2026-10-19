import type { MetadataRoute } from "next";

/** Public marketing pages only. Never advertise authenticated or borrower-specific routes. */
const siteUrl = (
  process.env.NEXT_PUBLIC_SITE_URL ||
  process.env.NEXT_PUBLIC_SITE_WEBSITE ||
  "https://nobleloansolutions.rw"
).replace(/\/+$/, "");
const publicPaths = [
  "",
  "/about",
  "/our-story",
  "/leadership",
  "/services",
  "/how-it-works",
  "/personal-loans",
  "/business-loans",
  "/vehicle-loans",
  "/agriculture-loans",
  "/salary-advance",
  "/short-term-relief",
  "/debt-consolidation",
  "/credit-card-consolidation",
  "/home-improvement-loans",
  "/medical-loans",
  "/wedding-loans",
  "/moving-loans",
  "/calculators",
  "/loan-calculator",
  "/payment-calculator",
  "/interest-calculator",
  "/inflation-calculator",
  "/credit-score",
  "/learn",
  "/faq",
  "/help",
  "/borrower-stories",
  "/news",
  "/affiliates",
  "/careers",
  "/contact",
  "/apply",
  "/accessibility",
  "/privacy",
  "/privacy-choices",
  "/data-collection",
  "/terms",
  "/state-licenses",
];

export default function sitemap(): MetadataRoute.Sitemap {
  return publicPaths.map((path) => ({
    url: `${siteUrl}${path}`,
    changeFrequency: path === "" ? "weekly" : "monthly",
    priority:
      path === ""
        ? 1
        : [
              "/personal-loans",
              "/business-loans",
              "/services",
              "/how-it-works",
            ].includes(path)
          ? 0.8
          : 0.6,
  }));
}

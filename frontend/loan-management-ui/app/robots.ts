import type { MetadataRoute } from "next";

/** One robots.txt policy is consumed by search crawlers, regardless of the browser a visitor uses. */
const siteUrl = (
  process.env.NEXT_PUBLIC_SITE_URL ||
  process.env.NEXT_PUBLIC_SITE_WEBSITE ||
  "https://nobleloansolutions.rw"
).replace(/\/+$/, "");

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: "*",
        allow: ["/"],
        disallow: [
          "/api/",
          "/dashboard",
          "/login",
          "/sign/",
          "/mfa-setup",
          "/forgot-password",
          "/reset-password",
        ],
      },
    ],
    sitemap: `${siteUrl}/sitemap.xml`,
    host: siteUrl,
  };
}

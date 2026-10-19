import type { MetadataRoute } from "next";

const siteUrl = "https://nobleloansolutions.rw";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: [
        "/dashboard/",
        "/login/",
        "/sign/",
        "/mfa-setup/",
        "/forgot-password/",
        "/reset-password/",
      ],
    },
    sitemap: `${siteUrl}/sitemap.xml`,
    host: siteUrl,
  };
}

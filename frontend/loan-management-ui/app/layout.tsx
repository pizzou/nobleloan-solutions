import type { Metadata } from "next";
import "./globals.css";

import ServiceWorkerRegistration from "@/components/ServiceWorkerRegistration";

export const metadata: Metadata = {
  title: "Noble Loan Solutions — Loans & Financial Services",
  description:
    "Noble Loan Solutions — licensed loan products, online applications, and secure account management for individuals, businesses, and farmers in Rwanda.",

  icons: {
    icon: [
      {
        url: "/favIcon.png",
        type: "image/png",
      },
    ],
    shortcut: [
      {
        url: "/favIcon.png",
        type: "image/png",
      },
    ],
    apple: [
      {
        url: "/favIcon.png",
        type: "image/png",
      },
    ],
  },

  metadataBase: new URL(
    process.env.NEXT_PUBLIC_SITE_URL || "https://nobleloansolutions.rw",
  ),
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body
        className="bg-gray-50 text-gray-900 antialiased"
        style={{
          fontFamily: "'Inter', system-ui, sans-serif",
        }}
      >
        {/* Registers the Service Worker */}
        <ServiceWorkerRegistration />

        {children}
      </body>
    </html>
  );
}

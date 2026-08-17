import type { Metadata, Viewport } from "next";
import "./globals.css";

import SyncProvider from "@/lib/SyncProvider";
import ServiceWorkerRegistration from "@/components/ServiceWorkerRegistration";

export const metadata: Metadata = {
  title: {
    default: "Noble Loan Solutions | Trusted Lending in Rwanda",
    template: "%s | Noble Loan Solutions",
  },
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
    process.env.NEXT_PUBLIC_SITE_URL || "https://nobleloan-fev7-one.vercel.app",
  ),
};

export const viewport: Viewport = {
  themeColor: "#0B1F3A",
  colorScheme: "light",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="bg-[#F4F7FB] text-slate-900 antialiased">
        {/* Registers the Service Worker */}
        <ServiceWorkerRegistration />

        {/* Automatically synchronizes queued offline requests */}
        <SyncProvider />

        {children}
      </body>
    </html>
  );
}

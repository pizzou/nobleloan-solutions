import type { Metadata } from "next";
import "./globals.css";

import SyncProvider from "@/lib/SyncProvider";
import ServiceWorkerRegistration from "@/components/ServiceWorkerRegistration";

export const metadata: Metadata = {
  title: "Growth Finance Services Ltd — Loans & Financial Services",
  description:
    "Growth Finance Services Ltd — licensed loan products, online applications, and secure account management for individuals, businesses, and farmers in Rwanda.",
  icons: {
    icon: "/logo-mark.png",
    shortcut: "/logo-mark.png",
    apple: "/logo-mark.png",
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
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

        {/* Automatically synchronizes queued offline requests */}
        <SyncProvider />

        {children}
      </body>
    </html>
  );
}

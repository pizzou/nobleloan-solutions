"use strict";

/*
 * Security policy:
 * - Cache only same-origin public static assets.
 * - Never intercept navigations, API calls, documents, authenticated pages,
 *   reports, dashboard routes, or mutation requests.
 * - Never serve stale business/financial data after a network failure.
 */
const CACHE_NAME = "noble-public-static-v4";
const OWNED_CACHE_PREFIXES = ["noble-public-", "loansaas-"];
const STATIC_PREFIXES = [
  "/_next/static/",
  "/images/",
  "/icons/",
  "/fonts/"
];
const STATIC_FILES = new Set([
  "/favicon.ico",
  "/favIcon.png",
  "/manifest.json",
  "/noble-loan-solutions-logo.svg",
  "/noble-logo.svg"
]);

function isPublicStaticAsset(request, url) {
  if (request.method !== "GET") return false;
  if (url.origin !== self.location.origin) return false;
  if (request.mode === "navigate" || request.destination === "document") return false;
  if (url.search) return false;
  if (request.headers.has("Authorization")) return false;

  // An allowlist, not a denylist: no API or user-specific URL can be cached
  // merely because it doesn't match a protected-path pattern.
  return STATIC_PREFIXES.some((prefix) => url.pathname.startsWith(prefix)) ||
    STATIC_FILES.has(url.pathname);
}

function canStoreStaticResponse(response) {
  if (!response || !response.ok || response.type === "opaque") return false;
  const control = (response.headers.get("Cache-Control") || "").toLowerCase();
  if (control.includes("no-store") || control.includes("private") || control.includes("no-cache")) {
    return false;
  }
  if ((response.headers.get("Vary") || "").trim() === "*") return false;
  return true;
}

async function deleteOwnedCaches() {
  const keys = await caches.keys();
  await Promise.all(keys
    .filter((key) => OWNED_CACHE_PREFIXES.some((prefix) => key.startsWith(prefix)))
    .map((key) => caches.delete(key)));
}

self.addEventListener("install", (event) => {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener("activate", (event) => {
  event.waitUntil((async () => {
    await deleteOwnedCaches();
    await self.clients.claim();

    // Older open tabs may still have the previous queue/sync JavaScript in
    // memory. Reload once after this security update so those handlers stop.
    const clients = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    for (const client of clients) {
      client.postMessage({ type: "SECURITY_RELOAD_REQUIRED", version: CACHE_NAME });
    }
  })());
});

self.addEventListener("message", (event) => {
  if (event.data?.type === "CLEAR_RUNTIME_CACHES" ||
      event.data?.type === "PURGE_RUNTIME_CACHES") {
    event.waitUntil(deleteOwnedCaches());
  }
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (!isPublicStaticAsset(request, url)) return;

  event.respondWith((async () => {
    try {
      const response = await fetch(request);
      if (canStoreStaticResponse(response)) {
        const cache = await caches.open(CACHE_NAME);
        await cache.put(request, response.clone());
      }
      return response;
    } catch {
      const cache = await caches.open(CACHE_NAME);
      const cached = await cache.match(request);
      if (cached) return cached;
      return new Response("Static asset unavailable while offline.", {
        status: 503,
        headers: {
          "Content-Type": "text/plain; charset=utf-8",
          "Cache-Control": "no-store"
        }
      });
    }
  })());
});

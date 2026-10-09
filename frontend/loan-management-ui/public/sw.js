
const CACHE_NAME = "noble-public-v5";

function isNeverCachePath(pathname) {
  return (
    pathname === "/api" || pathname.startsWith("/api/") ||
    pathname === "/ws" || pathname.startsWith("/ws/") ||
    pathname === "/actuator" || pathname.startsWith("/actuator/") ||
    pathname === "/dashboard" || pathname.startsWith("/dashboard/") ||
    pathname === "/login" || pathname.startsWith("/login/") ||
    pathname.startsWith("/mfa") || pathname.startsWith("/reset-password") ||
    pathname.startsWith("/forgot-password") || pathname.startsWith("/sign/") ||
    pathname === "/internal" || pathname.startsWith("/internal/") ||
    pathname.startsWith("/documents/") || pathname.startsWith("/files/")
  );
}

function isCacheableRequest(request, url) {
  if (request.method !== "GET" || url.origin !== self.location.origin) return false;
  if (isNeverCachePath(url.pathname)) return false;
  if (request.headers.has("Authorization")) return false;
  // Never cache navigation/document responses (including public pages): pages
  // may be server-rendered and vary by cookies, query params, or applicant data.
  if (request.mode === "navigate" || request.destination === "document") return false;
  // Exclude Next image optimization because its URL can point to uploaded or
  // otherwise private images. Only known static public assets are permitted.
  return (
    url.pathname.startsWith("/_next/static/") ||
    url.pathname.startsWith("/images/") ||
    url.pathname.startsWith("/icons/") ||
    url.pathname.startsWith("/fonts/")
  );
}

self.addEventListener("install", (event) => {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("message", (event) => {
  if (event.data?.type !== "CLEAR_RUNTIME_CACHES") return;
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.map((key) => caches.delete(key))))
      .catch(() => undefined)
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (!isCacheableRequest(request, url)) return;

  event.respondWith(
    fetch(request).then((response) => {
      const cacheControl = (response.headers.get("Cache-Control") || "").toLowerCase();
      if (response.ok && !response.headers.has("Set-Cookie") && !cacheControl.includes("no-store")) {
        void caches.open(CACHE_NAME)
          .then((cache) => cache.put(request, response.clone()))
          .catch(() => undefined);
      }
      return response;
    }).catch(async () => {
      const cached = await caches.match(request);
      return cached || new Response("", { status: 504, statusText: "Offline" });
    })
  );
});

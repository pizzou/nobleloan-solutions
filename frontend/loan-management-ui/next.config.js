const nextConfig = {
  output: "standalone",
  reactStrictMode: true,
  async rewrites() {
    const api = process.env.NEXT_PUBLIC_API_URL?.trim();
    if (!api || api.startsWith("/")) return [];
    return [{ source: "/api/:path*", destination: `${api.replace(/\/$/, "")}/:path*` }];
  },
  async headers() {
    const configuredApi = process.env.NEXT_PUBLIC_API_URL?.trim();
    let apiOrigin = "'self'";
    let websocketOrigin = null;

    if (configuredApi && !configuredApi.startsWith("/")) {
      try {
        const parsed = new URL(configuredApi);
        apiOrigin = parsed.origin;
        websocketOrigin = parsed.protocol === "https:"
          ? `wss://${parsed.host}`
          : parsed.protocol === "http:"
            ? `ws://${parsed.host}`
            : null;
      } catch {
        // Keep the fail-safe 'self' policy if the build-time API URL is invalid.
      }
    }

    const connectSources = [
      "'self'",
      apiOrigin,
      "https://api.frankfurter.dev",
      websocketOrigin,
    ].filter(Boolean).join(" ");

    return [
      {
        source: "/(.*)",
        headers: [
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "no-referrer" },
          { key: "Permissions-Policy", value: "camera=(self), microphone=(), geolocation=(), payment=()" },
          { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
          { key: "Cross-Origin-Resource-Policy", value: "same-origin" },
          { key: "Content-Security-Policy", value: [
            "default-src 'self'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'",
            "object-src 'none'",
            "script-src 'self' 'unsafe-inline'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com data:",
            "img-src 'self' data: blob: https:",
            `connect-src ${connectSources}`,
            "frame-src 'self' https:",
            "worker-src 'self' blob:",
            "manifest-src 'self'",
          ].join("; ") },
        ],
      },
    ];
  },
  images: {
    remotePatterns: [
      { protocol: "https", hostname: "maps.googleapis.com" },
      { protocol: "https", hostname: "fonts.googleapis.com" },
    ],
  },
};
module.exports = nextConfig;
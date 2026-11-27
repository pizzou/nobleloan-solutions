import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";

const siteUrl = (
  process.env.NEXT_PUBLIC_SITE_URL ||
  process.env.NEXT_PUBLIC_SITE_WEBSITE ||
  "https://nobleloansolutions.rw"
).replace(/\/+$/, "");

/**
 * Notify IndexNow (Bing and participating engines) about public URLs.
 * Protect this endpoint with INDEXNOW_SUBMISSION_TOKEN; never expose that token
 * to browser code or use a NEXT_PUBLIC_ prefix for it.
 */
export async function POST(request: NextRequest) {
  const configuredToken = process.env.INDEXNOW_SUBMISSION_TOKEN;
  const suppliedToken = request.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
  const key = process.env.INDEXNOW_KEY;

  if (!configuredToken || !key) {
    return NextResponse.json({ error: "IndexNow is not configured on this deployment." }, { status: 503 });
  }
  if (!suppliedToken || suppliedToken !== configuredToken) {
    return NextResponse.json({ error: "Unauthorized." }, { status: 401 });
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "Request body must be valid JSON." }, { status: 400 });
  }

  const candidate = (body as { urls?: unknown } | null)?.urls;
  if (!Array.isArray(candidate) || candidate.length < 1 || candidate.length > 100) {
    return NextResponse.json({ error: "Provide between 1 and 100 public URLs in a urls array." }, { status: 400 });
  }

  const allowedOrigin = new URL(siteUrl).origin;
  const urls: string[] = [];
  for (const item of candidate) {
    if (typeof item !== "string") {
      return NextResponse.json({ error: "Every URL must be a string." }, { status: 400 });
    }
    let parsed: URL;
    try { parsed = new URL(item); } catch {
      return NextResponse.json({ error: `Invalid URL: ${item}` }, { status: 400 });
    }
    if (parsed.origin !== allowedOrigin || parsed.username || parsed.password || parsed.hash) {
      return NextResponse.json({ error: "Only absolute public URLs on the configured canonical site are allowed." }, { status: 400 });
    }
    if (/^\/(api|dashboard|login|sign|mfa-setup|forgot-password|reset-password)(\/|$)/i.test(parsed.pathname)) {
      return NextResponse.json({ error: "Private or API URLs cannot be submitted for indexing." }, { status: 400 });
    }
    urls.push(parsed.toString());
  }

  const uniqueUrls = [...new Set(urls)];
  const keyFileName = `${key}.txt`;
  const keyLocation = `${siteUrl}/${keyFileName}`;
  try {
    const response = await fetch("https://api.indexnow.org/indexnow", {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify({
        host: new URL(siteUrl).host,
        key,
        keyLocation,
        urlList: uniqueUrls,
      }),
      signal: AbortSignal.timeout(10_000),
    });
    if (!response.ok) {
      return NextResponse.json({ error: "IndexNow rejected the submission.", upstreamStatus: response.status }, { status: 502 });
    }
    return NextResponse.json({ submitted: uniqueUrls.length, message: "IndexNow accepted the URL notification. Indexing is not guaranteed." });
  } catch {
    return NextResponse.json({ error: "Could not reach IndexNow. Try again later." }, { status: 502 });
  }
}

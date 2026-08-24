"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { authApi } from "@/services/api";
import { AuthContext, useAuthState } from "@/hooks/useAuth";
import { Button } from "@/components/ui/Button";
import { FormGroup, Input, Alert } from "@/components/ui/Form";
import Link from "next/link";

/* ============================================================
   NOBLE LOAN SOLUTIONS BRAND
   ============================================================ */

const NAVY = "#0B1F3A";
const DARK_NAVY = "#071426";
const BLUE = "#123B66";
const YELLOW = "#F4C430";
const LIGHT_YELLOW = "#FFF8D8";

function LoginInner() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const [mfaCode, setMfaCode] = useState("");
  const [mfaRequired, setMfaRequired] = useState(false);

  const [otp, setOtp] = useState("");
  const [otpRequired, setOtpRequired] = useState(false);
  const [otpMessage, setOtpMessage] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const { login } = useAuthState();
  const router = useRouter();

  /* ============================================================
     LOGIN
     ============================================================ */

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    setError("");
    setLoading(true);

    try {
      const res: any = await authApi.login(
        email,
        password,
        mfaRequired ? mfaCode : undefined,
        otpRequired ? otp : undefined,
      );

      /* --------------------------------------------------------
         MFA SETUP REQUIRED
         -------------------------------------------------------- */

      if (res?.mfaSetupRequired) {
        sessionStorage.setItem("mfaSetupToken", res.setupToken);

        sessionStorage.setItem("mfaSetupEmail", res.email ?? email);

        router.push("/mfa-setup");
        return;
      }

      /* --------------------------------------------------------
         MFA REQUIRED
         -------------------------------------------------------- */

      if (res?.mfaRequired) {
        setMfaRequired(true);
        setLoading(false);
        return;
      }

      /* --------------------------------------------------------
         OTP REQUIRED
         -------------------------------------------------------- */

      if (res?.otpRequired) {
        setOtpRequired(true);

        setOtpMessage(
          res.message || "We sent a 6-digit verification code to your email.",
        );

        setLoading(false);
        return;
      }

      /* --------------------------------------------------------
         TOKEN VALIDATION
         -------------------------------------------------------- */

      if (!res?.token) {
        setError("Unexpected response from server. Please try again.");

        setLoading(false);
        return;
      }

      /* --------------------------------------------------------
         LOGIN SUCCESS
         -------------------------------------------------------- */

      login(res, res.token);

      router.replace("/dashboard");
    } catch (err: any) {
      setError(
        err?.message ||
          "Invalid credentials. Please check your email and password.",
      );

      setLoading(false);
    }
  };

  /* ============================================================
     RESET VERIFICATION
     ============================================================ */

  const handleBackToLogin = () => {
    setMfaRequired(false);
    setOtpRequired(false);

    setMfaCode("");
    setOtp("");

    setError("");
    setOtpMessage("");
  };

  /* ============================================================
     RENDER
     ============================================================ */

  return (
    <div className="min-h-screen flex">
      {/* ======================================================
          LEFT BRAND PANEL
          ====================================================== */}

      <div
        className="hidden lg:flex w-1/2 flex-col justify-between px-16 py-12 relative overflow-hidden"
        style={{
          background: `linear-gradient(
            145deg,
            ${DARK_NAVY} 0%,
            ${NAVY} 55%,
            ${BLUE} 100%
          )`,
        }}
      >
        {/* Decorative circles */}

        <div
          className="absolute -right-32 -top-32 w-96 h-96 rounded-full opacity-10"
          style={{
            backgroundColor: YELLOW,
          }}
        />

        <div
          className="absolute -left-24 bottom-20 w-72 h-72 rounded-full opacity-5"
          style={{
            backgroundColor: YELLOW,
          }}
        />

        {/* --------------------------------------------------
            LOGO / BRAND
            -------------------------------------------------- */}

        <Link href="/" className="flex items-center gap-3 relative z-10">
          <div
            className="w-11 h-11 rounded-xl flex items-center justify-center font-extrabold text-lg shadow-lg"
            style={{
              backgroundColor: YELLOW,
              color: NAVY,
            }}
          >
            N
          </div>

          <div>
            <div className="text-white font-extrabold tracking-tight">
              Noble Loan Solutions
            </div>

            <div className="text-white/50 text-xs mt-0.5">Staff Portal</div>
          </div>
        </Link>

        {/* --------------------------------------------------
            MAIN BRAND MESSAGE
            -------------------------------------------------- */}

        <div className="relative z-10">
          <div
            className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold mb-8 uppercase tracking-wide"
            style={{
              backgroundColor: "rgba(244,196,48,0.12)",
              border: `1px solid rgba(244,196,48,0.35)`,
              color: YELLOW,
            }}
          >
            Secure Staff Portal
          </div>

          <h1 className="text-4xl font-extrabold text-white leading-tight mb-6 tracking-tight">
            Internal Operations
            <br />
            <span
              style={{
                color: YELLOW,
              }}
            >
              &amp; Loan Management
            </span>
          </h1>

          <p className="text-white/70 leading-relaxed mb-10 max-w-lg">
            Manage loans, borrowers, payments, KYC/AML compliance, FX rates, and
            reporting for Noble Loan Solutions.
          </p>

          {/* ------------------------------------------------
              FEATURES
              ------------------------------------------------ */}

          <div className="grid grid-cols-2 gap-3">
            {[
              "KYC / AML",
              "Multi-factor auth",
              "FX rates",
              "Webhooks",
              "Bulk disbursement",
              "Audit reports",
            ].map((feature) => (
              <div
                key={feature}
                className="flex items-center gap-2 rounded-xl px-4 py-3"
                style={{
                  backgroundColor: "rgba(255,255,255,0.05)",
                  border: "1px solid rgba(255,255,255,0.10)",
                }}
              >
                <span
                  className="w-2 h-2 rounded-full shrink-0"
                  style={{
                    backgroundColor: YELLOW,
                  }}
                />

                <span className="text-white text-sm font-medium">
                  {feature}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* --------------------------------------------------
            FOOTER
            -------------------------------------------------- */}

        <div className="text-white/35 text-xs relative z-10">
          © {new Date().getFullYear()} Noble Loan Solutions. All rights
          reserved.
        </div>
      </div>

      {/* ======================================================
          RIGHT LOGIN PANEL
          ====================================================== */}

      <div className="flex-1 flex items-center justify-center px-6 sm:px-8 bg-gray-50">
        <div className="w-full max-w-sm">
          {/* --------------------------------------------------
              MOBILE BRAND
              -------------------------------------------------- */}

          <div className="lg:hidden mb-8">
            <Link href="/" className="flex items-center gap-3">
              <div
                className="w-10 h-10 rounded-xl flex items-center justify-center font-extrabold text-lg"
                style={{
                  backgroundColor: YELLOW,
                  color: NAVY,
                }}
              >
                N
              </div>

              <div>
                <div className="text-sm font-extrabold text-gray-900">
                  Noble Loan Solutions
                </div>

                <div className="text-xs text-gray-400">Staff Portal</div>
              </div>
            </Link>
          </div>

          {/* --------------------------------------------------
              LOGIN HEADING
              -------------------------------------------------- */}

          <div className="mb-6">
            <div
              className="w-10 h-1 rounded-full mb-4"
              style={{
                backgroundColor: YELLOW,
              }}
            />

            <h2 className="text-2xl font-extrabold text-gray-900 mb-1">
              Staff Sign In
            </h2>

            <p className="text-sm text-gray-500">
              Access your organization dashboard
            </p>
          </div>

          {/* --------------------------------------------------
              ERROR
              -------------------------------------------------- */}

          {error && (
            <div className="mb-4">
              <Alert type="error">{error}</Alert>
            </div>
          )}

          {/* ==================================================
              LOGIN FORM
              ================================================== */}

          <form onSubmit={handleSubmit} className="space-y-4">
            {/* ------------------------------------------------
                NORMAL LOGIN
                ------------------------------------------------ */}

            {!mfaRequired && !otpRequired ? (
              <>
                <FormGroup label="Email Address" required>
                  <Input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="you@organization.com"
                    autoComplete="email"
                  />
                </FormGroup>

                <FormGroup label="Password" required>
                  <Input
                    type="password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••"
                    autoComplete="current-password"
                  />
                </FormGroup>
              </>
            ) : mfaRequired ? (
              /* ------------------------------------------------
                 MFA
                 ------------------------------------------------ */

              <div className="text-center py-4">
                <div
                  className="w-12 h-12 mx-auto rounded-xl flex items-center justify-center mb-4"
                  style={{
                    backgroundColor: LIGHT_YELLOW,
                  }}
                >
                  <span className="text-xl">🔐</span>
                </div>

                <div className="font-bold text-gray-900 mb-1">
                  Two-Factor Authentication
                </div>

                <div className="text-gray-500 text-sm mb-4">
                  Enter the 6-digit code from your authenticator app.
                </div>

                <FormGroup label="Verification Code">
                  <Input
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    placeholder="000000"
                    value={mfaCode}
                    onChange={(e) =>
                      setMfaCode(e.target.value.replace(/\D/g, ""))
                    }
                    className="text-center text-2xl tracking-[0.5em] font-mono"
                    autoFocus
                  />
                </FormGroup>
              </div>
            ) : (
              /* ------------------------------------------------
                 EMAIL OTP
                 ------------------------------------------------ */

              <div className="text-center py-4">
                <div
                  className="w-12 h-12 mx-auto rounded-xl flex items-center justify-center mb-4"
                  style={{
                    backgroundColor: LIGHT_YELLOW,
                  }}
                >
                  <span className="text-xl">✉️</span>
                </div>

                <div className="font-bold text-gray-900 mb-1">
                  Check Your Email
                </div>

                <div className="text-gray-500 text-sm mb-4">{otpMessage}</div>

                <FormGroup label="Verification Code">
                  <Input
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    placeholder="000000"
                    value={otp}
                    onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
                    className="text-center text-2xl tracking-[0.5em] font-mono"
                    autoFocus
                  />
                </FormGroup>
              </div>
            )}

            {/* =================================================
                SUBMIT BUTTON
                ================================================= */}

            <Button
              type="submit"
              className="w-full justify-center py-3 text-base font-bold border-0 shadow-md hover:shadow-lg transition-all"
              loading={loading}
            >
              {mfaRequired || otpRequired ? "Verify & Sign In" : "Sign In →"}
            </Button>
          </form>

          {/* ==================================================
              BACK TO LOGIN
              ================================================== */}

          {(mfaRequired || otpRequired) && (
            <button
              type="button"
              onClick={handleBackToLogin}
              className="mt-4 text-xs font-semibold w-full text-center transition-colors"
              style={{
                color: BLUE,
              }}
            >
              ← Back to sign in
            </button>
          )}

          {/* ==================================================
              SECURITY NOTE
              ================================================== */}

          <div className="mt-8 pt-5 border-t border-gray-200">
            <div className="flex items-center justify-center gap-2 text-xs text-gray-400">
              <span
                className="w-2 h-2 rounded-full"
                style={{
                  backgroundColor: YELLOW,
                }}
              />
              Secure access for authorized staff only
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ============================================================
   PAGE
   ============================================================ */

export default function LoginPage() {
  const auth = useAuthState();

  return (
    <AuthContext.Provider value={auth}>
      <LoginInner />
    </AuthContext.Provider>
  );
}

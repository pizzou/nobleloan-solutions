const fs = require("node:fs");
const assert = require("node:assert/strict");

const page = fs.readFileSync(
  "app/dashboard/loans/recycle-bin/page.tsx",
  "utf8",
);

assert.match(page, /user\?\.role\s*\?\?/,
  "Recycle-bin authorization must read the string role exposed by AuthResponse");
assert.match(page, /normalizedRole\s*===\s*"BUSINESS_OWNER"/,
  "Business Owner role must be normalized before the UI gate");
assert.doesNotMatch(page, /user\s+as\s+\{\s*role\?\s*:\s*\{\s*name\?/,
  "Do not treat AuthResponse.role as a nested role object");
assert.match(page, /loanApi\.recycleBin\(\)/,
  "The recycle-bin page must keep loading recycled loans from the API");
assert.match(page, /loanApi\.restore\(item\.id,\s*confirmation\)/,
  "The existing restore action must remain wired to the API");

console.log("Loan recycle-bin role regression checks passed (5 assertions).");

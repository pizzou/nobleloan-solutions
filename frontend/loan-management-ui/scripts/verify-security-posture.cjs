const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "..");
const checks = [
  ["public/sw.js", /request\.mode\s*===\s*["']navigate["']\s*\|\|\s*request\.destination\s*===\s*["']document["']/, "service worker must bypass navigation/document responses"],
  ["public/sw.js", /url\.pathname\.startsWith\(["']\/_next\/static\/["']\)/, "service worker must restrict offline cache to static public assets"],
  ["lib/offlineDb.ts", /DB_VERSION\s*=\s*6/, "offline DB schema must migrate legacy queued data"],
  ["lib/offlineDb.ts", /This financial action requires a live connection/, "offline financial writes must be rejected"],
  ["lib/offlineDb.ts", /export async function cacheGet[\s\S]*?return null/, "browser financial response cache must be disabled"],
  ["services/api.ts", /isHtmlErrorPayload/, "HTML proxy/challenge errors must be sanitized"],
  ["lib/securityCleanup.ts", /clearSensitiveOfflineData/, "logout/session cleanup must clear IndexedDB financial residue"],
];
let failures = 0;
for (const [relative, pattern, description] of checks) {
  const file = path.join(root, relative);
  const source = fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
  if (!pattern.test(source)) {
    console.error(`FAIL ${relative}: ${description}`);
    failures++;
  } else console.log(`PASS ${relative}: ${description}`);
}
if (failures) process.exit(1);
console.log("Critical frontend security invariants passed.");

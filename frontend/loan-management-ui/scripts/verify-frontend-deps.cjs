const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const packageJson = JSON.parse(fs.readFileSync(path.join(root, "package.json"), "utf8"));
const lockPath = path.join(root, "package-lock.json");

function fail(message) {
  console.error(`
Frontend dependency verification failed: ${message}`);
  console.error("Run scripts/repair-frontend.ps1 in PowerShell from this frontend directory.");
  process.exitCode = 1;
}

try {
  const lock = JSON.parse(fs.readFileSync(lockPath, "utf8"));
  const lockedRoot = lock.packages?.[""];
  if (!lockedRoot) throw new Error("package-lock.json has no root package entry");

  for (const name of ["next", "eslint-config-next"]) {
    const declared = packageJson.dependencies?.[name] ?? packageJson.devDependencies?.[name];
    const locked = lockedRoot.dependencies?.[name] ?? lockedRoot.devDependencies?.[name];
    const installedPath = path.join(root, "node_modules", name, "package.json");
    if (!declared || !locked) throw new Error(`${name} is missing from package.json or package-lock.json`);
    if (declared !== locked) throw new Error(`${name} version mismatch: package.json=${declared}, lock=${locked}`);
    if (!fs.existsSync(installedPath)) throw new Error(`${name} is not installed; run npm ci`);
    const installed = JSON.parse(fs.readFileSync(installedPath, "utf8")).version;
    if (installed !== declared) throw new Error(`${name} installed version ${installed} does not match ${declared}`);
  }

  require.resolve("next/babel", { paths: [root] });
  const nextRoot = path.join(root, "node_modules", "next");
  for (const required of ["babel.js", "types/index.d.ts", "dist/compiled/babel/bundle.js"]) {
    if (!fs.existsSync(path.join(nextRoot, required))) throw new Error(`Next.js installation is incomplete: missing next/${required}`);
  }

  console.log(`Frontend dependency integrity OK (Next ${packageJson.dependencies.next}; eslint-config-next ${packageJson.devDependencies["eslint-config-next"]}).`);
} catch (error) {
  fail(error instanceof Error ? error.message : String(error));
}

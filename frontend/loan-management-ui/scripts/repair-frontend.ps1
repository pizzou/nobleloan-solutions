$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

node --version
if ($LASTEXITCODE -ne 0) { throw "Node.js is not installed or not on PATH." }
npm --version
if ($LASTEXITCODE -ne 0) { throw "npm is not installed or not on PATH." }

foreach ($path in @("node_modules", ".next")) {
  $target = Join-Path $projectRoot $path
  if (Test-Path $target) { Remove-Item $target -Recurse -Force }
}

npm cache verify
if ($LASTEXITCODE -ne 0) { throw "npm cache verification failed." }
npm ci
if ($LASTEXITCODE -ne 0) { throw "npm ci failed. Check registry connectivity and npm output." }
npm run verify:deps
if ($LASTEXITCODE -ne 0) { throw "Next/Babel dependency verification failed." }
npm run typecheck
if ($LASTEXITCODE -ne 0) { throw "TypeScript check failed." }
npm run lint:ci
if ($LASTEXITCODE -ne 0) { throw "ESLint check failed." }
npm run build
if ($LASTEXITCODE -ne 0) { throw "Production build failed." }

Write-Host "Frontend reinstall and verification completed successfully." -ForegroundColor Green

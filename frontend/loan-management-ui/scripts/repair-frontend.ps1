$ErrorActionPreference = "Stop"
$frontendRoot = Split-Path -Parent $PSScriptRoot
Set-Location $frontendRoot

Write-Host "Removing incomplete local build/dependency artifacts..." -ForegroundColor Yellow
if (Test-Path ".\node_modules") { Remove-Item ".\node_modules" -Recurse -Force }
if (Test-Path ".\.next") { Remove-Item ".\.next" -Recurse -Force }

Write-Host "Verifying npm cache..." -ForegroundColor Cyan
npm cache verify
if ($LASTEXITCODE -ne 0) { throw "npm cache verify failed" }

Write-Host "Installing exact locked dependencies with npm ci..." -ForegroundColor Cyan
npm ci
if ($LASTEXITCODE -ne 0) { throw "npm ci failed; check network/proxy access and package-lock.json" }

Write-Host "Running dependency, security, type, lint and production build checks..." -ForegroundColor Cyan
npm run verify
if ($LASTEXITCODE -ne 0) { throw "Frontend verification failed. Fix the reported error before deployment." }
Write-Host "Frontend repair and validation completed." -ForegroundColor Green

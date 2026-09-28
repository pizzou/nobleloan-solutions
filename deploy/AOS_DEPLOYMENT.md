# AOS VM Deployment — Noble Loan Solutions

This stack is designed for an AOS cloud VM. AOS describes its cloud service as VM-based, so the deployment model is Docker Engine + Docker Compose on the VM, with Nginx exposing only ports 80/443.

## 1. Prepare the AOS VM

Use a supported Linux distribution with Docker Engine and the Docker Compose v2 plugin installed. Open only TCP 80 and 443 to the internet. Keep PostgreSQL, Spring Boot, and Next.js private to the Docker network; the supplied Compose file intentionally does not publish their ports.

## 2. Install the project

Copy the repository to the VM and run from the repository root:

```bash
cp .env.example .env
chmod 600 .env
chmod +x deploy/scripts/*.sh
```

Fill `.env` with real production values. Do not use placeholders. The backend deliberately refuses production startup when required secrets, provider credentials, organization identity, or evidence gates are missing.

Generate cryptographic application/backup secrets with:

```bash
./deploy/scripts/generate-secrets.sh
```

Store the values in your approved secrets manager and copy the runtime values into `.env` only on the VM.

## 3. DNS and TLS

Point the production DNS A/AAAA records for the values in `DOMAIN` to the AOS VM. Allow inbound TCP 80 and 443. Then obtain the certificate before the normal production deployment:

```bash
./deploy/scripts/ssl-init.sh admin@YOUR-REAL-DOMAIN.tld YOUR-REAL-DOMAIN.tld www.YOUR-REAL-DOMAIN.tld
```

The script uses a temporary self-signed certificate only as an ACME bootstrap mechanism; it replaces it with the real Let’s Encrypt certificate before returning. `deploy.sh` will refuse to run without a real certificate/key already present.

## 4. Production deploy

```bash
./deploy/scripts/deploy.sh
```

The production Docker build runs backend Maven verification tests and builds the frontend. Deployment waits for PostgreSQL, backend, frontend, and Nginx health, then verifies `https://DOMAIN/healthz` through the public TLS listener.

## 5. Backups, DR and TLS renewal

Before installing the cron jobs, configure a real encrypted backup key and an off-site upload command containing the literal `{file}` placeholder. Then:

```bash
./deploy/scripts/install-production-cron.sh
```

This schedules a daily encrypted/off-site PostgreSQL backup, a weekly isolated restore drill, and weekly Let’s Encrypt renewal.

Run a manual health check at any time:

```bash
./deploy/scripts/health-check.sh
```

## 6. Required production evidence

The application intentionally keeps the `PROD_GATE_*` variables false until the corresponding organizational, security, regulatory, accounting, backup, monitoring, and production-infrastructure evidence exists. Do not set these values to true simply to make startup succeed.

## 7. AOS infrastructure considerations

AOS cloud service documentation describes VM compute, storage and backup offerings. Select VM/storage capacity based on expected borrower/application volume, document the backup retention policy, and keep application/database backups separate from the VM itself.

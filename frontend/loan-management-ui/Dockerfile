# ============================================================
# FINOS / LOANSAAS PRO - PRODUCTION FRONTEND IMAGE
# AOS RWANDA DEPLOYMENT
# ============================================================

# ------------------------------------------------------------
# BUILD STAGE
# ------------------------------------------------------------

FROM node:20-alpine AS builder

WORKDIR /app

# Disable Next.js telemetry
ENV NEXT_TELEMETRY_DISABLED=1

# Copy dependency manifests first for Docker layer caching
COPY package.json package-lock.json* ./

# Install exact locked dependencies
RUN npm ci

# Copy frontend source
COPY . .

# ------------------------------------------------------------
# PUBLIC API CONFIGURATION
# ------------------------------------------------------------
#
# IMPORTANT:
#
# This value is consumed by browser-side JavaScript.
#
# Do NOT use:
#
#   http://backend:8080/api
#
# "backend" is only resolvable inside the Docker network.
#
# The public browser endpoint is:
#
#   /api
#
# Nginx will proxy:
#
#   /api/*
#
# to:
#
#   backend:8080
#
# ------------------------------------------------------------

ARG NEXT_PUBLIC_API_URL=/api

ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}

# ------------------------------------------------------------
# BUILD
# ------------------------------------------------------------

RUN npm run build


# ------------------------------------------------------------
# RUNTIME STAGE
# ------------------------------------------------------------

FROM node:20-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# ------------------------------------------------------------
# NON-ROOT USER
# ------------------------------------------------------------

RUN addgroup -S loansaas \
    && adduser -S loansaas -G loansaas

# ------------------------------------------------------------
# NEXT.JS STANDALONE OUTPUT
# ------------------------------------------------------------

COPY --from=builder /app/public ./public

COPY --from=builder \
    --chown=loansaas:loansaas \
    /app/.next/standalone ./

COPY --from=builder \
    --chown=loansaas:loansaas \
    /app/.next/static ./.next/static

# Ensure application files belong to the runtime user
RUN chown -R loansaas:loansaas /app

USER loansaas

# ------------------------------------------------------------
# SERVER PORT
# ------------------------------------------------------------

EXPOSE 3000

# ------------------------------------------------------------
# CONTAINER HEALTH CHECK
# ------------------------------------------------------------

HEALTHCHECK \
    --interval=30s \
    --timeout=10s \
    --start-period=30s \
    --retries=3 \
    CMD wget -qO- http://127.0.0.1:3000/ \
        || exit 1

# ------------------------------------------------------------
# START NEXT.JS STANDALONE SERVER
# ------------------------------------------------------------

CMD ["node", "server.js"]
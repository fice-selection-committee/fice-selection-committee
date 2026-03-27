# Phase 13: Docker & Deployment

**Depends on**: Phase 1-11
**Blocks**: None

## Checklist

- [ ] `client/web/docker/Dockerfile` — Multi-stage production build:
  ```dockerfile
  # Stage 1: Dependencies
  FROM node:20-alpine AS deps
  RUN corepack enable && corepack prepare pnpm@latest --activate
  WORKDIR /app
  COPY package.json pnpm-lock.yaml ./
  RUN pnpm install --frozen-lockfile

  # Stage 2: Build
  FROM node:20-alpine AS builder
  RUN corepack enable && corepack prepare pnpm@latest --activate
  WORKDIR /app
  COPY --from=deps /app/node_modules ./node_modules
  COPY . .
  ENV NEXT_TELEMETRY_DISABLED=1
  RUN pnpm build

  # Stage 3: Runner
  FROM node:20-alpine AS runner
  WORKDIR /app
  ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1
  RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs
  COPY --from=builder /app/public ./public
  COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
  COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
  USER nextjs
  EXPOSE 3000
  ENV PORT=3000
  CMD ["node", "server.js"]
  ```

- [ ] `client/web/.dockerignore`:
  ```
  node_modules
  .next
  .git
  tests
  *.md
  .env.local
  ```

- [ ] Add `web` service to `infra/docker-compose.services.yml`:
  ```yaml
  web:
    build:
      context: ../client/web
      dockerfile: docker/Dockerfile
    container_name: sc-web
    restart: unless-stopped
    networks:
      - sc-net
    depends_on:
      gateway:
        condition: service_healthy
    environment:
      NEXT_PUBLIC_API_BASE: http://gateway:8080
    ports:
      - "3000:3000"
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:3000"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 20s
  ```

- [ ] Verify: `docker build -t sc-web ./client/web -f client/web/docker/Dockerfile`
- [ ] Verify: container starts and app is accessible on port 3000
- [ ] Verify: app connects to gateway inside Docker network

## Deliverables
- Multi-stage Dockerfile with standalone output (~100MB image)
- Docker Compose integration with existing infrastructure
- Health check configured
- Production-ready container

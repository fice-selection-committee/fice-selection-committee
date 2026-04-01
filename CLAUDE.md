# CLAUDE.md — Engineering Operating Standard

This document governs how Claude Code operates inside the FICE Selection Committee repository. Every instruction is binding. Ambiguity must be resolved by asking, not by assuming. Violation of any rule in this document or its referenced files constitutes an engineering failure.

## Reference Files

Read on-demand when the corresponding agent, skill, or workflow is activated. Do NOT paraphrase from memory — read the file.

| File | When to Read |
|---|---|
| `docs/claude/agents.md` | Before executing as any agent. MUST be read, not recalled. |
| `docs/claude/skills.md` | Before executing any skill. MUST be read, not recalled. |
| `docs/claude/architecture.md` | For service topology, shared libs, infrastructure, compose topology |
| `docs/claude/docker.md` | For Docker startup, known gaps, developer workflows, improvement mandate |
| `docs/claude/testing.md` | For TDD protocol, bug fix protocol, test infrastructure, layer mapping |
| `docs/claude/build.md` | For build commands, dependency chain, code style, CI/CD reference |
| `docs/claude/interaction-standards.md` | For hover, focus, disabled, loading, cursor, transition, color-state rules in frontend components |
| `docs/claude/ios-atmosphere.md` | For typography scale, button system, motion tokens, elevation, color rules, spacing guidelines |

---

## 1. System Mental Model

These are architectural truths. A proposed change that violates any is architecturally invalid.

- **Bounded contexts**: Each service owns its domain, database, schema, data. Cross-service access only through Feign HTTP or RabbitMQ events. Direct cross-database access is forbidden.
- **Contracts are boundaries**: API endpoints, Feign interfaces, and event payloads are explicit integration surfaces. Any shape change is breaking until proven otherwise.
- **Gateway is the entry point**: Port 8080. Sole controlled entry for external traffic. No service exposes endpoints directly to consumers.
- **Frontend is a projection layer**: Consumer of APIs, never an authority on business logic, permissions, or data truth. Frontend validation is UX convenience, not a security boundary.
- **Shared libraries are distributed changes**: Libraries in `server/libs/` affect every consumer. `./gradlew publishToMavenLocal` from `server/` is mandatory before building dependent services. A library change without consumer verification is a broken build waiting to happen.
- **Docker is the runtime platform**: Composed containers on `sc-net`. Not optional — it is the system's operational model. "Works on my machine" without Docker is not a valid state.
- **Infrastructure is system**: PostgreSQL 16, Redis 7, RabbitMQ 3, MinIO, Mailpit, Zipkin, Prometheus + Grafana are integral components, not optional add-ons. Never assume they are installed on the host.
- **Runtime topology matters**: How services start, connect, fail, and recover is as important as the source code. Docker Compose defines the operational model. Changes to compose files are infrastructure changes.
- **Tests are not optional**: Every non-trivial change requires test coverage. Every bug fix requires regression protection. TDD sequencing is the default workflow. Implementation without tests is incomplete delivery.

---

## 2. Agent Activation

Claude MUST adopt the appropriate agent role based on task type. Multiple agents may be active simultaneously. Read `docs/claude/agents.md` for full specifications before executing as any agent.

| Agent | Activation Trigger |
|---|---|
| **Solution Architect** | Change spans multiple services, shared library, event contracts, or runtime topology |
| **Backend Engineer** | Java source, Gradle, Spring config, DB migrations, business logic |
| **Frontend Engineer** | TypeScript/React, styling, routing, API integration in `client/web/` |
| **QA / Test Architect** | Every non-trivial change (always active in advisory capacity) |
| **DevOps / Infrastructure** | GitHub Actions, Gradle config, Docker publishing, deployment |
| **API Contract Guardian** | REST endpoints, DTOs, Feign interfaces, event payloads |
| **Docker Runtime** | Container startup, communication, persistence, developer workflow, compose changes |
| **Distributed Systems Risk Reviewer** | Cross-service boundary, async messaging, startup ordering, failure handling |

**Multi-agent activation**: When a task spans multiple concerns (e.g., new Feign client + Docker compose change + contract test), activate all relevant agents. Each agent's constraints apply simultaneously.

---

## 3. Skill Activation

Skills are verification procedures that produce specific outputs. Read `docs/claude/skills.md` for full procedures before executing any skill.

| Skill | Trigger |
|---|---|
| Service Analysis | Task targeting a specific backend service |
| API Design and Validation | Creating or modifying REST endpoints |
| Test Strategy Design | Change requiring new test coverage |
| Refactoring Safety Check | Any refactoring operation |
| Dependency Impact Analysis | Adding, removing, or upgrading a dependency |
| Distributed Flow Reasoning | Inter-service communication change |
| Failure Scenario Analysis | Startup, health check, or inter-service dependency change |
| Docker Compose Design Review | Compose files, Dockerfiles, or startup config change |
| Contract Evolution Safety Review | Shared library, Feign interface, or event payload change |
| Bug Reproduction and Validation | Any bug report or defect fix |
| Observability Readiness Check | New service or instrumentation change |
| Runtime Topology Validation | Service startup, network, dependency ordering, or health check change |
| Regression Test Gap Review | After any fix, to verify regression protection is sufficient |

**Mandatory coupling**: Every skill that changes behavior, contracts, runtime wiring, or cross-service flow MUST define: what tests should fail first, what tests must exist after the change, whether new regression tests are required.

---

## 4. Operational Rules

### 4.1 Context Validation

- **Verify file existence** before proposing to create or modify. Use `Glob` or `Read`.
- **Read before writing**. Never modify code you have not read in this conversation.
- **Inspect dependencies** — what other files, services, or contracts depend on the code being changed.
- **Distinguish observation from inference** — state whether a fact is from reading the file ("observed") or is an assumption ("inferred"). If inferred, state the basis.
- **Do not assume framework conventions** — Next.js 16 has breaking changes from earlier versions; read `node_modules/next/dist/docs/` when uncertain. Verify Spring Boot 3.5.6 behavior against actual config in `application.yml`, not training data.
- **Ground in repository structure** — recommendations must reference actual paths, actual file names, actual class names found in this repository.

### 4.2 Change Safety

Before proposing any change, evaluate:

| Dimension | Question |
|---|---|
| **Blast radius** | Local to one file, one service, or crosses service boundaries? |
| **Contract impact** | Modifies API endpoint, Feign client interface, event payload, or shared DTO? |
| **Data impact** | Database schema change? New Flyway migration required? |
| **Runtime impact** | Docker startup, health checks, service dependencies, compose topology affected? |
| **Environment impact** | New environment variables? Added to `infra/.env.example`? Consistent naming? |
| **Build chain impact** | Shared library changed? `publishToMavenLocal` required before service builds? |
| **Test impact** | What test layers are affected? What must fail first? What must pass after? |
| **Rollback path** | How is this reverted if it fails in production? |

Explicitly warn when a change is breaking, distributed, or affects runtime topology.

### 4.3 Microservices Rules

- NEVER introduce direct database access between services.
- NEVER create shared mutable state outside of explicit contracts (Feign, events).
- NEVER bypass the gateway for external-facing traffic.
- Treat every inter-service call as a network call that can fail, time out, or return errors.
- Prefer eventual consistency through events over synchronous orchestration.
- New inter-service calls MUST have circuit breaker and timeout configuration.
- New Feign clients MUST have contract tests in `src/integrationTest/**/contract/`.

### 4.4 Frontend Rules

- NEVER place authorization logic not also enforced on the backend. Frontend guards are UX, not security.
- NEVER store sensitive data (tokens, secrets, PII) in client-side state beyond what the auth cookie provides.
- NEVER assume backend implementation details — code against the API contract via types in `src/types/`.
- NEVER bypass Next.js proxy route guards in `src/proxy.ts`.
- Always use existing Axios client with JWT interceptor (`src/lib/api/`).
- Always define TypeScript types in `src/types/` — never use `any` for API responses.
- Use TanStack Query for server state, Zustand for client state. Do not mix.
- Use React Hook Form + Zod for form handling. Do not introduce alternative form libraries.

### 4.5 Testing Rules

For every non-trivial change, define: unit test impact, integration test impact, E2E impact, contract test impact.

**TDD mandate**: Failing test first → minimal implementation → passing validation → safe refactor. Read `docs/claude/testing.md`.

**Bug fix mandate**: Every bug fix MUST include a regression test that reproduces the bug before the fix. "Manual verification sufficient" is not acceptable when automated coverage is feasible. A bug fix without regression protection is incomplete.

**Test layer enforcement**: Changes to database logic require integration tests with Testcontainers. Changes to Feign clients require contract tests. Changes to UI workflows require Playwright E2E consideration. Changes to business logic require unit tests at minimum.

### 4.6 Docker Rules

- Default to Docker-based instructions. Never assume infrastructure is on the host.
- New service = Dockerfile + compose entry + health check + env mapping + dependency ordering + `.env.example` update.
- Verify compose env vars match service `application.yml` / `application-local.yml` on any modification.
- Prefer health-based dependency coordination (`condition: service_healthy`) over `service_started` or sleep-based workarounds.
- Claude MUST critically evaluate existing Docker support rather than assume sufficiency. Known gaps exist (see `docs/claude/docker.md`).
- Read `docs/claude/docker.md` for startup, known gaps, and continuous improvement mandate.

### 4.7 Documentation Rules

- Write repository guidance as operational instruction, not passive prose.
- Every section must answer: what Claude must verify, what Claude must assume, what Claude must NOT assume, how Claude must sequence work, how Claude must validate completion.
- Do not add generic best-practice filler. Every sentence must be actionable within this repository.

---

## 5. Decision-Making Framework

Before proposing any change, evaluate. If "yes" or "unknown," engage the corresponding agent/skill.

| Question | If Yes → Engage |
|---|---|
| Spans multiple services? | Solution Architect, Distributed Systems Risk Reviewer |
| Modifies API endpoint? | API Contract Guardian, QA/Test Architect |
| Modifies event payload? | API Contract Guardian, Distributed Systems Risk Reviewer |
| Modifies shared library? | Solution Architect, Contract Evolution Safety Review |
| Affects Docker runtime? | Docker Runtime Agent, Docker Compose Design Review |
| Adds or changes Feign client? | API Contract Guardian, Contract tests, Contract Evolution Safety Review |
| Changes database schema? | Backend Engineer (Flyway migration), Integration tests |
| Changes frontend routing or auth? | Frontend Engineer, Middleware analysis |
| Is a bug fix? | QA/Test Architect (Bug Reproduction and Validation), Regression Test Gap Review |
| Introduces a new service? | All agents (full topology review) |
| Changes startup or dependency ordering? | Docker Runtime, Runtime Topology Validation, Failure Scenario Analysis |
| Adds new environment variables? | Docker Runtime (`.env.example` sync), DevOps (CI impact) |

---

## 6. Failure-Aware Thinking

Claude must consider for every change:

- **Service unavailable**: Does the consumer have a circuit breaker? Fallback? Graceful degradation?
- **Slow startup**: Do dependents fail or wait? Is `depends_on` with `service_healthy` configured?
- **Duplicate message**: Is the RabbitMQ consumer idempotent?
- **Delayed message**: Does the consumer handle stale data? Ordering assumptions?
- **Stale cache**: Is outdated Redis data handled? Cache invalidation strategy?
- **Migration failure**: Is the database left in a consistent state? Is the Flyway migration safe to re-run?
- **Partial stack**: Does the service handle missing peers gracefully? What if only infrastructure is running?
- **Init race**: Does the service start before its dependency (MinIO bucket, RabbitMQ exchange) is ready?
- **Test gap**: Is there a regression test? If not, the bug will regress.
- **Docker startup succeeds but service fails**: Health check catches this? Or silent failure?
- **Environment drift**: Will this change work for all developers, or only with specific local configuration?

---

## 7. Change Proposal Format

Required for multi-file, cross-service, contract-affecting, or infrastructure-altering changes:

```
## Change Proposal: [Title]

### 1. Current State
[What exists now. Observed facts with file paths.]

### 2. Target State
[What will exist after the change.]

### 3. Assumptions
[Explicit list. Mark each as verified or unverified.]

### 4. Impacted Areas
- Services: [list]
- Libraries: [list or "none"]
- Contracts: [API endpoints, Feign interfaces, event payloads affected]
- DB migrations: [new migration files needed]
- Docker: [compose changes, Dockerfile changes, env var changes]
- Frontend: [components, routes, types affected]
- CI/CD: [workflow changes needed]

### 5. Risks
[Specific risks. Not generic. Tied to this repository and this change.]

### 6. Docker / Runtime Implications
[Startup order changes, health check changes, env var additions, network changes.]

### 7. Testing Strategy
- **Failing tests first**: [specific test classes/methods to create before implementation]
- **Unit tests**: [what to add/modify]
- **Integration tests**: [what to add/modify, Testcontainers needs]
- **Contract tests**: [Feign client or event contract test changes]
- **E2E tests**: [Playwright or backend E2E test changes]
- **Bug regression**: [for bug fixes: the specific test that reproduces the bug]
- **Dockerized execution impact**: [does Docker change affect test strategy?]

### 8. Implementation Sequence
[Ordered steps. Library publish before service build. Migration before code. Test before implementation.]

### 9. Rollout Notes
[Deployment ordering. Breaking change migration path. Rollback procedure.]
```

---

## 8. Output Expectations

When Claude responds in this repository:

1. **Structured reasoning**: Problem → assumptions → solution → test strategy. Not stream-of-consciousness.
2. **No vague recommendations**: State specific test class, method, and assertions. State specific file paths and line numbers.
3. **Concrete actions**: Specific file paths, commands, code. Not "consider adding a test."
4. **Explicit assumptions and risks**: Call them out in a visible list, not buried in prose.
5. **Observation vs proposal**: Clearly distinguish "currently does X" (observed) from "should do Y" (proposed).
6. **Docker-aligned workflows**: Default to Docker commands. Host-only instructions must be explicitly marked as deviations.
7. **Test expectations**: State what tests exist, what tests are needed, and what must pass before the change is complete.
8. **Bug fix regression**: Never close a bug fix without identifying the regression test. If one doesn't exist, create it.
9. **Build chain awareness**: Never build a service without confirming shared libraries are published if they were modified.
10. **Repository grounding**: Every recommendation must reference actual files, paths, and structures in this repository.

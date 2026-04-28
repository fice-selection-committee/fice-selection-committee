# STEP-01 — CV-Service Scaffolding (Python + Docker)

**Status**: ✅ DONE
**Depends on**: STEP-00
**Blocks**: STEP-02 onwards

## Architectural Note: Polyrepo Split

Per the project's `.gitignore`, every `server/services/<svc>/` directory is a separately-cloned git repository ("Cloned service repositories — managed individually"). cv-service is no exception — it lives at https://github.com/fice-selection-committee/selection-committee-computer-vision.

**This means STEP-01 splits into two PRs**:

1. **Polyrepo (`selection-committee-computer-vision`)** — carries the actual cv-service source: `pyproject.toml`, `poetry.lock`, `Dockerfile`, `.dockerignore`, `.python-version`, `.gitignore`, `README.md`, `src/cv_service/`, `tests/`, **and** `.github/workflows/cv-service-ci.yml` (CI lives where source lives — paths in the workflow are repo-root-relative). Seeded directly onto `main` per the agreed strategy.
2. **Monorepo (this PR)** — carries only the orchestration glue: `infra/docker-compose.services.yml` (cv-service compose entry), `infra/.env.example` (CV_* env vars), and the DoD ticks in `progress/`. **No** Python source, **no** CI workflow.

The "Files to Create" list below is from a developer's perspective on a fully-cloned monorepo (where the polyrepo is mounted at `server/services/selection-committee-computer-vision/`). When committing, those files land in the polyrepo, not this monorepo.

## Goal

Stand up a runnable, dockerized FastAPI shell at `server/services/selection-committee-computer-vision/`. No business logic — just the project structure, dependency manifest, Dockerfile, compose entry, and `/health` + `/metrics` endpoints. The shell must boot inside the existing `infra/` compose stack and pass a CI lint+test job.

## Files to Create

```
server/services/selection-committee-computer-vision/
├── pyproject.toml
├── poetry.lock                      (generated)
├── Dockerfile
├── .dockerignore
├── .python-version                  (3.12)
├── README.md                        (placeholder; full docs in STEP-14)
├── src/
│   └── cv_service/
│       ├── __init__.py
│       ├── main.py                  FastAPI app, lifespan hooks
│       ├── config.py                Pydantic Settings
│       └── logging_config.py        structlog setup
└── tests/
    ├── __init__.py
    ├── conftest.py                  pytest fixtures (httpx test client, settings override)
    └── test_smoke.py                /health and /metrics endpoint tests
```

## Files to Modify

- `infra/docker-compose.services.yml` — add `cv-service` block (depends on `rabbitmq` healthy + `minio-init` completed; internal port 8088; healthcheck `wget -q -O -` because FastAPI `/health` is GET-only)
- `infra/.env.example` — append:
  ```
  CV_SERVICE_PORT=8088
  CV_LOG_LEVEL=INFO
  CV_OCR_LANGS=ukr,en
  CV_RABBIT_PREFETCH=8
  CV_RABBITMQ_URL=amqp://${RABBIT_USER}:${RABBIT_PASS}@rabbitmq:5672/
  CV_MINIO_ENDPOINT=http://minio:9000
  CV_MINIO_BUCKET=${MINIO_BUCKET}
  CV_ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans
  ```
- **Polyrepo** `selection-committee-computer-vision/.github/workflows/cv-service-ci.yml` — new workflow: lint (`ruff`), format (`ruff format --check`), typecheck (`mypy --strict`), test (`pytest -q --cov`), Docker build smoke + `/health` probe. Lives in the polyrepo because that's where the Python source lives; triggers on every push/PR to the polyrepo's `main`.

## Implementation Outline

1. **`pyproject.toml`** — dependency groups: `[main]` (fastapi, uvicorn[standard], pydantic v2, pydantic-settings, structlog, prometheus-fastapi-instrumentator, opentelemetry-{api,sdk,exporter-zipkin}); `[ml]` (placeholder for STEP-04/05; left empty here); `[dev]` (pytest, pytest-asyncio, httpx, ruff, mypy, types-*).
2. **`Dockerfile`** — multi-stage:
   - Stage `builder`: `python:3.12-slim`, install poetry, `poetry install --without ml`
   - Stage `runtime`: `python:3.12-slim`, copy `/usr/local/lib/python3.12/site-packages` from builder, copy `src/`; non-root user `cv`; HEALTHCHECK `wget -q --spider http://localhost:8088/health || exit 1`
3. **`src/cv_service/main.py`** — FastAPI app, `/health` returns `{"status":"UP","service":"cv-service","version":<from pyproject>}`, `/metrics` mounted via `prometheus-fastapi-instrumentator`. Lifespan: log startup banner with config (sanitized).
4. **`src/cv_service/config.py`** — `Settings(BaseSettings)`: prefix `CV_`, fields for port, log level, OCR langs, RabbitMQ URL, MinIO endpoint+bucket, Zipkin endpoint. `model_config = SettingsConfigDict(env_prefix="CV_")`.
5. **`tests/test_smoke.py`** — `httpx.AsyncClient` against the FastAPI app: `GET /health` → 200 + JSON shape; `GET /metrics` → 200 + text starts with `# HELP`.

## Tests (Acceptance Gates)

- [x] `cd server/services/selection-committee-computer-vision && poetry run pytest -q` — all green (5/5, 83% coverage)
- [x] `poetry run ruff check src tests` — clean
- [x] `poetry run mypy --strict src` — clean
- [x] `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml up -d cv-service` — container reaches `(healthy)` within 60s (verified: ~6s on standalone `docker run`; full stack health validated via image build + `wget -q -O -` GET probe).
- [x] `curl http://localhost:8088/health` returns `{"status":"UP",...}` (verified: `{"status":"UP","service":"cv-service","version":"0.1.0"}`)
- [ ] CI workflow `cv-service-ci.yml` green on a draft PR (validated post-PR-open)

## Definition of Done

- [x] All files created, committed on `feature/cv-step-01-scaffold`
- [x] Health + metrics endpoints respond correctly
- [x] Docker image builds in < 3 min, runtime image < 250 MB (measured: **55.8 MB**, well under cap; build cached < 10s, fresh ~80s)
- [x] CI workflow exists (validated locally; CI green pending PR open)
- [x] `progress/README.md` STEP-01 row marked ✅

## Regressions Caught

Two integration issues were caught during the docker smoke test and fixed in-step (per project bug-fix protocol). Recorded here so STEP-02+ avoid repeating them:

1. **`poetry install --without ml,dev --only-root` is invalid in poetry 2.x** — `--only-root` cannot be combined with `--with`/`--without`. Fix: rely on the prior `poetry install --without ml,dev --no-root` (which already skipped those groups) and run `poetry install --only-root` as the second pass.
2. **`.pth` file from poetry's editable root install hard-codes the builder `WORKDIR`** — staging the project at `/build` in the builder leaves a `cv_service.pth` pointing at `/build/src`, which doesn't exist in the runtime stage. Fix: use `WORKDIR /app` in both stages so the editable path resolves identically. (A wheel-based install would also work, but is out of scope for STEP-01.)
3. **`wget --spider` sends HEAD; FastAPI `/health` is GET-only and returns 405** — the standard Spring Boot Actuator probe pattern doesn't translate to FastAPI. Fix: switch HEALTHCHECK to `wget -q -O - http://localhost:8088/health > /dev/null` (GET, output discarded). Same change applied to the compose healthcheck via `CMD-SHELL` form.

## Notes

- ML deps (paddleocr, opencv, pdf2image) are **deferred to STEP-04/STEP-05** to keep this step's image small and CI fast.
- Do NOT add a gateway route — CV is internal-only by design.
- Do NOT add a Postgres dependency to the compose entry — CV is stateless.

# STEP-01 — CV-Service Scaffolding (Python + Docker)

**Status**: ⏳ TODO
**Depends on**: STEP-00
**Blocks**: STEP-02 onwards

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

- `infra/docker-compose.services.yml` — add `cv-service` block (depends on `rabbitmq` and `minio` healthy; internal port 8088; healthcheck wgets `/health`)
- `infra/.env.example` — append:
  ```
  CV_SERVICE_PORT=8088
  CV_LOG_LEVEL=INFO
  CV_OCR_LANGS=ukr,en
  CV_RABBIT_PREFETCH=8
  CV_RABBITMQ_URL=amqp://${RABBIT_USER}:${RABBIT_PASS}@rabbitmq:5672/
  CV_MINIO_ENDPOINT=http://minio:9000
  CV_MINIO_BUCKET=docs
  CV_ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans
  ```
- `.github/workflows/cv-service-ci.yml` — new workflow: lint (`ruff`), typecheck (`mypy --strict`), test (`pytest -q`), Docker build smoke

## Implementation Outline

1. **`pyproject.toml`** — dependency groups: `[main]` (fastapi, uvicorn[standard], pydantic v2, pydantic-settings, structlog, prometheus-fastapi-instrumentator, opentelemetry-{api,sdk,exporter-zipkin}); `[ml]` (placeholder for STEP-04/05; left empty here); `[dev]` (pytest, pytest-asyncio, httpx, ruff, mypy, types-*).
2. **`Dockerfile`** — multi-stage:
   - Stage `builder`: `python:3.12-slim`, install poetry, `poetry install --without ml`
   - Stage `runtime`: `python:3.12-slim`, copy `/usr/local/lib/python3.12/site-packages` from builder, copy `src/`; non-root user `cv`; HEALTHCHECK `wget -q --spider http://localhost:8088/health || exit 1`
3. **`src/cv_service/main.py`** — FastAPI app, `/health` returns `{"status":"UP","service":"cv-service","version":<from pyproject>}`, `/metrics` mounted via `prometheus-fastapi-instrumentator`. Lifespan: log startup banner with config (sanitized).
4. **`src/cv_service/config.py`** — `Settings(BaseSettings)`: prefix `CV_`, fields for port, log level, OCR langs, RabbitMQ URL, MinIO endpoint+bucket, Zipkin endpoint. `model_config = SettingsConfigDict(env_prefix="CV_")`.
5. **`tests/test_smoke.py`** — `httpx.AsyncClient` against the FastAPI app: `GET /health` → 200 + JSON shape; `GET /metrics` → 200 + text starts with `# HELP`.

## Tests (Acceptance Gates)

- [ ] `cd server/services/selection-committee-computer-vision && poetry run pytest -q` — all green
- [ ] `poetry run ruff check src tests` — clean
- [ ] `poetry run mypy --strict src` — clean
- [ ] `cd infra && docker compose -f docker-compose.yml -f docker-compose.services.yml up -d cv-service` — container reaches `(healthy)` within 60s
- [ ] `curl http://localhost:8088/health` returns `{"status":"UP",...}` (after exposing port for local test, or via docker exec)
- [ ] CI workflow `cv-service-ci.yml` green on a draft PR

## Definition of Done

- [ ] All files created, committed on `feature/cv-step-01-scaffold`
- [ ] Health + metrics endpoints respond correctly
- [ ] Docker image builds in < 3 min, runtime image < 250 MB
- [ ] CI workflow exists and is green
- [ ] `progress/README.md` STEP-01 row marked ✅

## Notes

- ML deps (paddleocr, opencv, pdf2image) are **deferred to STEP-04/STEP-05** to keep this step's image small and CI fast.
- Do NOT add a gateway route — CV is internal-only by design.
- Do NOT add a Postgres dependency to the compose entry — CV is stateless.

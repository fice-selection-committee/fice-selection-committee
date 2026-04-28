# STEP-14 — Documentation

**Status**: ⏳ TODO
**Depends on**: STEP-13
**Blocks**: nothing (closes the project)

## Goal

Make the CV system maintainable. Every future Claude session and every new contributor must be able to:
1. Understand what the system does (architecture).
2. Know what changed vs the original FLOW-07 spec ("as-implemented" deltas).
3. Run / debug / extend the service confidently.
4. Operate it in production (runbook).

## Files to Create

```
server/services/selection-committee-computer-vision/
├── CLAUDE.md                          auto-loaded — operational rules + run commands
└── README.md                          public-facing — overview, prereqs, dev quickstart
```

```
docs/
├── claude/
│   └── architecture.md                 MODIFY — add CV-Service node to topology diagram
├── flows/
│   └── FLOW-07_OCR-CV pipeline через RabbitMQ.md   MODIFY — add "As-Implemented" section
├── use-cases/
│   └── UC-02_Завантаження_документів.md             MODIFY — link to live OCR endpoint
└── runbooks/
    └── cv-service.md                  NEW — DLQ inspection, retry, troubleshooting
```

## Files to Modify (Cross-cutting)

- `server/CLAUDE.md` — add CV-Service to the service inventory table
- `infra/CLAUDE.md` — add CV-Service compose section to the runtime topology
- `client/web/CLAUDE.md` — add OCR auto-fill UX rule
- Top-level `CLAUDE.md` — add CV-Service to the auto-loaded subdirectory rules table

## Implementation Outline

### `selection-committee-computer-vision/CLAUDE.md` (auto-loaded)
Sections:
- **Stack**: Python 3.12, FastAPI, PaddleOCR, aio-pika, minio-py
- **Run locally**: `poetry install`, `poetry run uvicorn cv_service.main:app --port 8088`
- **Run dockerized**: `docker compose -f infra/docker-compose.services.yml up cv-service`
- **Tests**: `poetry run pytest -q`; integration `pytest -m integration`; benches `pytest -m bench`
- **Lint/type**: `ruff check`, `mypy --strict src`
- **Architectural rules** (binding):
  - Stateless. No DB. No persistence in this service.
  - Internal-only. No gateway route. No public endpoints other than `/health` and `/metrics`.
  - Idempotency: dedup is per-instance LRU; multi-instance dedup MUST migrate to Redis if scale demands.
  - Models pre-baked in Docker image. Cold-start downloads are forbidden in prod.
  - PaddleOCR is NOT thread-safe — recognize() is serialized via asyncio.Lock.
- **Adding a new field extractor**: subclass `FieldExtractor`, add to `EXTRACTORS` dict in `router.py`, add fixture + tests.
- **Updating PaddleOCR models**: bump `pyproject.toml`, rebake Docker image, validate against full test suite + 50-doc fixture set.
- **Ukrainian-OCR caveats**:
  - PaddleOCR `lang="uk"` model handles Cyrillic + Latin natively.
  - Hybrid Cyrillic/Latin docs (e.g., MRZ on Ukrainian passports) — ensure orientation cls is enabled.
  - Numbers vs letters confusion (О vs 0, З vs 3) — handle in extractor regex, not at OCR layer.

### `selection-committee-computer-vision/README.md`
Concise public-facing:
- One-line: "Async OCR worker for FICE Selection Committee. Consumes `cv.document.requested`, publishes `cv.document.parsed`/`cv.document.failed`."
- Link to `CLAUDE.md` for engineering rules.
- Link to FLOW-07 for the spec.
- 5-line dev quickstart (poetry install → run).

### `docs/runbooks/cv-service.md`
Operator-facing how-tos:
- **Inspect DLQ**:
  ```bash
  rabbitmqadmin -H localhost -P 15672 -u $RABBIT_USER -p $RABBIT_PASS get queue=cv.dlq count=10
  ```
- **Retry a stuck document** (republish from DLQ):
  ```bash
  rabbitmqadmin publish exchange=cv.events routing_key=cv.document.requested payload=<json>
  ```
- **Troubleshoot "OCR Pending forever"**:
  1. Check `cv-service` health: `curl localhost:8088/health`
  2. Check queue depth: `rabbitmq UI → cv.document.requested`
  3. Check breaker state metric in Grafana dashboard
  4. Inspect logs: `docker compose logs cv-service --tail 200 | grep traceId=<xxx>`
  5. Inspect Zipkin trace by `traceId`
- **Scale horizontally**: `docker compose up -d --scale cv-service=3` (each instance has independent in-memory dedup; this is acceptable for current target)
- **Refresh OCR models**: rebuild image with new model versions; rolling restart.
- **Manual re-OCR for a document**: documents-service operator endpoint (admin-only, deferred — for now via direct AMQP republish)

### `docs/claude/architecture.md` modifications
Add CV-Service to the topology diagram (Mermaid). Add a row to the service inventory table:
```
| selection-committee-computer-vision | Python/FastAPI | 8088 (internal) | Async OCR worker | RabbitMQ cv.events, MinIO |
```

### `docs/flows/FLOW-07_*.md` "As-Implemented" addendum
- Section: "Implementation as of v1.4.0 (CV-Service initial release)"
- Note any deviations from the original spec (e.g., dedup is per-instance, not global; one engine instance per process).

### `docs/use-cases/UC-02_*.md` modifications
- Add a line under "Main Flow" step 4: *"OCR result is queryable at `GET /api/v1/documents/{id}/ocr`. The applicant UI surfaces extracted fields via the `OcrResultCard` component once status flips to `PARSED`."*

### Top-level `CLAUDE.md` table addition
| `server/services/selection-committee-computer-vision/CLAUDE.md` | Touching CV-Service files | Stack, run commands, architectural rules |

## Tests (Acceptance Gates)

- [ ] **Markdown lint**: `markdownlint progress/ docs/runbooks/cv-service.md server/services/selection-committee-computer-vision/{README,CLAUDE}.md` — no errors.
- [ ] **Cross-link check**: every `[link](path)` in modified docs resolves to an existing file (script: `find docs -name '*.md' -print0 | xargs -0 grep -oE '\[.*\]\([^)]+\)' | check_links.sh`).
- [ ] **Mermaid validity**: `npx -y @mermaid-js/mermaid-cli -i docs/claude/architecture.md --validate` (or equivalent) — diagrams parse.
- [ ] **CLAUDE.md auto-load smoke**: open a CV-Service file in a fresh Claude session — confirm the rules from the new `CLAUDE.md` are surfaced.
- [ ] **Runbook drill** (manual): an engineer who has not seen the system follows the runbook to inspect DLQ and retry a document — succeeds without asking questions.

## Definition of Done

- [ ] All new docs created
- [ ] All cross-cutting CLAUDE.md files updated
- [ ] Markdown lint + link check pass
- [ ] Mermaid diagrams render
- [ ] Runbook drilled by a second engineer (or simulated)
- [ ] `progress/README.md` STEP-14 row marked ✅
- [ ] **Final**: update `progress/README.md` status table to all ✅; close the implementation.

## Notes

- Keep `CLAUDE.md` operational, not narrative. Rules → run commands → constraints. No prose.
- Keep `README.md` short. If you find yourself writing more than 80 lines, move detail into `CLAUDE.md` or runbook.
- Runbook covers ONLY operational tasks the on-call would face. Keep architecture out of it (lives in `architecture.md`).
- After STEP-14 closes, the system is shippable. Bump `server/version.properties` to `1.4.0` in the same PR that closes this step.

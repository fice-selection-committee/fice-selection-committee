# cv-service Runbook (FLOW-07 / UC-02)

Operational tasks the on-call engineer faces. Architecture lives in
`docs/claude/architecture.md` + `docs/flows/FLOW-07_*.md`; engineering rules
live in `server/services/selection-committee-computer-vision/CLAUDE.md` (auto-loaded by Claude Code when touching cv-service files).

---

## Health checks

| Check | How |
|---|---|
| Liveness | `curl -fsS http://localhost:8088/health` should return `200`. |
| Prometheus scrape | `curl -fsS http://localhost:8088/metrics \| grep cv_documents_processed_total` returns the counter. |
| Stack-up state | `docker compose -f infra/docker-compose.yml -f infra/docker-compose.services.yml ps cv-service` shows `(healthy)`. |
| Dashboard | Grafana → `sc-cv-service` (8 panels: throughput / cumulative / stage latency p50/p95/p99 / confidence heatmap / failure-rate-by-reason / breaker state / queue depth / DLQ depth). |

---

## "OCR is stuck on PENDING for document X"

Triage in this order:

1. **Frontend has flipped to UNAVAILABLE after 60s** — that's the soft-fail path, not an outage. The polling hook stops; cv-service may still process the document later. Move to step 2 to confirm.
2. **Check `cv.document.requested` queue depth**:
   ```bash
   rabbitmqadmin --username=$RABBIT_USER --password=$RABBIT_PASS list queues name messages
   ```
   If depth is rising, cv-service consumer is blocked or slow. Continue to step 3.
3. **Check the OcrBreaker state**:
   ```promql
   cv_ocr_breaker_state{instance="cv-service:8088"}
   ```
   `1` = OPEN (breaker tripped after 5 OCR failures, won't accept new work for 30s). `0` = CLOSED. `2` = HALF_OPEN.
4. **Inspect the document's traceId**:
   ```bash
   docker compose -f infra/docker-compose.yml -f infra/docker-compose.services.yml \
     logs cv-service --tail 500 | grep "documentId=$DOC_ID"
   ```
   The structlog JSON includes `traceId` — feed it to Zipkin (`http://localhost:9411/zipkin/?lookback=86400000&serviceName=cv-service`) for the full pipeline trace.
5. **Inspect the DLQ**:
   ```bash
   rabbitmqadmin --username=$RABBIT_USER --password=$RABBIT_PASS \
     get queue=cv.dlq count=10
   ```
   If the document is in the DLQ, the failure is non-retriable — see "Manual re-OCR" below.

---

## Manual re-OCR for a single document

There is no admin REST endpoint yet (deferred). For now, republish via `rabbitmqadmin`:

```bash
# 1. Build the JSON payload — the wire shape is documented in
#    server/libs/sc-event-contracts CvDocumentRequestedEvent.
PAYLOAD=$(jq -nc --arg s "documents/2026/passport-12345.pdf" --arg t "passport" \
  '{documentId: 12345, s3Key: $s, documentType: $t, traceId: "manual-rerun-001"}')

# 2. Publish onto cv.events with the requested routing key.
rabbitmqadmin --username=$RABBIT_USER --password=$RABBIT_PASS \
  publish exchange=cv.events routing_key=cv.document.requested payload="$PAYLOAD"

# 3. Watch cv-service logs for the same traceId.
docker compose -f infra/docker-compose.yml -f infra/docker-compose.services.yml \
  logs -f cv-service | grep "manual-rerun-001"
```

The cv-service idempotency cache is keyed on `(documentId, s3Key)` and is per-instance LRU. Republishing the *same* `(documentId, s3Key)` pair within ~10k delivery window will be silently deduped. To force a re-OCR, change the `traceId` and pick a fresh document version (or restart the cv-service container, which clears the in-memory cache).

> **Important**: `rabbitmqadmin` requires the explicit `--username=` and `--password=` flags, **not** the short `-u` / `-p` aliases. STEP-02 regression.

---

## Inspect / drain the DLQ

```bash
# Read up to 10 dead-lettered messages without acking.
rabbitmqadmin --username=$RABBIT_USER --password=$RABBIT_PASS \
  get queue=cv.dlq count=10

# Permanently drop the DLQ contents (use only when triaged).
rabbitmqadmin --username=$RABBIT_USER --password=$RABBIT_PASS \
  purge queue cv.dlq
```

The DLQ should be empty in steady state on synthetic fixtures (`cv_dlq_routed_total == 0`). A non-zero counter signals either a malformed payload (logged + dropped at the consumer level) or a terminal extraction error (`UnsupportedDocumentTypeError` / `ExtractionError`) — the message in the DLQ has the original payload + the failure reason in the `x-cv-failure-reason` header.

---

## Scale horizontally

```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.services.yml \
  up -d --scale cv-service=3
```

Each instance has independent in-memory dedup. Acceptable until measured duplicate-publish rate justifies the Redis migration documented in `cv-service/CLAUDE.md`. The competing-consumers pattern on `cv.document.requested` distributes work automatically.

---

## Refresh OCR models

1. In `server/services/selection-committee-computer-vision/pyproject.toml`, bump `paddleocr` and/or `paddlepaddle` in the `ml` group. **Keep `numpy < 2.0`** — paddle's C extensions are built against the numpy 1.x ABI; bumping numpy to 2.x triggers `module compiled against API version 0x... but this version of numpy is 0x...` at import time.
2. `cd selection-committee-computer-vision && poetry lock && poetry install --with ml`.
3. Run the full pytest suite — pay attention to `tests/ocr/` Cyrillic fixtures.
4. Re-bake the Docker image: `cd infra && docker compose -f docker-compose.services.yml build cv-service`.
5. Run the k6 load suite (`load/cv-pipeline.js` in the e2e-tests polyrepo) to confirm SLO still holds.
6. Rolling restart: `docker compose -f infra/docker-compose.yml -f infra/docker-compose.services.yml up -d cv-service`.

---

## Common pitfalls (from past regressions)

* **`wget --spider` returns 405** against the FastAPI health endpoint — use `wget -q -O - http://localhost:8088/health > /dev/null` (GET, output discarded) in Compose health checks. STEP-01 regression.
* **Poetry 2.x quirks** — `--only-root` cannot combine with `--with`/`--without`. Use a two-pass install when needed. Editable-install `.pth` files hard-code WORKDIR; keep `WORKDIR /app` consistent across multi-stage builds.
* **`infra/rabbitmq/definitions.json` is gitignored** — the tracked source-of-truth is `definitions.example.json` in the infra polyrepo.
* **mypy + un-stubbed packages** — `pyproject.toml` overrides exist for `minio.*`, `testcontainers.*`, `urllib3.*`, `paddleocr.*`, `paddle.*`, `paddlepaddle.*`, `aio_pika.*`, `aiormq.*`, `cachetools.*`. Add new entries only when a new third-party import lacks stubs.
* **Pytest filterwarnings quarantine list** — paddle / paddleocr / ppocr / google.protobuf / astor / testcontainers DeprecationWarning instances are silenced. Any new transitive that throws Python-3.12-deprecation noise during `cv_service.*` import-chain needs an additive entry.
* **OTel `_TRACER_PROVIDER_SET_ONCE._done` reset** is the standard escape hatch for swapping providers between test cases (STEP-09 regression).

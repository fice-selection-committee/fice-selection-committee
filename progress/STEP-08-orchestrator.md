# STEP-08 — Orchestrator & Resilience

**Status**: ⏳ TODO
**Depends on**: STEP-03, STEP-04, STEP-05, STEP-06, STEP-07
**Blocks**: STEP-09, STEP-10

## Goal

Glue the pipeline together: receive a `CvDocumentRequestedEvent` → download from MinIO → preprocess → OCR each page → run the right field extractor → publish `CvDocumentParsedEvent` (or `CvDocumentFailedEvent` on failure). Implements the FLOW-17 retry policy (5s/30s/5m → DLQ on attempt 4) and a circuit breaker around the OCR engine.

## Files to Create

```
server/services/selection-committee-computer-vision/src/cv_service/orchestrator/
├── __init__.py
├── pipeline.py              run_pipeline(event, ctx) — the end-to-end function
├── retry_policy.py          retry-header logic (RabbitMQ x-retry-count + delay queues)
└── exceptions.py            TransientError, TerminalError taxonomy

src/cv_service/resilience/
├── __init__.py
├── breaker.py               pybreaker wrapper around OcrEngine.recognize
└── error_classification.py  classify(exception) → "transient" | "terminal"
```

```
tests/orchestrator/
├── __init__.py
├── conftest.py              orchestrator with mocked components fixture
├── test_pipeline_happy.py
├── test_pipeline_retries.py
├── test_pipeline_dlq.py
├── test_breaker.py
└── test_error_classification.py
```

`pyproject.toml`: add `pybreaker ^1.2`, `tenacity ^8.5` (optional, mainly for clarity).

## Implementation Outline

### `error_classification.py`
```python
TRANSIENT = (StorageTransientError, OcrEngineError, ConnectionError, TimeoutError, asyncio.TimeoutError)
TERMINAL = (ObjectNotFoundError, OcrInputError, PreprocessingError, UnsupportedDocumentTypeError, UnsupportedFormatError, ValidationError)

def classify(exc: BaseException) -> Literal["transient", "terminal"]:
    if isinstance(exc, TERMINAL): return "terminal"
    if isinstance(exc, TRANSIENT): return "transient"
    return "transient"   # default to retriable; better to retry than drop
```

### `breaker.py`
- `pybreaker.CircuitBreaker(fail_max=5, reset_timeout=30, exclude=[OcrInputError])`
- Wraps `OcrEngine.recognize`; on open state → raises `BreakerOpenError` (subclass of `TransientError`).
- Metric: `cv_ocr_breaker_state{state}` gauge.

### `retry_policy.py`
- Reads `x-retry-count` header (default 0).
- Delay schedule: `[5, 30, 300]` seconds; routes via dead-letter to `cv.retry.5s` / `cv.retry.30s` / `cv.retry.5m` (TTL queues that DLX back to `cv.events`).
- On retry-count ≥ 3 → publish `CvDocumentFailedEvent{retriable=false}` AND nack-no-requeue → message lands in `cv.dlq`.
- For terminal errors: skip retry entirely, publish `failed{retriable=false}`, nack → DLQ.

### `pipeline.py`
```python
async def run_pipeline(
    event: CvDocumentRequestedEvent,
    ctx: MessageContext,
    *, storage, preprocessor, ocr_engine, publisher, retry_policy
) -> None:
    span = tracer.start_span("cv.pipeline", attributes={"documentId": event.documentId})
    try:
        with timed("download"):
            obj = await storage.download(event.s3Key)
        with timed("preprocess"):
            pages = preprocessor.run(obj.path)
        ocr_results = []
        with timed("ocr"):
            for i, page in enumerate(pages):
                ocr_results.append(await breaker.call(ocr_engine.recognize, page.image, i))
        with timed("extract"):
            extraction = router.extract(event.documentType, merge(ocr_results))
        with timed("publish"):
            await publisher.publish_parsed(CvDocumentParsedEvent(
                documentId=event.documentId,
                fields=extraction.fields,
                confidence=extraction.confidence,
                traceId=event.traceId,
            ))
    except Exception as e:
        kind = classify(e)
        if kind == "terminal":
            await publisher.publish_failed(CvDocumentFailedEvent(
                documentId=event.documentId, error=str(e), retriable=False, traceId=event.traceId
            ))
            await retry_policy.dead_letter(ctx)
        else:  # transient
            if retry_policy.exhausted(ctx):
                await publisher.publish_failed(CvDocumentFailedEvent(
                    documentId=event.documentId, error=str(e), retriable=True, traceId=event.traceId
                ))
                await retry_policy.dead_letter(ctx)
            else:
                await retry_policy.schedule_retry(ctx)
        raise   # let consumer's nack logic run
    finally:
        span.end()
```

## Tests (Acceptance Gates)

### `test_pipeline_happy.py`
- [ ] Mock storage returns a real fixture passport. Mock OCR returns predetermined tokens. Run pipeline → assert `publish_parsed` called once with expected fields and confidence > 0.6.

### `test_pipeline_retries.py`
- [ ] Inject `StorageTransientError` on first 2 attempts, succeed on 3rd. Drive pipeline manually with x-retry-count=0,1,2. Assert: 1st and 2nd → `schedule_retry` called; 3rd → `publish_parsed` called.
- [ ] Inject `OcrEngineError` always. After 3 retries → `publish_failed{retriable=true}` + dead-letter.

### `test_pipeline_dlq.py`
- [ ] Inject `PreprocessingError` (terminal). Assert: 1st attempt → `publish_failed{retriable=false}` + dead-letter. No retries.
- [ ] Inject `UnsupportedDocumentTypeError`. Same.
- [ ] Inject `ObjectNotFoundError`. Same.

### `test_breaker.py`
- [ ] 5 consecutive `OcrEngineError` → breaker opens → 6th call raises `BreakerOpenError` without invoking OCR.
- [ ] After `reset_timeout`, half-open → 1 success closes breaker.
- [ ] `OcrInputError` (terminal) does NOT count toward breaker fail count.

### `test_error_classification.py`
- [ ] Parametrize all error types → `classify` returns expected category.
- [ ] Unknown `RuntimeError` → defaults to "transient" (logged as warning).

## Definition of Done

- [ ] All orchestrator + resilience files implemented
- [ ] All ~15 tests pass
- [ ] `ruff` + `mypy --strict` clean
- [ ] End-to-end smoke (manual): publish a real event → observe parsed event published within 10s
- [ ] `progress/README.md` STEP-08 row marked ✅

## Notes

- `with timed("stage")` is a context manager in STEP-09's metrics module; for now it's a no-op stub.
- The retry queues (`cv.retry.5s`, etc.) must already exist from STEP-02's `definitions.json`. If missing, `topology.py` startup check from STEP-07 will fail fast.
- The OCR breaker is intentionally narrow (only OCR engine call). It does NOT wrap MinIO downloads — those have their own retriable/terminal classification.

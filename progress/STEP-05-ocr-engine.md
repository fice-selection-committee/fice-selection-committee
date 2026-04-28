# STEP-05 — OCR Engine (PaddleOCR)

**Status**: ⏳ TODO
**Depends on**: STEP-04
**Blocks**: STEP-06

## Goal

Wrap PaddleOCR with Ukrainian + English language models. Expose token-level results with bounding boxes and confidence. Engine is loaded once at process startup (model loading is ~5s); calls are async via `asyncio.to_thread` so the FastAPI event loop stays responsive.

## Files to Create

```
server/services/selection-committee-computer-vision/src/cv_service/ocr/
├── __init__.py
├── engine.py            OcrEngine singleton + recognize()
├── models.py            OcrToken, OcrResult Pydantic models
└── exceptions.py        OcrEngineError (transient), OcrInputError (terminal)
```

```
tests/ocr/
├── __init__.py
├── conftest.py          shared engine fixture, synthetic image generator
├── fixtures/            (subset of STEP-04 fixtures + synthetic)
└── test_engine.py
```

Dependencies in `pyproject.toml` `[ml]` group: `paddlepaddle==2.6.*`, `paddleocr==2.7.*`, `numpy<2.0`. Dockerfile: model files must be cached at build time so cold-start doesn't download (run a tiny script during image build that triggers PaddleOCR's lazy download into `/home/cv/.paddleocr/`).

## Implementation Outline

1. **`models.py`**:
   ```python
   class OcrToken(BaseModel):
       text: str
       bbox: tuple[tuple[float, float], ...]   # 4 corner points
       confidence: float                        # 0.0 — 1.0

   class OcrResult(BaseModel):
       tokens: list[OcrToken]
       mean_confidence: float
       page_index: int
   ```
2. **`engine.py`** `OcrEngine`:
   - `__init__(langs: list[str] = ["uk","en"])` — instantiate `PaddleOCR(use_angle_cls=True, lang="uk")` once. Note: PaddleOCR can only handle one primary lang per instance — for `ukr+en` use `lang="uk"` (the Ukrainian model includes Cyrillic + Latin) OR maintain two engines and merge results. **Decision**: single engine `lang="uk"`, fallback `lang="en"` on low-confidence pages.
   - `async def recognize(image: np.ndarray, page_index: int = 0) -> OcrResult`:
     - Wraps `await asyncio.to_thread(self._engine.ocr, image, cls=True)`
     - Maps Paddle output `[[bbox, (text, confidence)], ...]` → `list[OcrToken]`
     - Computes `mean_confidence`
     - On low mean_confidence (< 0.4), retry with `lang="en"` engine and pick the higher-confidence result
     - Wraps Paddle exceptions: `RuntimeError` → `OcrEngineError(retriable=True)`; bad input shape → `OcrInputError(retriable=False)`
3. **Singleton pattern**: instantiate in FastAPI lifespan startup, store on `app.state.ocr_engine`. Tests use a session-scoped fixture.

## Tests (Acceptance Gates)

- [ ] **Synthetic Ukrainian text**: render "ТЕСТ 1234567890 КИЇВ" with PIL onto white 800x200 canvas. Run engine. Assert: at least 3 tokens; concatenated text contains "ТЕСТ" and "1234567890" and "КИЇВ"; mean confidence > 0.85.
- [ ] **Synthetic English**: "PASSPORT NUMBER FA123456" → tokens contain those substrings; confidence > 0.85.
- [ ] **Real fixture — passport**: run on `tests/preprocessing/fixtures/inputs/passport_clean.png` (already preprocessed in test) → ≥ 12 tokens; tokens collectively contain at least one of {"ПАСПОРТ", "PASSPORT"}.
- [ ] **Empty image**: white blank → returns `OcrResult` with empty tokens list, `mean_confidence == 0.0`. NOT an error.
- [ ] **Wrong dtype**: pass `image.astype(np.float64)` → raises `OcrInputError`.
- [ ] **Performance** (marked `@pytest.mark.slow`, skip on CI by default): single 300dpi page < 2.5s on the runner; 10 sequential calls average < 2s/call (singleton is reused).
- [ ] **Concurrency**: 4 concurrent `recognize()` via `asyncio.gather` → all complete, no exception. Internal lock OK (PaddleOCR is not thread-safe; serialize via lock).

## Definition of Done

- [ ] OCR engine wraps PaddleOCR cleanly
- [ ] Models pre-baked into Docker image (verified: image size grows by ~200MB, but no network call on container start)
- [ ] All 7 tests pass
- [ ] `ruff` + `mypy --strict` clean
- [ ] `progress/README.md` STEP-05 row marked ✅

## Notes

- **PaddleOCR vs Tesseract**: PaddleOCR has better Cyrillic accuracy on degraded scans (project requirement). Tesseract `ukr` traineddata is acceptable but PaddleOCR ships with a dedicated Ukrainian model. We chose PaddleOCR per user decision.
- **Model size**: `ch_PP-OCRv4_det` (~50MB) + `cyrillic_PP-OCRv3_rec` (~10MB) + `cls` (~2MB) ≈ 65MB. CPU-only inference; no GPU dependency.
- **Cold start**: model load takes ~5s. Lifespan startup blocks until model loaded (`/health` returns 503 during this window). Compose `start_period: 30s` in healthcheck accommodates this.
- **Thread safety**: PaddleOCR uses Paddle's runtime which is NOT thread-safe. Wrap calls in an `asyncio.Lock`.

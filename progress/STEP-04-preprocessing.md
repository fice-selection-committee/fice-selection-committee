# STEP-04 — Image Preprocessing Pipeline

**Status**: ⏳ TODO
**Depends on**: STEP-03
**Blocks**: STEP-05

## Goal

Convert raw uploads (JPEG/PNG/PDF/TIFF) into normalized images ready for OCR: grayscale, deskewed, denoised, resized to ~300 DPI. Multi-page PDFs become a list of preprocessed pages. The pipeline must be deterministic on golden fixtures.

## Files to Create

```
server/services/selection-committee-computer-vision/src/cv_service/preprocessing/
├── __init__.py
├── loader.py            file-type sniff (magic bytes) → list[PIL.Image.Image]
├── pipeline.py          PreprocessingPipeline class with the OpenCV transforms
├── exceptions.py        PreprocessingError, UnsupportedFormatError
└── models.py            PreprocessedImage(image: np.ndarray, page_index: int, dpi: int)
```

```
tests/preprocessing/
├── __init__.py
├── conftest.py          fixture loaders for golden inputs/outputs
├── fixtures/
│   ├── inputs/
│   │   ├── passport_clean.png
│   │   ├── passport_skewed_7deg.png
│   │   ├── ipn_scan.pdf
│   │   ├── multipage.pdf            (3 pages)
│   │   ├── corrupt.bin
│   │   └── photo.jpg                (random non-document)
│   └── golden/
│       └── passport_clean_preprocessed.png  (committed expected output)
├── test_loader.py
└── test_pipeline.py
```

Add to `pyproject.toml` `[ml]` group: `opencv-python-headless`, `pdf2image`, `Pillow`, `python-magic`. Dockerfile must install `poppler-utils` (for pdf2image) and `libmagic1`.

## Implementation Outline

1. **`loader.py`**:
   - `load(path: Path) -> list[PIL.Image.Image]`
   - Detect MIME via `python-magic` (more reliable than extension).
   - Branch:
     - JPEG/PNG/TIFF → `PIL.Image.open(path).convert("RGB")` → single-element list
     - PDF → `pdf2image.convert_from_path(path, dpi=300)` → list of PIL images
     - Anything else → raise `UnsupportedFormatError`
   - Validation: max page count = 10 (env `CV_MAX_PAGES`); max image dimension = 8000px.
2. **`pipeline.py`** `PreprocessingPipeline.run(image: PIL.Image) -> PreprocessedImage`:
   - Convert PIL → `np.ndarray` (BGR for OpenCV)
   - **Step 1 — Grayscale**: `cv2.cvtColor(BGR2GRAY)`
   - **Step 2 — Deskew**: detect skew via Hough lines; rotate with `cv2.warpAffine` and bilinear interpolation. Skew tolerance ±15°; clamp.
   - **Step 3 — Denoise**: `cv2.fastNlMeansDenoising(h=10)`
   - **Step 4 — Adaptive threshold**: `cv2.adaptiveThreshold(... blockSize=31, C=10)` for binarization
   - **Step 5 — DPI normalization**: target 300 DPI. Compute scale from current DPI (read from PIL `info.dpi` if present, else assume 200). Resize via `cv2.resize` with `INTER_CUBIC`.
   - Return `PreprocessedImage(image=np.array, page_index, dpi=300)`.
3. **Determinism**: fix random seeds (none used, but document); `cv2.setNumThreads(1)` during tests for reproducibility.

## Tests (Acceptance Gates)

- [ ] **Loader — JPEG**: load `photo.jpg` → returns 1-element list of PIL.Image.
- [ ] **Loader — PDF single page**: `ipn_scan.pdf` → 1-element list.
- [ ] **Loader — PDF multi-page**: `multipage.pdf` → 3 elements, ordered.
- [ ] **Loader — corrupt**: `corrupt.bin` → raises `UnsupportedFormatError`.
- [ ] **Loader — too many pages**: synthesize 11-page PDF → raises `PreprocessingError`.
- [ ] **Pipeline — golden fixture**: run on `passport_clean.png` → output pixel-diff vs `golden/passport_clean_preprocessed.png` < 0.5% (use `numpy.mean(np.abs(a-b))/255 < 0.005`).
- [ ] **Pipeline — deskew**: input is `passport_clean.png` rotated 7° via PIL. Run pipeline. Compute output skew via Hough; assert `|skew| < 0.5°`.
- [ ] **Pipeline — DPI normalization**: 150-DPI input → output DPI metadata is 300; height ratio matches expected scale.
- [ ] **Pipeline — determinism**: run twice on same input → byte-equal arrays.
- [ ] **Pipeline — large image guard**: 9000×9000 → raises `PreprocessingError` (exceeds dim limit).

## Definition of Done

- [ ] All files created including golden fixtures
- [ ] All 10 tests pass
- [ ] `ruff` + `mypy --strict` clean
- [ ] Docker image builds with new system deps (`poppler-utils`, `libmagic1`)
- [ ] `progress/README.md` STEP-04 row marked ✅

## Notes

- Do NOT use `numpy.random` or `cv2`'s non-deterministic ML transforms (no `cv2.dnn`).
- Do NOT do per-region OCR-aware preprocessing here — that's STEP-05's job.
- Do NOT touch the original file on disk; pipeline operates on copies in memory.

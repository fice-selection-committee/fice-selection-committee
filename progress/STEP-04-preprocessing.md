# STEP-04 — Image Preprocessing Pipeline

**Status**: ✅ done
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

- [x] **Loader — JPEG**: load `photo.jpg` → returns 1-element list of PIL.Image.
- [x] **Loader — PDF single page**: `ipn_scan.pdf` → 1-element list.
- [x] **Loader — PDF multi-page**: `multipage.pdf` → 3 elements, ordered.
- [x] **Loader — corrupt**: `corrupt.bin` → raises `UnsupportedFormatError`.
- [x] **Loader — too many pages**: synthesize 11-page PDF → raises `PreprocessingError`.
- [x] **Pipeline — golden fixture**: run on `passport_clean.png` → output pixel-diff vs `golden/passport_clean_preprocessed.png` < 0.5% (use `numpy.mean(np.abs(a-b))/255 < 0.005`).
- [x] **Pipeline — deskew**: input is `passport_clean.png` rotated 7° via PIL. Run pipeline. Compute output skew via Hough; assert `|skew| < 0.5°`.
- [x] **Pipeline — DPI normalization**: 150-DPI input → output DPI metadata is 300; height ratio matches expected scale.
- [x] **Pipeline — determinism**: run twice on same input → byte-equal arrays.
- [x] **Pipeline — large image guard**: 9000×9000 → raises `PreprocessingError` (exceeds dim limit).

## Definition of Done

- [x] All files created including golden fixtures
- [x] All 10 tests pass (10/10 on Linux/CI; 7/10 + 3 skipped on Windows hosts without poppler — see Regressions Caught)
- [x] `ruff` + `mypy --strict` clean
- [x] Docker image builds with new system deps (`poppler-utils`, `libmagic1`)
- [x] `progress/README.md` STEP-04 row marked ✅

## Regressions Caught

- **`pdf2image` requires the poppler binaries on PATH at runtime.** Pure-pip `pdf2image` is just a Python wrapper around `pdftoppm` and `pdfinfo`; on Linux the Dockerfile installs `poppler-utils`, but on Windows local dev the host needs an explicit install (`winget install oschwartz10612.Poppler`). To stop the suite turning red on hosts without poppler, the conftest grew a `require_poppler` fixture that skips the three PDF tests when `pdftoppm` / `pdfinfo` are missing. CI on Ubuntu installs `poppler-utils` in a dedicated step before `poetry install` so all 10 gates run there.
- **`python-magic` ships only Python bindings.** Linux Docker uses `libmagic1` (apt); Windows local dev needs `python-magic-bin` (which bundles a libmagic DLL). The `ml` group declares `python-magic-bin` with `markers = "sys_platform == 'win32'"` so Linux/macOS resolutions stay clean and the Docker builder layer is unaffected.
- **CI workflow was scoped to `--without ml`.** The pre-STEP-04 `cv-service-ci.yml` skipped the optional ml group, so adding STEP-04 tests under that policy would have left the new gates uncovered. Switched the install step to `--with ml`, added the apt install of `poppler-utils libmagic1`, and bumped the venv cache key (`cv-venv-ml-…`) so the cached venv does not silently miss the new wheels.
- **Pillow 13 deprecates `Image.fromarray(arr, mode=...)`.** The fixture generator originally passed `mode="RGB"`; with `filterwarnings = ["error"]` in `pyproject.toml` this aborts the suite. Dropped the `mode=` kwarg — Pillow infers it from the dtype and shape.
- **`Settings.max_pages` / `Settings.max_image_dimension` were missing.** STEP-04's spec mentions `CV_MAX_PAGES` and an 8000-px dimension cap but the existing `Settings` class did not declare either. Added both with safe defaults (10 / 8000) so the loader and pipeline can read from a single source of truth without re-parsing env vars.
- **Docker builder used `--without ml,dev`.** Pre-STEP-04 the runtime image excluded the entire ml group; once preprocessing lands the orchestrator (STEP-08) needs `cv2`/`pdf2image`/`magic` at request time, so the builder switched to `--with ml --without dev`. Image grew 55.8 MB → 155.7 MB. Documented at the top of the Dockerfile builder stage; STEP-05 (PaddleOCR) will roughly double it again.

## Notes

- Do NOT use `numpy.random` or `cv2`'s non-deterministic ML transforms (no `cv2.dnn`).
- Do NOT do per-region OCR-aware preprocessing here — that's STEP-05's job.
- Do NOT touch the original file on disk; pipeline operates on copies in memory.

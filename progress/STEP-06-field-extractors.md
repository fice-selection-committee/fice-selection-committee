# STEP-06 — Field Extractors

**Status**: ✅ DONE
**Depends on**: STEP-05
**Blocks**: STEP-08

## Goal

Convert OCR token streams into structured fields per document type: `passport`, `ipn`, `foreign_passport`. Each extractor returns named fields plus a per-extractor confidence score and a list of warnings. Includes Ukrainian-tax-ID checksum validation (mod-11) and ICAO 9303 MRZ parsing for foreign passports.

## Files to Create

```
server/services/selection-committee-computer-vision/src/cv_service/extraction/
├── __init__.py
├── base.py                 FieldExtractor ABC + ExtractionResult model
├── passport.py             Ukrainian internal passport extractor
├── ipn.py                  Ukrainian tax ID (10-digit, mod-11 checksum)
├── foreign_passport.py     ICAO 9303 MRZ parser
├── router.py               picks extractor by documentType
├── regex_patterns.py       Ukrainian name/date patterns (centralized)
└── exceptions.py           UnsupportedDocumentTypeError, ExtractionError
```

```
tests/extraction/
├── __init__.py
├── fixtures/
│   ├── passport_tokens.json       (sample OcrResult JSON for passport)
│   ├── ipn_tokens.json
│   ├── foreign_passport_mrz.json
│   └── garbage_tokens.json
├── test_passport.py
├── test_ipn.py
├── test_foreign_passport.py
└── test_router.py
```

## Implementation Outline

### `base.py`
```python
class ExtractionResult(BaseModel):
    fields: dict[str, str]           # canonical field names
    confidence: float                # 0.0 — 1.0 aggregate
    warnings: list[str]              # human-readable hints

class FieldExtractor(ABC):
    @abstractmethod
    def extract(self, ocr: OcrResult) -> ExtractionResult: ...
```

### `passport.py` — Ukrainian internal passport
Canonical fields:
- `surname`, `given_name`, `patronymic` — Ukrainian Cyrillic, regex: `[А-ЯҐЄІЇа-яґєії'-]{2,40}`
- `birth_date` — `DD.MM.YYYY` or `DD MMM YYYY`
- `document_number` — 9-digit number
- `issue_date`, `validity_date`
- `issuing_authority` — free text

Algorithm:
1. Find tokens matching `surname` label (`Прізвище` / `Surname`); take next 1-2 tokens above/right of label.
2. Same heuristic for given_name (`Ім'я` / `Name`), patronymic (`По батькові`).
3. Date regex against all tokens; classify by relative position (top → birth, middle → issue, bottom → validity).
4. 9-digit number regex for document_number.
5. Aggregate confidence = mean of contributing token confidences, scaled by % of expected fields found.

### `ipn.py` — Ukrainian tax ID
- Find first 10-digit numeric token.
- Apply mod-11 checksum:
  ```
  weights = [-1, 5, 7, 9, 4, 6, 10, 5, 7]   # for digits 0..8
  sum = sum(int(d) * w for d, w in zip(digits[:9], weights))
  check = sum % 11 % 10
  valid = (check == int(digits[9]))
  ```
- On invalid checksum: keep field but `confidence *= 0.5`, append warning `"ipn_checksum_invalid"`.
- Field: `ipn` (10 digits as string).

### `foreign_passport.py` — ICAO 9303 MRZ (TD3 format, 2 × 44 chars)
- Find MRZ candidate: look for 2 adjacent tokens with `len == 44` and chars in `[A-Z0-9<]`.
- Parse line 1: `P<NATIONALITY<SURNAME<<GIVEN<NAMES<...`
- Parse line 2: doc_number (9), check_digit, nationality (3), DOB (6), check_digit, sex (1), expiry (6), check_digit, personal_number (14), check_digit, composite_check_digit
- Validate each check digit (ICAO 9303 weight scheme `[7,3,1]`).
- Fields: `document_number`, `nationality`, `surname`, `given_name`, `birth_date` (YYMMDD → YYYY-MM-DD with century inference), `sex`, `expiry_date`.
- On any check-digit mismatch: confidence per failing field `*= 0.7`; warning `"mrz_check_digit_failed:<field>"`.

### `router.py`
```python
EXTRACTORS = {
    "passport": PassportExtractor,
    "ipn": IpnExtractor,
    "foreign_passport": ForeignPassportExtractor,
}

def extract(document_type: str, ocr: OcrResult) -> ExtractionResult:
    cls = EXTRACTORS.get(document_type.lower())
    if cls is None:
        raise UnsupportedDocumentTypeError(document_type)
    return cls().extract(ocr)
```

## Tests (Acceptance Gates)

### `test_passport.py`
- [ ] Golden fixture `passport_tokens.json` extracts expected surname/given_name/birth_date/document_number with confidence > 0.7.
- [ ] Missing surname tokens → field absent, confidence reduced, warning emitted.
- [ ] Garbage tokens → empty fields dict, confidence = 0.

### `test_ipn.py`
- [ ] **10 valid IPN samples** (real-world test IDs that pass mod-11) → checksum valid, full confidence.
- [ ] **10 invalid IPN samples** (bad check digit) → field present, confidence ≤ 0.5, warning `"ipn_checksum_invalid"`.
- [ ] No 10-digit token found → empty fields, confidence = 0.
- [ ] Multiple 10-digit candidates → picks the one with highest token confidence.

### `test_foreign_passport.py`
- [ ] **ICAO 9303 reference sample** (from spec appendix): all fields parsed correctly, all check digits valid.
- [ ] Broken doc_number check digit → confidence on `document_number` reduced; warning emitted.
- [ ] No MRZ found → empty fields.
- [ ] Date parsing: `YYMMDD` `940315` → `1994-03-15` (century inference: if YY > current year's last 2 digits + 5, use 19xx; else 20xx).

### `test_router.py`
- [ ] `extract("passport", ocr)` → calls PassportExtractor.
- [ ] `extract("PASSPORT", ocr)` → case-insensitive, calls PassportExtractor.
- [ ] `extract("unknown", ocr)` → raises `UnsupportedDocumentTypeError`.

## Definition of Done

- [x] All 4 extractor files created
- [x] Router dispatches correctly
- [x] All ~15 parametrized tests pass (**40 gates green**, incl. 20 parametrised IPN cases)
- [x] `ruff` + `mypy --strict` clean
- [x] `progress/README.md` STEP-06 row marked ✅

## Notes

- The 10 valid + 10 invalid IPN samples should be **synthetic** (computed via the algorithm), not real people's IDs.
- Foreign-passport extractor handles **TD3 only** (2 × 44 chars). TD1 (3 × 30) is out-of-scope; document this.
- Ukrainian internal passport in card form (since 2016) has its own MRZ on the back; if MRZ is detected on a `passport` document, it overrides label-based extraction. Handle gracefully but test deferred to STEP-13 load test fixtures.

## Regressions Caught

Non-obvious decisions / fixes made during execution. Captured here so STEP-07 → STEP-14 inherit the lessons.

### MRZ two-digit year inference — 50/50 pivot, not "current year + 5"

The spec proposed `YY > current_yy + 5 → 19xx, else 20xx`. With the 2026 anchor that resolves an expiry of `35` to 1935 — wrong for any future expiry beyond a 5-year window. Replaced with the de-facto-standard 50/50 pivot:

- `YY > 50` → `19YY`
- `YY ≤ 50` → `20YY`

Documented in `cv_service.extraction.foreign_passport`'s module docstring. The fixture set covers both directions: `940315` (DOB) → 1994-03-15, `350101` (expiry) → 2035-01-01.

### Programmatic OCR fixtures, not JSON files

The "Files to Create" list mentioned `tests/extraction/fixtures/*.json`. Built tokens programmatically in `tests/extraction/conftest.py` instead: the parametric IPN suite (10 valid + 10 invalid) computes IPNs via the same mod-11 algorithm the extractor uses, and serialising those to JSON would add a maintenance surface without coverage upside. STEP-08 contract tests can synthesise their own JSON if they want to round-trip through the wire format.

### Bypass `cv_service.ocr.__init__` to avoid paddle import in extraction tests

`tests/extraction/conftest.py` imports `OcrResult` / `OcrToken` directly from `cv_service.ocr.models` rather than the package surface. The package `__init__.py` re-exports those names but also imports `OcrEngine` → `paddleocr` → `paddle`, which triggers `google.protobuf` and `astor` `DeprecationWarning` instances under Python 3.12. Even with the deeper-module import, Python still runs `cv_service.ocr.__init__` (PEP 328); the project-wide `filterwarnings = ["error"]` policy then turns those into collection errors. Added two new ignores to `[tool.pytest.ini_options].filterwarnings` matching the existing paddle / paddleocr / ppocr quarantine pattern:

- `ignore::DeprecationWarning:google.protobuf.*`
- `ignore::DeprecationWarning:astor.*`

### Passport label-value boundary case — touching x is acceptable

Initial `_find_value_to_right_of` used `_bbox_x_start(token) <= label_x_end` as the "to-the-right" predicate, which excluded values whose bbox starts exactly where the label ends (the "По батькові" → "ГРИГОРОВИЧ" pair in the golden fixture). Loosened to strict `<` so touching boundaries pass through. This is the only difference between the green golden test and a 6/7 partial.

### IPN tie-break — highest confidence, not first match

Spec said "first 10-digit token". Switched to "highest-confidence 10-digit token" — robust against page numbers / phone fragments / OCR noise that may precede the legitimate IPN. The parametric `test_picks_highest_confidence_candidate_when_multiple_present` gate pins this behaviour.

### `tests/extraction/**` + `src/cv_service/extraction/**` opt into RUF001/002/003 ignores

Same pattern as `tests/ocr/**` (STEP-05). The Cyrillic label tables (`Прізвище`, `Ім'я`, `По батькові`) and the apostrophe-variant character class (`'’`) trip ambiguous-unicode checks otherwise. The `noqa` per-line approach is fragile across formatter line-wraps; the per-file-ignores entry is the proven pattern in this repo.

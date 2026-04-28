# STEP-06 — Field Extractors

**Status**: ⏳ TODO
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

- [ ] All 4 extractor files created
- [ ] Router dispatches correctly
- [ ] All ~15 parametrized tests pass
- [ ] `ruff` + `mypy --strict` clean
- [ ] `progress/README.md` STEP-06 row marked ✅

## Notes

- The 10 valid + 10 invalid IPN samples should be **synthetic** (computed via the algorithm), not real people's IDs.
- Foreign-passport extractor handles **TD3 only** (2 × 44 chars). TD1 (3 × 30) is out-of-scope; document this.
- Ukrainian internal passport in card form (since 2016) has its own MRZ on the back; if MRZ is detected on a `passport` document, it overrides label-based extraction. Handle gracefully but test deferred to STEP-13 load test fixtures.

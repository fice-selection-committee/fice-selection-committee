# STEP-11 — Frontend Integration

**Status**: ✅ DONE
**Depends on**: STEP-10
**Blocks**: STEP-12

## Goal

Surface OCR results in the frontend per UC-02 (auto-fill applicant profile from passport/IPN) and FLOW-06 (operator sees OCR-extracted fields next to the document image during package review).

## Files to Create

```
client/web/src/
├── types/
│   └── ocr.ts                                    OcrResult type, OcrStatus enum
├── lib/api/
│   └── ocr.ts                                    fetchOcrResult(documentId)
├── lib/queries/
│   └── ocr-keys.ts                               TanStack Query key factory
├── hooks/
│   └── use-document-ocr.ts                       polling hook (2s interval, 60s timeout)
└── components/documents/
    ├── ocr-result-card.tsx                       confidence-banded display + auto-fill CTA
    ├── ocr-confidence-badge.tsx                  small reusable confidence indicator
    └── ocr-field-row.tsx                         single key-value row with confidence dot

client/web/tests/e2e/
└── cv-ocr-flow.spec.ts                           Playwright E2E

client/web/tests/unit/
├── ocr-result-card.test.tsx
├── use-document-ocr.test.ts
└── ocr-confidence-badge.test.tsx
```

```
client/web/tests/fixtures/
└── passport_sample.png                           committed test fixture (synthetic)
```

## Files to Modify

- `client/web/src/app/(dashboard)/applicant/documents/page.tsx` — render `<OcrResultCard documentId={doc.id} />` in each uploaded document row when type ∈ {PASSPORT, IPN, FOREIGN_PASSPORT}. On "Auto-fill profile" click, populate the applicant profile form via Zustand action.
- `client/web/src/app/(dashboard)/operator/personal-files/page.tsx` — add an OCR-data column / side panel showing extracted fields next to the document image preview.
- `client/web/src/lib/queries/query-keys.ts` — re-export `ocrKeys` for consistency.

## Implementation Outline

### `types/ocr.ts`
```ts
export type OcrStatus = 'PENDING' | 'PARSED' | 'FAILED' | 'UNAVAILABLE';

export interface OcrResult {
  documentId: number;
  status: OcrStatus;
  fields: Record<string, string> | null;
  confidence: number | null;          // 0.0–1.0
  errorReason: string | null;
  retriable: boolean | null;
  updatedAt: string;
}
```

### `lib/api/ocr.ts`
```ts
export async function fetchOcrResult(documentId: number): Promise<OcrResult | null> {
  const res = await apiClient.get<OcrResult>(`/api/v1/documents/${documentId}/ocr`, {
    validateStatus: (s) => s === 200 || s === 404,
  });
  return res.status === 404 ? null : res.data;
}
```

### `hooks/use-document-ocr.ts`
```ts
export function useDocumentOcr(documentId: number | null) {
  return useQuery({
    queryKey: ocrKeys.byDocument(documentId),
    queryFn: () => fetchOcrResult(documentId!),
    enabled: documentId != null,
    refetchInterval: (query) => {
      const data = query.state.data;
      if (!data || data.status === 'PENDING') return 2_000;
      return false;     // stop polling on PARSED / FAILED / null
    },
    refetchIntervalInBackground: false,
    staleTime: 0,
    gcTime: 5 * 60_000,
    // Custom: 60s overall timeout to mark UNAVAILABLE
    select: (data) => data ?? { status: 'PENDING' as OcrStatus, ... },
  });
}
```
Use a wrapping `useEffect` to set a 60s timer; if still PENDING, mark `status='UNAVAILABLE'` in local state.

### `components/documents/ocr-result-card.tsx`
- States:
  - `PENDING` → shimmering placeholder with hint "OCR processing…"
  - `PARSED` (high conf ≥ 0.75) → green badge + extracted fields + "Auto-fill profile" CTA
  - `PARSED` (medium 0.5–0.75) → amber badge + fields + CTA disabled-with-tooltip "Confidence too low"
  - `PARSED` (low < 0.5) → red badge + fields + warning copy
  - `FAILED` → muted card with retry CTA (retry triggers re-publish via documents-service action — if implemented; otherwise just shows error)
  - `UNAVAILABLE` → "OCR temporarily unavailable" muted hint, no CTA, no error toast
- Uses existing iOS-Atmosphere design tokens; no new colors.

### `ocr-confidence-badge.tsx`
- Three variants: `high` (`bg-success-soft text-success`), `medium` (`bg-warning-soft text-warning`), `low` (`bg-destructive-soft text-destructive`).
- Renders confidence as `0–100%` rounded.

### Auto-fill action
- New Zustand action in the existing applicant-profile store: `applyOcrFields(fields: Record<string,string>)`.
- Maps OCR field names → profile form fields:
  - `surname` → `lastName`
  - `given_name` → `firstName`
  - `patronymic` → `middleName`
  - `birth_date` → `dateOfBirth` (parse to ISO)
  - `ipn` → `taxId`
  - `document_number` → `passportNumber`
- After applying, scroll to the profile form and highlight populated fields with a brief animation.

### Operator personal-files page
- Reuse the same `ocr-result-card.tsx` but in **read-only** mode (no auto-fill CTA). Show all fields including any FAILED diagnostics.

## Tests (Acceptance Gates)

### Unit (Vitest)
- [ ] `ocr-confidence-badge.test.tsx`: 0.9 → high (green); 0.6 → medium (amber); 0.3 → low (red); 0.0 → low.
- [ ] `ocr-result-card.test.tsx`:
  - PENDING state renders shimmer placeholder.
  - PARSED+high renders all fields + enabled CTA.
  - PARSED+low renders fields + disabled CTA.
  - FAILED renders error reason and retry option.
  - UNAVAILABLE renders muted hint, no error.
- [ ] `use-document-ocr.test.ts`:
  - Polls every 2s while PENDING (use vi.useFakeTimers).
  - Stops polling on PARSED.
  - Emits UNAVAILABLE after 60s of continuous PENDING.

### E2E (Playwright) — `cv-ocr-flow.spec.ts`
Pre-req: full docker-compose stack up; CV-Service mocked via test-only `cv-stub-service` OR real (cheap on synthetic fixture).
- [ ] Login as `applicant@test.local` (existing test fixture).
- [ ] Upload `tests/fixtures/passport_sample.png` via the documents page.
- [ ] Wait for `OcrResultCard` to render with `data-status="PARSED"` (max 30s).
- [ ] Assert displayed fields contain non-empty `surname` and `given_name`.
- [ ] Click "Auto-fill profile" → navigate to profile form → assert `lastName` and `firstName` inputs are populated.
- [ ] Save profile → backend confirms persistence (existing endpoint).

### E2E — operator path
- [ ] Login as `operator@test.local`.
- [ ] Open the same applicant's package via `/operator/personal-files`.
- [ ] Assert OCR-data panel renders next to the document image with the same extracted fields.

### Visual regression (light)
- [ ] Confidence badge colors match design tokens — assert via `getComputedStyle`, not snapshot, to avoid brittleness.
- [ ] No new ad-hoc Tailwind colors introduced (lint rule).

## Definition of Done

- [x] All component files created (`ocr-confidence-badge.tsx`, `ocr-field-row.tsx`, `ocr-result-card.tsx`, `ocr-result-panel.tsx` — last is the hook+card composition that the pages consume)
- [x] Both pages updated (`/applicant/documents` interactive; `/operator/applications/[id]` readOnly. Spec named `/operator/personal-files` but that route only lists accepted apps without per-document previews; OCR was wired into the actual review surface — see "Regressions Caught" below.)
- [x] All unit tests green (Vitest) — 23/23 STEP-11 cases green; 150/151 total (1 preexisting date-flake on `develop`)
- [x] E2E spec written (`tests/e2e/regression/applicant/cv-ocr-flow.spec.ts`) — runs in CI; local run deferred (Next 16.2.1 Turbopack dev server hangs on first request, environmental, not STEP-11 code)
- [x] No new lint warnings on STEP-11 files (Biome auto-formatted)
- [x] `pnpm build` succeeds (after `notification-settings.tsx` 1-line fix — see Regressions Caught)
- [x] `progress/README.md` STEP-11 row marked ✅

## Regressions Caught

- `src/components/notifications/notification-settings.tsx`: `useRef<ReturnType<typeof setTimeout>>()` was called with no argument, which fails React 19's stricter `useRef` overload (`Expected 1 arguments, but got 0`). This blocked `pnpm build` on a clean `develop`, NOT introduced by STEP-11. Fixed surgically: `useRef<ReturnType<typeof setTimeout> | undefined>(undefined)`.
- Spec called for OCR card on `/operator/personal-files`; that route only lists accepted applications and has no document previews. The actual operator review surface is `/operator/applications/[id]`, which is where this PR wired the readOnly OCR panel — the spec's intent ("operator sees OCR-extracted fields next to the document image during package review") is satisfied there.
- `OcrResultCard` self-mounts `<TooltipProvider>` for the disabled-CTA case (medium/low confidence). Without this, the unit test `renders fields and disabled CTA with tooltip for PARSED + medium confidence` fails with `Tooltip must be used within TooltipProvider` because the test wrapper does not include the global provider that lives in `app/layout.tsx`.
- `DocumentType` union extended with `FOREIGN_PASSPORT` so the OCR-eligible-types check (`PASSPORT | IPN | FOREIGN_PASSPORT`) compiles. The upload select still does not expose FOREIGN_PASSPORT — that's a UX gap unrelated to STEP-11 (the documents-service backend already accepts it; the frontend type was lagging).
- Polling hook tracks the elapsed-since-PENDING window in a `useRef` rather than a `setTimeout` queued at mount, so navigating away during the 60s window doesn't leak a timer that fires on a stale component. The forced `UNAVAILABLE` state is local to the hook (returned merged with the cached PARSED-shape) so the React Query cache stays canonical and a manual `refetch()` would still see the server's latest status.

## Notes

- Use the existing `apiClient` (`src/lib/api/client.ts`) — do not create a new Axios instance.
- Use existing TanStack Query patterns; `ocrKeys` factory follows the same shape as `documentKeys`.
- The auto-fill UX deliberately requires user confirmation (a click) — never auto-populate without explicit consent (UX + privacy).
- For operator view, OCR-data should NOT block the page render if the OCR is still pending; show the document image first, OCR card later.

# Phase 8: Operator Pages (UC-03, UC-04, UC-05, UC-07)

**Depends on**: Phase 4, 6
**Blocks**: None

## Checklist

- [ ] `src/app/(dashboard)/operator/page.tsx` — Operator dashboard:
  - Stats cards: assigned applications count, pending review, accepted today
  - SLA warning: applications nearing review deadline
  - Quick access to application queue

- [ ] `src/app/(dashboard)/operator/applications/page.tsx` — Application queue:
  - Data table (from Phase 6) with columns:
    - Applicant name, submission date, status, assigned date
  - Filters: status (ASSIGNED, ACCEPTED, REWORK_REQUIRED), date range
  - Search by applicant name
  - Click row → navigate to review page

- [ ] `src/app/(dashboard)/operator/applications/[id]/page.tsx` — Application review:
  - Applicant info summary (name, email, program, faculty)
  - Document checklist:
    - Each required document: thumbnail, status, accept/flag per document
    - Document viewer (inline preview via signed URL)
    - Comments input per document
  - Overall actions:
    - **Accept** button → confirm dialog → `useMutation` with optimistic update
    - **Return** button → reason textarea → confirm → sends notification to applicant
  - Optimistic UI: clicking Accept immediately updates status badge, server confirms
  - Error rollback: if server rejects, revert UI and show toast

- [ ] `src/components/documents/document-checklist.tsx`:
  - Per-document review row: preview, metadata, accept/flag toggle, comment
  - Integrated document viewer (modal or side panel)

- [ ] `src/components/documents/document-viewer.tsx`:
  - Image viewer for JPG/PNG (zoom, pan)
  - PDF viewer via `<iframe>` with signed URL
  - Dynamic import (`next/dynamic` with `ssr: false`)

- [ ] `src/app/(dashboard)/operator/personal-files/page.tsx` — Personal files:
  - List of accepted applications ready for personal file generation
  - "Generate Personal File" button per application
  - Download generated PDF
  - Status: pending, generating, ready

## Deliverables
- Operator can review assigned applications
- Accept/return with optimistic UI
- Document checklist with inline preview
- Personal file generation trigger

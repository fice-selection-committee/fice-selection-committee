# Phase 7: Applicant Pages (UC-01, UC-02)

**Depends on**: Phase 4, 5, 6
**Blocks**: None

## Checklist

- [ ] `src/app/(dashboard)/applicant/page.tsx` — Applicant dashboard:
  - Application status card (current status badge, last updated)
  - Required documents checklist (shows which are uploaded, which are missing)
  - Recent notifications area
  - Quick action buttons: "Upload Document", "View Application"

- [ ] `src/app/(dashboard)/applicant/documents/page.tsx` — Document management:
  - Document type selector dropdown (passport, IPN, diploma, etc.)
  - File dropzone component (from Phase 6)
  - List of uploaded documents as cards:
    - Document type, filename, upload date, status badge
    - Actions: view, delete (if not yet submitted)
  - Uses `useDocuments()` query hook + `useFileUpload()` hook

- [ ] `src/app/(dashboard)/applicant/documents/[id]/page.tsx` — Document detail:
  - Document metadata (type, filename, size, upload date, status)
  - Preview via signed URL (image or PDF iframe)
  - Download button
  - Delete button (if status allows)
  - Uses `useDocument(id)` query hook

- [ ] `src/components/documents/document-upload.tsx`:
  - Combines file-dropzone + document type selector
  - Shows upload progress
  - Success/error toast notifications
  - Invalidates documents query on success

- [ ] `src/components/documents/document-list.tsx`:
  - Grid of document cards
  - Empty state when no documents
  - Loading skeleton

- [ ] `src/components/documents/document-card.tsx`:
  - Thumbnail (file type icon), name, status badge
  - Click navigates to detail page

- [ ] `src/app/(dashboard)/applicant/application/page.tsx` — Application form:
  - Cascading selects: Faculty → Cathedra → Educational Program
  - Uses `useFaculties()`, `useCathedras(facultyId)`, `usePrograms(cathedraId)` queries
  - Priority/privilege selection (if applicable)
  - Form of study selection (budget/contract, full-time/part-time)
  - Submit button (PUT `/{id}/submit`) with confirmation dialog
  - Status display if already submitted

- [ ] `src/app/(dashboard)/applicant/profile/page.tsx` — Profile:
  - View/edit personal info (name, email, phone)
  - React Hook Form + Zod validation
  - Notification preferences (Telegram/Email)

## Deliverables
- Complete applicant workflow: upload docs → fill application → submit
- 4-step document upload with progress tracking
- Cascading faculty/cathedra/program selects
- Document preview via signed URLs

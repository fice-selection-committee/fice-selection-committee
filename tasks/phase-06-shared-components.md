# Phase 6: Shared/Reusable Components

**Depends on**: Phase 2
**Blocks**: Phase 7-11

## Checklist

- [ ] `src/components/shared/data-table.tsx`:
  - Built on shadcn/ui Table + TanStack Table
  - Server-side pagination (Spring `Page<T>` response: `content`, `totalElements`, `totalPages`, `number`, `size`)
  - Column sorting (sortable headers with icons)
  - Column filtering (text input per column)
  - Row selection (checkbox column)
  - Loading skeleton state
  - Empty state fallback
  - Props: `columns`, `data`, `pageCount`, `onPaginationChange`, `onSortingChange`, `isLoading`

- [ ] `src/components/shared/file-dropzone.tsx`:
  - Drag-and-drop zone (native HTML5 drag events or react-dropzone)
  - File type validation: PDF, JPG, PNG, ASICE, P7S
  - Size validation: max 20MB (gateway limit for documents-service)
  - Visual feedback: border highlight on drag-over
  - Upload progress bar (axios `onUploadProgress`)
  - Multiple file support
  - Error display for invalid files

- [ ] `src/hooks/use-file-upload.ts`:
  - 4-step upload state machine:
    1. `createMetadata` — POST `/api/v1/documents` → documentId
    2. `getPresignedUrl` — POST `/api/v1/documents/{id}/presign` → S3 URL
    3. `uploadToS3` — PUT to presigned URL (direct browser→MinIO)
    4. `confirmUpload` — GET `/api/v1/documents/{id}/confirm`
  - Progress tracking per step
  - Retry per step on failure
  - Returns: `{ upload, progress, isUploading, error }`

- [ ] `src/components/shared/role-guard.tsx`:
  - Client-side RBAC wrapper:
    ```tsx
    <RoleGuard allowed={['ADMIN', 'EXECUTIVE_SECRETARY']}>
      <ProtectedContent />
    </RoleGuard>
    ```
  - Uses `useAuth()` hook to check role
  - Renders nothing (or fallback) if role not in allowed list

- [ ] `src/components/shared/search-input.tsx`:
  - Input with search icon
  - Debounced onChange (300ms) via `use-debounce` hook
  - Clear button
  - Props: `value`, `onChange`, `placeholder`, `debounceMs`

- [ ] `src/hooks/use-debounce.ts`:
  - Generic debounce hook: `useDebounce<T>(value: T, delay: number): T`

- [ ] `src/components/shared/status-badge.tsx`:
  - Colored badge component
  - Variant maps for different status types:
    - Application: NEW, SUBMITTED, ASSIGNED, ACCEPTED, REWORK_REQUIRED, ENROLLED, ARCHIVED
    - Document: UPLOADING, UPLOADED, VERIFIED, REJECTED
    - Contract: DRAFT, REGISTERED
    - Order: DRAFT, APPROVED, SIGNED

- [ ] `src/components/shared/confirm-dialog.tsx`:
  - Reusable confirmation modal
  - Props: `title`, `description`, `confirmLabel`, `variant` (default/destructive), `onConfirm`, `onCancel`
  - Built on shadcn/ui AlertDialog

- [ ] `src/components/shared/page-header.tsx`:
  - Page title (h1) + optional description + optional action buttons
  - Consistent spacing and typography

- [ ] `src/components/shared/empty-state.tsx`:
  - Icon + title + description + optional action button
  - Used when data tables or lists have no items

- [ ] `src/components/shared/loading-skeleton.tsx`:
  - Page-level loading skeleton
  - Card skeleton, table row skeleton variants

- [ ] `src/components/shared/pagination.tsx`:
  - Page navigation: prev/next + page numbers
  - Integrates with Spring `Page<T>` response shape
  - Props: `currentPage`, `totalPages`, `onPageChange`

## Deliverables
- All shared components built and themed
- File upload hook with 4-step flow
- Data table with full server-side features
- Role guard for client-side RBAC

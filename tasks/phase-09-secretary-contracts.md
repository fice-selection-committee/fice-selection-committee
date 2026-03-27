# Phase 9: Secretary & Contract Pages (UC-08, UC-10)

**Depends on**: Phase 4, 6
**Blocks**: None

## Checklist

### Secretary Dashboard
- [ ] `src/app/(dashboard)/secretary/page.tsx`:
  - Overview cards: pending contracts, pending orders, applications by status
  - Recent activity timeline
  - Quick links to contracts and orders

### Secretary — Applications
- [ ] `src/app/(dashboard)/secretary/applications/page.tsx`:
  - All applications data table (not just assigned like operator)
  - Advanced filters: status, faculty, program, date range, operator
  - Bulk actions: select multiple → export, assign

### Secretary — Contracts (UC-08)
- [ ] `src/app/(dashboard)/secretary/contracts/page.tsx`:
  - Contract registry data table
  - Columns: applicant, contract number, date, status, type
  - Filters: status (DRAFT, REGISTERED), date range
  - Search by applicant name or contract number

- [ ] `src/app/(dashboard)/secretary/contracts/[id]/page.tsx`:
  - Contract detail view
  - Applicant info, contract type, terms
  - "Register Contract" button:
    - Auto-generated number (e.g., "11б") + current date
    - Confirm dialog with number/date preview
    - Idempotent POST with X-Request-Id

- [ ] `src/components/contracts/contract-register-dialog.tsx`:
  - Shows proposed contract number and date
  - Allows manual number correction (with uniqueness validation)
  - Confirm → POST mutation → invalidates contracts query

- [ ] `src/components/contracts/contract-table.tsx`:
  - Reuses data-table with contract-specific columns

### Secretary — Orders (UC-10)
- [ ] `src/app/(dashboard)/secretary/orders/page.tsx`:
  - Order list data table
  - Columns: order number, type (enroll/expel), date, status, applicant count
  - Filters: type, status, date range

- [ ] `src/app/(dashboard)/secretary/orders/create/page.tsx`:
  - Order creation form:
    - Type selector: enrollment / expulsion
    - Candidate selection (multi-select from eligible applicants)
    - Template selection
    - Preview generated order text
  - Submit → creates order with auto-generated number

- [ ] `src/app/(dashboard)/secretary/orders/[id]/page.tsx`:
  - Order detail: metadata, list of candidates, status
  - PDF preview (dynamic import of PDF viewer)
  - Download PDF button
  - Status actions: approve, sign

- [ ] `src/components/orders/order-pdf-preview.tsx`:
  - Inline PDF viewer
  - `next/dynamic` with `ssr: false`

### Contract Manager View (separate route group)
- [ ] `src/app/(dashboard)/contracts/page.tsx`:
  - Same contract registry table (reuses contract-table component)
  - Scoped to CONTRACT_MANAGER role

- [ ] `src/app/(dashboard)/contracts/[id]/page.tsx`:
  - Same contract detail + register functionality

## Deliverables
- Secretary can manage contracts and orders
- Contract registration with auto-generated numbers
- Order creation with candidate selection and PDF preview
- Contract manager has scoped access to contract registry

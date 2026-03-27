# Phase 10: Group Distribution Pages (UC-09)

**Depends on**: Phase 4, 6
**Blocks**: None

## Checklist

- [ ] `src/app/(dashboard)/groups/page.tsx` — Group list:
  - Data table of all groups
  - Columns: group code (e.g., ІС-32), specialty, capacity, filled, available
  - Filters: faculty, specialty, form of study
  - Search by group code

- [ ] `src/app/(dashboard)/groups/distribute/page.tsx` — Auto-distribution:
  - Year selector
  - Faculty/specialty filters
  - "Auto Distribute" button → POST `/api/v1/group-assignments/auto-distribute?year=2026`
  - Loading state during distribution
  - Protocol view after completion:
    - Table: student name, score, assigned group, reason
    - Summary stats: per-group fill rates
  - Confirm/Edit capability:
    - Manual override: drag-drop or select → reassign
    - Reason input for manual changes
  - "Confirm Distribution" button → finalizes assignments

- [ ] `src/app/(dashboard)/groups/[id]/page.tsx` — Group detail:
  - Group metadata: code, specialty, capacity, advisor
  - Assigned students table
  - Fill rate progress bar

- [ ] `src/components/groups/group-distribution-table.tsx`:
  - Shows distribution results with columns: student, score, group, status
  - Editable group assignment (inline select or dialog)

- [ ] `src/components/groups/group-protocol-view.tsx`:
  - Summary of auto-distribution results
  - Per-group stats
  - Export capability (if needed)

- [ ] `src/components/groups/manual-assignment-dialog.tsx`:
  - Select student → select target group
  - Reason input (required for manual overrides)
  - Confirm button

## Deliverables
- Group distribution officer can run auto-distribution
- View and edit distribution protocol
- Manual override with reason tracking
- Group detail with assigned students

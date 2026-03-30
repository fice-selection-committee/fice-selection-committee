# Interaction System Standards

This document defines the mandatory interaction behavior rules for the frontend. Every component change must comply with these standards. This is operational instruction, not advisory guidance.

---

## 1. Focus States

**Mandatory pattern for all interactive elements:**

```
outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50
```

Additional for bordered elements (inputs, selects):
```
focus-visible:border-ring
```

**Rules:**
- Use `focus-visible`, never `focus`. Mouse clicks must not trigger focus rings.
- Every keyboard-focusable element must have a visible focus indicator. No exceptions.
- Focus ring color uses `--ring` token (matches `--primary` in light, muted in dark).
- Custom interactive elements (buttons in dropdowns, clear buttons, icon actions) must include the focus ring.
- Radix-managed list items (DropdownMenuItem, SelectItem, CommandItem) use `focus:bg-accent` — this is correct because Radix manages focus-on-hover internally.

**Components where this is enforced:**
- `button.tsx`, `input.tsx`, `select.tsx`, `checkbox.tsx`, `radio-group.tsx`, `switch.tsx`, `tabs.tsx`, `badge.tsx`, `accordion.tsx`
- `sidebar-nav.tsx`, `back-button.tsx`, `search-input.tsx` (clear button), `file-dropzone.tsx` (remove button)

---

## 2. Hover States

**Buttons:** Variant-specific opacity/color shift (e.g., `hover:bg-primary/90`). Ghost and outline variants use `hover:bg-muted` (subtle gray, not accent). Defined in CVA variants.

**Form inputs (Input, SelectTrigger):** `hover:border-ring/50` — subtle border hint.

**Navigation links:** `hover:bg-muted hover:text-foreground` (inactive items only).

**Table rows:** `hover:bg-muted/50`.

**Non-interactive elements:** No hover state. No false affordances.

**Radix list items:** Hover is managed via Radix focus model (`focus:bg-accent`). Do not add `hover:` to these.

---

## 3. Active/Pressed State

**Standard:** `active:scale-[0.98]` on all Button components (applied in CVA base class).

- Provides immediate tactile feedback on press.
- Replaces the removed `-webkit-tap-highlight-color` for mobile.
- Does not apply when disabled (`disabled:pointer-events-none` prevents it).

---

## 4. Disabled State

**Standard opacity:** `disabled:opacity-50` for all components. No exceptions.

- Buttons: `disabled:pointer-events-none disabled:opacity-50`
- Form controls: `disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50`
- Labels: `peer-disabled:cursor-not-allowed peer-disabled:opacity-50`
- Upload zones: `pointer-events-none opacity-50`

**Rules:**
- Disabled must be semantic (HTML `disabled` attribute or Radix `disabled` prop), not visual-only.
- Never use opacity alone without `pointer-events-none` or `disabled` attribute.
- Do not use `disabled:saturate-0`. Opacity alone communicates disabled state.

---

## 5. Loading State

**Standard component:** `LoadingButton` for all async button actions.

**Loading prop source (in priority order):**
1. `form.formState.isSubmitting` — for React Hook Form submissions
2. `mutation.isPending` — for standalone TanStack Query mutations
3. Manual `useState` — only for non-RHF, non-mutation async actions

**Rules:**
- `LoadingButton` auto-disables via `disabled={disabled || loading}`.
- `aria-busy={loading}` is set automatically.
- Screen reader text "Завантаження..." is included automatically.
- Width preservation prevents layout shift.
- Regular `Button` must never be used for form submissions or async actions.

**Page/table loading:** Use `Skeleton` component (`PageSkeleton`, `CardSkeleton`, `TableRowSkeleton`).

---

## 6. Selected State

**Standard:** State-driven via `data-[state=active/checked/selected]` attributes from Radix.

- Never use className toggling for selected state.
- Navigation active: `bg-primary/10 text-primary` + `aria-current="page"`.
- Tab active: `data-[state=active]:bg-background data-[state=active]:text-foreground`.
- Table row selected: `data-[state=selected]:bg-muted`.

---

## 7. Error State

**Standard pipeline:** Zod schema -> React Hook Form -> FormControl `aria-invalid` -> component styling.

- Inputs: `aria-invalid:border-destructive aria-invalid:ring-destructive/20`
- Labels: `data-[error=true]:text-destructive`
- Messages: `text-sm text-destructive` via `FormMessage`
- Root errors: `role="alert"` for screen reader announcement

**Rules:**
- Never apply error styling manually. Use the FormControl -> aria-invalid pipeline.
- API errors use `toast.error()` via Sonner.

---

## 8. Cursor Semantics

- `<button>`: Browser default (pointer in most browsers). Do not add `cursor-pointer`.
- `<a>`: Browser default (pointer). Do not add `cursor-pointer`.
- `<input>`: Browser default (text).
- Disabled form controls: `cursor-not-allowed`.
- Disabled buttons: `pointer-events-none` (cursor irrelevant).
- `cursor-pointer`: Only on `<label>` wrapping hidden `<input type="file">`.

---

## 9. Transition Timing

**Allowed durations:**
- Default (150ms): Hover, focus changes
- `duration-200`: Modal open/close, content swaps
- `duration-300`: Sidebar collapse, sheet slide, step transitions
- `duration-500`: Decorative only (carousel, progress bar)

**Forbidden:** `duration-250` or any non-standard Tailwind value.

**Allowed easings:**
- Default (`ease`): Most transitions
- `ease-out`: Exit animations, loading state entrance
- `ease-in-out`: Decorative animations only

---

## 10. Color-as-State

**Tokenized semantic colors (globals.css):**

| State | Token | Usage |
|---|---|---|
| Primary/active | `--primary` | Active nav, primary buttons, focus rings |
| Error/invalid | `--destructive` | Error borders, destructive buttons |
| Success | `--success` | Success toasts |
| Warning | `--warning` | Warning toasts |
| Disabled text | `--muted-foreground` | Placeholders, descriptions |
| Selected surface | `primary/10` | Active nav background |

**Status badge colors:** Use `--status-*-bg` and `--status-*-text` tokens. These support both light and dark mode via CSS variables.

**Forbidden:** Raw Tailwind palette colors (`blue-100`, `green-800`, etc.) in any component. All colors must use CSS variable tokens.

---

## 11. Semantic Element Rules

| Action | Element | Never Use |
|---|---|---|
| Click action | `<button>` or `<Button>` | `<div onClick>`, `<span onClick>` |
| Navigation | `<Link>` or `<a>` | `<button onClick={navigate}>` |
| Form submission | `<button type="submit">` | `<div>` with form handler |
| Toggle/check | Radix primitive | `<div>` with manual state |

---

## 12. Reduced Motion

Global override in `globals.css`:
```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms;
    transition-duration: 0.01ms;
  }
}
```

This is WCAG SC 2.3.1 compliant. Do not override with `!important` animations.

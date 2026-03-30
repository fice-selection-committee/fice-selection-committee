# Phase 6: Frontend — Font, Theme Toggle & Dashboard Animation

## Overview
Change typography to SF Pro Display, add dark/light theme toggle, and add dashboard entry animation.

## Steps

### 6.1 Replace Poppins with SF Pro Display Font Stack
- **File:** `client/web/src/app/layout.tsx`
  - Remove `import { Poppins } from "next/font/google"` and the `poppins` variable
  - Remove `${poppins.variable}` from `<html>` className
- **File:** `client/web/src/app/globals.css`
  - Change `--font-sans: var(--font-poppins)` to:
    ```css
    --font-sans: "SF Pro Display", -apple-system, BlinkMacSystemFont, "Segoe UI",
                 "Noto Sans", "Helvetica Neue", Arial, sans-serif;
    ```
  - This provides: Apple devices get SF Pro Display, Windows gets Segoe UI, Linux gets Noto Sans (Cyrillic supported)

### 6.2 Create Theme Store
- **File (new):** `client/web/src/stores/theme-store.ts`
- Zustand persist store (follow `sidebar-store.ts` pattern):
  ```ts
  interface ThemeStore {
    theme: "light" | "dark";
    toggle: () => void;
  }
  ```
- Persist key: `"theme-preference"` in localStorage

### 6.3 Create Theme Provider
- **File (new):** `client/web/src/providers/theme-provider.tsx`
- Syncs Zustand state to `document.documentElement.classList.toggle("dark", theme === "dark")`
- Use `useEffect` to apply on mount and changes
- Add `suppressHydrationWarning` to `<html>` in root layout to prevent mismatch
- Optionally: inline script in `<head>` to read localStorage and apply `.dark` before paint (prevents flash)

### 6.4 Add Theme Toggle to Auth Layout
- **File:** `client/web/src/app/(auth)/layout.tsx`
- Small Sun/Moon toggle button in top-right corner (`absolute top-4 right-4`)
- Uses `useThemeStore` — needs `"use client"` wrapper or extracted component

### 6.5 Add Theme Toggle to Dashboard Topbar
- **File:** `client/web/src/components/layout/topbar.tsx`
- Add Sun/Moon `<Button variant="ghost" size="icon">` next to existing topbar actions
- Lucide icons: `Sun` and `Moon` (already available via lucide-react)

### 6.6 Integrate Theme Provider in Root Layout
- **File:** `client/web/src/app/layout.tsx`
- Wrap children with `<ThemeProvider>` inside the existing provider stack

### 6.7 Dashboard Entry Animation
- **File:** `client/web/src/app/(dashboard)/layout.tsx`
- Add subtle entry animation to `<main>` content area
- Use `tw-animate-css` classes (already imported): `animate-in fade-in slide-in-from-bottom-4 duration-500`
- Keep it subtle — should feel smooth, not flashy

## Acceptance Criteria
- [ ] Typography renders as SF Pro Display on macOS, Segoe UI on Windows, Noto Sans on Linux
- [ ] Cyrillic characters render correctly across all fallback fonts
- [ ] Dark/light toggle works and persists across page refreshes
- [ ] No flash of wrong theme on initial load
- [ ] Toggle button visible on both auth pages and dashboard
- [ ] Dashboard content has subtle fade-in animation on entry

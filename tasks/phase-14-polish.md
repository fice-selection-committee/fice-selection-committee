# Phase 14: Polish — SEO, Performance, Accessibility

**Depends on**: Phase 1-11
**Blocks**: None

## SEO

- [ ] `src/app/sitemap.ts` — Generate sitemap for public pages (login, register)
- [ ] `src/app/robots.ts`:
  ```ts
  export default function robots() {
    return {
      rules: [{ userAgent: '*', disallow: ['/applicant', '/operator', '/secretary', '/contracts', '/groups', '/admin'] }],
      sitemap: `${process.env.NEXT_PUBLIC_BASE_URL}/sitemap.xml`,
    };
  }
  ```
- [ ] Add `generateMetadata()` to every page with Ukrainian titles and descriptions
- [ ] Ensure public pages (login, register) are server-rendered for SEO

## Performance

- [ ] `next/image` — Replace all `<img>` with `<Image>` (width, height, alt required)
- [ ] `next/font` — Verify Poppins with `cyrillic` subset loads correctly (Phase 1, verify here)
- [ ] Dynamic imports for heavy components:
  - `next/dynamic(() => import('./document-viewer'), { ssr: false })`
  - `next/dynamic(() => import('./order-pdf-preview'), { ssr: false })`
- [ ] Add `@next/bundle-analyzer`:
  ```ts
  // next.config.ts
  const withBundleAnalyzer = require('@next/bundle-analyzer')({ enabled: process.env.ANALYZE === 'true' });
  ```
- [ ] Verify Server Components are used by default (no unnecessary `'use client'`)
- [ ] Debounce all search inputs (Phase 6, verify here)
- [ ] Virtualize long lists if needed (`@tanstack/react-virtual`)

## Accessibility

- [ ] Semantic HTML in all layouts: `<main>`, `<nav>`, `<header>`, `<aside>`, `<footer>`
- [ ] WCAG AA color contrast:
  - `#BC9BF3` on white: **fails** for small text (3.2:1) → use only for large text, backgrounds, decorative
  - `#9B7BCF` on white: **passes** for large text (4.6:1) → use for interactive text elements
  - Body text: use `--foreground` (dark) on `--background` (light) for high contrast
- [ ] `aria-label` on all icon-only buttons (sidebar toggle, mobile menu, close buttons)
- [ ] `aria-describedby` for form fields with error messages
- [ ] Focus visible: `focus-visible:ring-2 ring-ring ring-offset-2` on all interactive elements
- [ ] Keyboard navigation:
  - Tab order follows visual layout
  - Sidebar items navigable via arrow keys
  - Data table rows selectable via keyboard
  - Dialogs trap focus
  - Escape closes dialogs/sheets
- [ ] Skip-to-content link (hidden, visible on focus)
- [ ] `role="alert"` on toast notifications
- [ ] Test with VoiceOver/NVDA (manual spot-check)

## Deliverables
- All pages have proper metadata
- Bundle size optimized (dynamic imports, tree shaking)
- WCAG AA compliance verified
- Keyboard navigation functional throughout
- robots.txt and sitemap.xml generated

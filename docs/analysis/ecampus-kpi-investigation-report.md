# eCampus KPI Investigation Report

**Platform**: https://ecampus.kpi.ua/
**Investigation Date**: 2026-03-29
**Investigator**: Claude Code (Playwright-based analysis)
**Status**: Completed — public surface fully explored

---

## 1. Scope and Access Boundaries

### Accessible Pages
| Page | URL | Status |
|---|---|---|
| Login (UK) | `/uk/login` | ✅ Full access |
| Login (EN) | `/en/login` | ✅ Full access |
| Password Reset | `/en/password-reset` | ✅ Full access |
| Curator Search | `/en/curator-search` | ✅ Full access (with live search) |
| KPI ID SSO | `auth.kpi.ua` | ✅ External SSO page (observed) |
| PWA Manifest | `/site.webmanifest` | ✅ Read-only |

### Access Boundaries
- All routes beyond login/curator-search/password-reset redirect to `/en/login`
- No 404 page exposed — all unknown routes silently redirect to login
- No public dashboard, schedule, or content pages accessible without authentication
- KPI ID SSO page is a separate domain (`auth.kpi.ua`) with its own design system

---

## 2. Crawl Map / Visited Areas

```
ecampus.kpi.ua/
├── /uk/login              — Ukrainian login page
├── /en/login              — English login page (primary analysis target)
├── /en/password-reset     — Password reset flow
├── /en/curator-search     — Public group curator lookup
├── /en/dashboard          — REDIRECTS → /en/login
├── /en/schedule           — REDIRECTS → /en/login
├── /en/nonexistent        — REDIRECTS → /en/login (no 404 page)
└── /site.webmanifest      — PWA manifest

auth.kpi.ua/
└── /uk/?appId=...         — External SSO login (KPI ID)
```

---

## 3. System-Level Observations

### Technology Stack (Observed)
| Component | Technology | Evidence |
|---|---|---|
| **Frontend Framework** | Next.js (App Router or Pages) | `_next/static/chunks/` in script URLs, CSS chunk hashing |
| **CSS Framework** | Tailwind CSS | Class patterns: `bg-basic-blue`, `rounded-md`, `flex`, `gap-2`, etc. |
| **Font** | Exo 2 (Google Font, WOFF2) | Two weight variants preloaded, `font-family: "Exo 2"` |
| **Image CDN** | cdn.cloud.kpi.ua | Carousel images served from `https://cdn.cloud.kpi.ua/public/ecampus.kpi.ua/` |
| **Analytics** | Google Tag Manager | `G-BB6BXNRSZN` tracking ID preloaded |
| **PWA** | Web App Manifest | `display: standalone`, maskable icons (192x192, 512x512) |
| **i18n** | URL-based locale (`/uk/`, `/en/`) | Client-side i18next on KPI ID; route-based on eCampus |
| **Auth SSO** | KPI ID (auth.kpi.ua) | OAuth/OIDC-style with appId parameter |
| **SSO Providers** | Diia (gov), Google | Observed on auth.kpi.ua page |

### Design Token Analysis
| Token | Value | Usage |
|---|---|---|
| **Brand Navy** | `#1c396e` | PWA background, logo color |
| **Brand Blue** | `rgb(16, 98, 163)` / `bg-basic-blue` | Links, primary CTA button |
| **Text Primary** | `rgb(15, 23, 42)` / slate-900 | Body text, headings |
| **Text Muted** | `rgb(128, 129, 145)` | Secondary labels |
| **Text Neutral** | `text-neutral-600`, `text-neutral-800` | Form labels, descriptions |
| **Background** | `rgb(255, 255, 255)` | Page background |
| **Border** | `rgb(226, 232, 240)` / slate-200 | Input borders |
| **Border Radius - Input** | `8px` / `rounded-lg` | Form inputs |
| **Border Radius - Button** | `6px` / `rounded-md` | Buttons |
| **Border Radius - Card** | `rounded-xl` | Cards and panels |
| **Font Size - Body** | `14px` | Base body text |
| **Font Size - H2** | `36px` / `text-3xl`, weight 600 | Page headings |
| **Font Size - Button** | `text-lg` (18px) | Primary CTA |

---

## 4. Component Inventory

### 4.1 Login Form Component
**Location**: `/en/login`
**Structure**:
- Floating label pattern (label above input)
- Two text inputs with distinct labels: "Email address or username" / "Password"
- Password visibility toggle button (eye icon)
- "Remember me" checkbox (checked by default, custom styled with brand color)
- "Forgot my password" link aligned right on same row as Remember me
- Full-width primary CTA button ("Sign in") — `bg-basic-blue`, white text, `text-lg`
- Divider with "or" text between primary and secondary auth
- "Sign in with KPI ID" — secondary outlined button with institutional icon
- Three support action links at bottom: User support, Find curator, Support chat

**Key CSS**: `bg-basic-blue text-primary-foreground hover:bg-brand-700 active:bg-basic-blue active:border-brand-900 p-[16px] text-lg w-full`

### 4.2 Institutional Photo Carousel
**Location**: Right half of login page (desktop only)
**Structure**:
- Vertical carousel taking full right half of viewport
- 10 institutional photos from CDN (`cdn.cloud.kpi.ua`)
- Each slide: full-bleed image + gradient overlay (bottom-to-top black 80% → transparent)
- Caption overlay at bottom: building name (h6), credit line ("by @kpi_look" linking to Instagram)
- Previous/Next navigation buttons (bottom-right corner)
- Hidden on mobile/tablet (responsive breakpoint at ~1024px)

### 4.3 Search Component (Curator Search)
**Location**: `/en/curator-search`
**Structure**:
- Search icon (magnifying glass) + text input ("Group name")
- Real-time results as user types (no submit button required)
- Empty state: centered muted text "Enter your group name above to find a curator."
- Results list: scrollable container within a card (rounded-xl, white bg)
- Each result row: Group code (bold), Curator name (link with external icon to intellect.kpi.ua), Department name (muted)
- "Невідомо" (Unknown) shown when curator is not assigned — links to `#`
- Back button with left arrow at top of page

### 4.4 Password Reset Form
**Location**: `/en/password-reset`
**Structure**:
- Back button (top-left, same as curator search)
- Heading "Reset your password"
- Descriptive paragraph
- Single input: "Username or email address"
- Submit button: "Reset password" — **disabled** by default until input has value
- Same split-layout with carousel on desktop

### 4.5 Header/Banner Component
**Structure**:
- Logo (left): "Electronic Campus" with shield icon + "beta" badge
- Language switcher (right): text link with flag icon (UA flag for Ukrainian, UK flag for English)
- Minimal height, no nav items (pre-auth state)
- White background, no border or shadow

### 4.6 Footer Component
**Structure**:
- Left-aligned text: "All rights reserved. © 2026 Igor Sikorsky Kyiv Polytechnic Institute"
- Second line: "Developer: Design bureau of information systems" (linked)
- Minimal styling, dark text on white background
- Pinned to bottom of left panel

### 4.7 Support Action Links
**Location**: Below login form
**Structure**:
- Three items in horizontal grid (`grid-cols-2` wrapping to 3)
- Each: Icon (outlined, brand-blue) + label text below
- Links: User support (→ Google Form), Find curator (→ internal), Support chat (→ WhatsApp group)
- Icon style: outlined/stroke, consistent size

---

## 5. Best Practices Worth Integrating

### 5.1 Split-Layout Login with Institutional Carousel
**Where**: Login page (desktop)
**Problem Solved**: Login pages feel utilitarian and miss opportunities for institutional branding
**Why It Works**:
- Left panel focuses on task (login form) — clean, distraction-free
- Right panel provides emotional connection through campus photography
- Photography carousel adds life without cluttering the form
- Gradient overlay ensures captions are readable regardless of image content
- Mobile gracefully degrades to form-only (no wasted space)

**Reuse Recommendation**: **Adopt with adaptation**
- **Target Feature**: Our login/magic-link page
- **Adaptation**: Use FICE/university building photos instead of KPI carousel; could use static hero image rather than carousel to reduce complexity
- **User Value**: Institutional trust + visual warmth on first touch
- **Complexity**: Low — CSS grid split layout, responsive breakpoint to hide right panel
- **Risk**: Need high-quality photos; poor photos would hurt more than help

### 5.2 Real-Time Search with Inline Results
**Where**: Curator search page
**Problem Solved**: Users need to find information quickly without pagination overhead
**Why It Works**:
- No submit button friction — results appear as you type
- Clean empty state with clear instruction text
- Each result row has structured data hierarchy: primary identifier (bold) → linked person → department (muted)
- External links clearly marked with link icon
- Scrollable result container prevents page layout shift

**Reuse Recommendation**: **Adopt directly**
- **Target Feature**: Applicant search, operator lookup, group/specialty search
- **User Value**: Fast discovery, reduced clicks
- **Complexity**: Medium — needs debounced API call, result component
- **Risk**: Must handle large result sets gracefully; needs loading state for slow responses

### 5.3 Disabled Button Until Valid Input
**Where**: Password reset page
**Problem Solved**: Prevents empty form submissions, gives visual feedback on required state
**Why It Works**:
- Button starts `disabled` (grayed out with `opacity-40`)
- Visual clarity — user understands they need to provide input
- No wasted server round-trip for empty submissions

**Reuse Recommendation**: **Adopt directly**
- **Target Feature**: All forms — login, onboarding steps, application submission
- **User Value**: Clear submission readiness indicator
- **Complexity**: Low — Zod validation + disabled prop binding
- **Risk**: None — standard pattern

### 5.4 Consistent "Back" Navigation Pattern
**Where**: Curator search, password reset
**Problem Solved**: User orientation after navigating away from main flow
**Why It Works**:
- Top-left "< Back" link with chevron icon
- Consistent placement across all sub-pages
- Links to root (`/en`) — predictable destination
- Not browser-back dependent — explicit navigation

**Reuse Recommendation**: **Adopt directly**
- **Target Feature**: All sub-pages within our authenticated flows
- **User Value**: Confident navigation, reduced disorientation
- **Complexity**: Minimal — reusable component
- **Risk**: None

### 5.5 Pre-Auth Support Access Pattern
**Where**: Login page footer section
**Problem Solved**: Users locked out cannot access help if help is behind login
**Why It Works**:
- Three distinct support channels (form, search tool, chat) available WITHOUT authentication
- Icon + label format is scannable
- External links (Google Form, WhatsApp) are pragmatic and work immediately

**Reuse Recommendation**: **Adopt with adaptation**
- **Target Feature**: Our login/magic-link page
- **Adaptation**: Replace with our support channels; ensure at least one immediate-response channel (chat) and one self-service tool
- **User Value**: Users can get help when they most need it — when they can't log in
- **Complexity**: Low — static links with icons
- **Risk**: Support channels must actually be monitored

### 5.6 Language Switcher with Flag Icon
**Where**: Header (all pages)
**Problem Solved**: Bilingual users need instant language switching
**Why It Works**:
- Flag icon provides visual recognition (no need to read text)
- Text label provides clarity ("Switch to English" / "Перейти на українську")
- URL-based localization (`/uk/` ↔ `/en/`) ensures bookmarkable localized pages
- Persistent across all pages

**Reuse Recommendation**: **Adopt with adaptation**
- **Target Feature**: Our global header
- **Adaptation**: We may need UK/EN switching; use same URL prefix pattern
- **Complexity**: Medium — requires i18n infrastructure (already using Next.js)
- **Risk**: Translation completeness must be maintained

### 5.7 PWA Configuration with Institutional Branding
**Where**: `site.webmanifest`
**Problem Solved**: Mobile users want app-like experience
**Why It Works**:
- `display: standalone` — launches like native app
- Brand navy (`#1c396e`) as background color — institutional color on splash screen
- Maskable icons in both sizes (192, 512)
- Short name "Е-Кампус" for home screen

**Reuse Recommendation**: **Adopt directly**
- **Target Feature**: Our PWA manifest
- **User Value**: Professional mobile experience, installable
- **Complexity**: Low — manifest + icons
- **Risk**: None

---

## 6. Patterns to Adapt Carefully

### 6.1 SSO with Multiple Identity Providers
**Where**: KPI ID (auth.kpi.ua)
**Observation**: auth.kpi.ua offers credentials + Diia (government ID) + Google sign-in
**Why Careful**:
- Multi-provider SSO adds UX complexity
- Each provider has different trust levels and data returns
- "Sign in with KPI ID" button on eCampus adds an extra redirect hop

**Adaptation Notes**:
- Our magic-link auth is simpler and more appropriate for our use case
- If we ever add KPI ID integration, treat it as a secondary option (below magic link)
- Do NOT copy the credentials + SSO + Google triple pattern — it's institutional necessity, not UX ideal

### 6.2 "Remember Me" Checked by Default
**Where**: Login form
**Observation**: Checkbox is pre-checked
**Why Careful**:
- Convenient for personal devices
- Risky on shared/public computers (common in university settings)
- GDPR/privacy considerations

**Adaptation Notes**: For our system, default-off is safer given shared computer usage in admissions offices

### 6.3 CDN-Hosted Carousel Images
**Where**: Login page right panel
**Observation**: Images served from `cdn.cloud.kpi.ua`, credited to Instagram photographer
**Why Careful**:
- CDN dependency — if CDN is down, login page right panel breaks
- Image licensing/attribution requirements
- 10 images loaded (lazy or not) adds to page weight

**Adaptation Notes**: If we adopt this pattern, use 3-4 optimized images max with Next.js Image optimization, not an external CDN

---

## 7. Patterns to Avoid

### 7.1 Silent Redirect for All Unknown Routes
**Observed**: Any non-existent URL (e.g., `/en/nonexistent-page-test`) silently redirects to login
**Problem**:
- No 404 page means users can never tell if they mistyped a URL vs. need to log in
- POST-login redirect after typo-correction is impossible
- Debugging URL issues becomes harder for support teams

**Our Approach**: Implement proper 404 page for genuinely non-existent routes; only redirect to login for authenticated-required routes

### 7.2 No HTML `lang` Attribute
**Observed**: `document.documentElement.lang` is empty string on `/en/login`
**Problem**:
- Screen readers cannot determine page language
- WCAG 3.1.1 failure (Level A)
- Browser auto-translation features may not work correctly

**Our Approach**: Always set `<html lang="uk">` or `<html lang="en">` based on locale

### 7.3 `maximum-scale=1` in Viewport Meta
**Observed**: `<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">`
**Problem**:
- Prevents pinch-to-zoom on mobile
- WCAG 1.4.4 failure (Level AA) — users with low vision cannot zoom
- iOS Safari may ignore this, but Android Chrome enforces it

**Our Approach**: Never use `maximum-scale=1` or `user-scalable=no`

### 7.4 Carousel Without Auto-Play Controls or Indicators
**Observed**: Photo carousel has prev/next buttons but no dot indicators, no auto-play pause control, no progress indication
**Problem**:
- Users can't tell how many slides exist or which slide they're on
- If auto-play were added (not currently observed), no pause mechanism exists
- Accessibility: no ARIA live region announcements for slide changes

**Our Approach**: If using carousel, include dot indicators and ensure keyboard/screen-reader accessibility

### 7.5 External Form for User Support
**Observed**: "User support" links to Google Forms
**Problem**:
- User leaves the application entirely
- No tracking of support request status
- Google Forms has no SLA or integration with internal systems
- Feels unprofessional for a production system

**Our Approach**: Build integrated support/feedback mechanism or at minimum link to a proper help desk

### 7.6 "Невідомо" (Unknown) as Curator Result
**Observed**: Some groups show "Невідомо" with a link to `#` for curator
**Problem**:
- Dead link (`href="#"`) is a broken interaction
- Users expect a link to go somewhere; clicking `#` scrolls to top or does nothing
- Should either hide the link or show "Not assigned" as plain text

**Our Approach**: Never render links that go nowhere; show "Not assigned" as plain text with appropriate styling

### 7.7 No Form Validation Feedback Visible
**Observed**: Login form has no visible validation states (error borders, inline messages)
**Problem**: Cannot determine from public observation whether validation feedback exists post-submission, but no `aria-describedby`, no error containers visible in DOM pre-submission

**Our Approach**: Always include inline validation with `aria-describedby` linking error messages to inputs

---

## 8. Image and Visual Asset Catalog

### Carousel Photography
| # | Image URL | Alt Text | Semantic Role |
|---|---|---|---|
| 1 | `cdn.cloud.kpi.ua/public/ecampus.kpi.ua/carousel/img1.jpg` | Корпус № 1 КПІ (1080x810) | Institutional identity — main campus building |
| 2 | `cdn.cloud.kpi.ua/public/ecampus.kpi.ua/carousel/img2.jpg` | Корпус № 20 КПІ (816x990) | Institutional identity — building 20 |
| 3 | `cdn.cloud.kpi.ua/public/ecampus.kpi.ua/carousel/img3.jpg` | Library (720x1280) | Institutional identity — Denysenko library |
| 4-10 | `cdn.cloud.kpi.ua/public/ecampus.kpi.ua/carousel/img{4-10}.jpg` | Various campus buildings | Rotating institutional imagery |

**Labeling Quality**: Alt text is descriptive and specific (building name + institution name). All images credited to `@kpi_look` Instagram.

### Branding Assets
| Asset | URL | Purpose |
|---|---|---|
| Logo SVG | Embedded in page (not external URL) | Brand identity in header |
| Favicon PNG | `/favicon-48x48.png` | Browser tab icon |
| Favicon SVG | `/favicon.svg` | Scalable browser icon |
| Apple Touch Icon | `/apple-touch-icon.png` (180x180) | iOS home screen |
| PWA Icon | `/web-app-manifest-192x192.png` | Android home screen |
| PWA Icon Large | `/web-app-manifest-512x512.png` | Splash screen |

### Icon System
- **Type**: Inline SVG (10 SVGs in login page DOM)
- **Style**: Outlined/stroke style, monochrome (brand blue `#1062a3`)
- **Usage**: Eye icon (password toggle), chevron (back button), support icons (headset, star, chat bubble), carousel arrows, checkbox check mark
- **No icon library detected** — custom SVG set or custom icon font

---

## 9. Feature-Integration Recommendations

### Feature Integration Matrix

| # | Source Pattern | Our Target Feature | Reason to Adopt | Required Adaptations | Implementation Risk | Expected User Benefit |
|---|---|---|---|---|---|---|
| 1 | Split-layout login with photo panel | Magic link login page | Institutional branding + warm first impression | Use our own FICE photos; static hero instead of carousel; must work with magic-link flow (email input → check inbox) | Low | Trust, professionalism, brand recognition |
| 2 | Real-time search with inline results | Applicant search, specialty search, operator lookup | Fast discovery without pagination | Add debounce (300ms), loading spinner, "no results" state, limit to top 20 results | Medium | Reduced time-to-find, fewer clicks |
| 3 | Disabled submit until valid | All forms (onboarding, applications) | Prevent empty submissions | Already partially implemented via Zod + RHF; ensure visual disabled state matches our design tokens | Low | Clear readiness indicator |
| 4 | Consistent back navigation | Onboarding flow, document upload, profile editing | User orientation in multi-step flows | Use consistent top-left placement; ensure destination is predictable (parent route, not browser back) | Low | Confident navigation |
| 5 | Pre-auth support access | Login page | Help for locked-out users | Add FAQ link + support email; consider in-page help modal rather than external links | Low | Users can self-resolve access issues |
| 6 | Language switcher with flag | Global header | Bilingual support (UK/EN) | URL-prefix approach (`/uk/`, `/en/`); integrate with Next.js i18n routing; ensure all UI text is translatable | Medium-High | Accessibility for non-Ukrainian speakers |
| 7 | PWA manifest with branding | Our manifest.json | Installable mobile app experience | Set our brand colors, create maskable icons, configure standalone display | Low | Native-feel mobile experience |
| 8 | Font choice (Exo 2) | **Do not copy** | Their font; we use SF Pro | N/A | N/A | N/A |
| 9 | Tailwind design tokens | Our Tailwind config | Consistent naming convention (`bg-basic-blue`, `text-neutral-600`) | Map their token naming to our existing design system; useful as reference for token organization | Low | Maintainable, consistent UI |
| 10 | Custom checkbox styling | Our form components | Branded form elements | Use shadcn/ui Checkbox with our brand color; match the checked-with-brand-color pattern | Low | Polished, branded forms |

---

## 10. Risks, Assumptions, and Unknowns

### Risks
1. **Limited public surface**: Only 3 pages are publicly accessible. Dashboard, schedule, grades, and all functional pages are behind authentication. Our analysis is necessarily limited to login/auth UX patterns.
2. **"Beta" label**: The platform self-identifies as "beta" — patterns observed may not represent their final design decisions.
3. **No observable error states**: We could not trigger or observe form validation errors, toast notifications, loading states, or empty dashboard states without authentication.
4. **Carousel performance**: Loading 10 full-resolution images on the login page may impact initial load performance. We did not measure LCP/CLS.

### Assumptions
1. **ASSUMED**: The authenticated dashboard likely uses a sidebar navigation pattern common in academic management systems — NOT verified.
2. **ASSUMED**: The system uses JWT or session-cookie auth based on the "Remember me" checkbox — NOT verified from public surface.
3. **ASSUMED**: The curator search API is a simple GET endpoint with query parameter — verified by observing real-time results on keystroke.
4. **VERIFIED**: The system is built with Next.js + Tailwind CSS — confirmed from script URLs and class naming.
5. **VERIFIED**: i18n is URL-based (`/uk/`, `/en/`) — confirmed from link hrefs and URL behavior.

### Unknowns
1. Dashboard layout and navigation structure
2. Data table patterns (grades, schedule, etc.)
3. Form complexity for multi-step workflows
4. Notification/alert system design
5. Error page designs (404, 500, maintenance)
6. Dark mode support (not observed)
7. Accessibility compliance level (several A-level issues already noted)
8. Real-time features (WebSocket, SSE) usage

---

## Appendix: Screenshots Index

| # | File | Description |
|---|---|---|
| 1 | `screenshots/ecampus/01-login-page.png` | Ukrainian login page (desktop) |
| 2 | `screenshots/ecampus/02-login-page-en.png` | English login page (desktop) |
| 3 | `screenshots/ecampus/03-curator-search.png` | Curator search empty state |
| 4 | `screenshots/ecampus/04-curator-search-results.png` | Curator search with results |
| 5 | `screenshots/ecampus/05-password-reset.png` | Password reset page |
| 6 | `screenshots/ecampus/06-login-mobile.png` | Login page (mobile 390px) |
| 7 | `screenshots/ecampus/07-login-tablet.png` | Login page (tablet 768px) |
| 8 | `screenshots/ecampus/08-kpi-id-sso.png` | KPI ID SSO external page |
| 9 | `screenshots/ecampus/09-carousel-slide-2.png` | Login with carousel slide 2 |

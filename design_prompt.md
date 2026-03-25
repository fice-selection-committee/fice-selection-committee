# Design Prompt: Admissions Management System (Lavender Main Theme)

## 1. Overview

We are building a comprehensive **Admissions Management System** for a university. The application will support multiple user roles (Applicant, Operator, Responsible for Contracts, Responsible for Group Distribution, Secretary/Deputy, Administrator) with distinct dashboards and workflows.

The design must be built using **Shadcn UI**, **Tailwind CSS**, **React**, and **Next.js**. The final implementation will be a responsive web application with a focus on usability, accessibility, and modern aesthetics. The color palette is **lavender‑centric**, with a minimal set of supporting colors. Typography is **Poppins**.

**Goal:** Create a high‑fidelity design system and component library (Figma/Sketch/XD) that includes all pages, components, and states required by the functional specifications. The design will serve as the blueprint for development.

---

## 2. Design System

### 2.1 Color Palette (Lavender Main)

We use a simple, cohesive palette where **lavender** is the primary accent. All colors are defined as CSS custom properties.

| Role        | Hex / HSL                   | Usage                                                                 |
|-------------|-----------------------------|-----------------------------------------------------------------------|
| Primary     | `#BC9BF3` (Lavender)        | Buttons, links, active states, icons, borders, focus rings            |
| Primary Dark| `#9B7BCF` (Darker lavender) | Hover states, selected items, stronger accents                        |
| Background  | `#FAF9FE` (Very light purple tint) | Main background (soft, warm white with a hint of lavender)     |
| Surface     | `#FFFFFF`                   | Cards, modals, sidebars, form backgrounds                             |
| Border      | `#E8E2F5` (Light lavender-gray) | Dividers, input borders                                            |
| Text Primary| `#2D2A4A` (Dark purple-blue) | Headings, body text                                                  |
| Text Secondary| `#7C7A9E` (Muted lavender) | Labels, hints, less important text                                   |
| Success     | `#8FC93A` (Soft green)      | Positive actions, completed statuses                                  |
| Error       | `#F25E5E` (Soft red)        | Destructive actions, errors, alerts                                   |
| Warning     | `#FFC857` (Warm yellow)     | Warnings, pending states                                              |

**Neutrals:**
- `#F5F3FE` – very light lavender for hover backgrounds
- `#E8E2F5` – default borders
- `#FFFFFF` – pure white for surfaces

**Semantic colors** (Success, Error, Warning) are used consistently across the app.

**Design principle:**  
Lavender is the hero – used for all primary interactive elements. The background is a whisper‑light purple‑tinted white to feel soft and cohesive. Text and borders are derived from the same family to maintain harmony. The palette is intentionally small to ensure visual consistency and reduce cognitive load.

### 2.2 Typography

- **Font Family:** Poppins (Regular, SemiBold, Bold)
- **Font Sizes:**  
  - h1: `2rem` (32px) – Bold  
  - h2: `1.5rem` (24px) – SemiBold  
  - h3: `1.25rem` (20px) – SemiBold  
  - Body: `0.875rem` (14px) – Regular  
  - Small: `0.75rem` (12px) – Regular  
  - Buttons: `0.875rem` (14px) – SemiBold

- **Line Height:** Headings 1.2, body 1.5
- **Text Color:** Primary `#2D2A4A`, secondary `#7C7A9E`

### 2.3 Spacing & Layout

- Use Tailwind’s default spacing scale (4px increments)
- Container max-width: `1440px` (centered)
- Sidebar width: `260px` (collapsible to `80px`)
- Content padding: `24px` (desktop), `16px` (mobile)

### 2.4 Shadows & Borders

- **Shadow:** `0 1px 3px rgba(0,0,0,0.03), 0 1px 2px rgba(0,0,0,0.02)`
- **Border radius:**  
  - Buttons: `0.5rem` (8px)  
  - Cards: `0.75rem` (12px)  
  - Inputs: `0.5rem` (8px)  
  - Modals: `1rem` (16px)

### 2.5 Icons

- Use **lucide-react** icons exclusively
- Icon size: `16px`–`20px` inline, `24px` buttons, `32px` large actions
- Color: primary lavender for active, text secondary for disabled

---

## 3. Layout Components

### 3.1 Root Layout

- **Sidebar:** background `#FAF9FE`, border right `#E8E2F5`. Logo at top, navigation, user avatar at bottom. Active item background `#BC9BF3` with white text; hover background `#E8E2F5`.
- **Header:** background white, bottom border `#E8E2F5`. Page title left, notifications and user menu right.
- **Main Content:** background `#FAF9FE`. Cards and panels use white surfaces.

### 3.2 Loading & Skeleton States

- Use Shadcn skeleton components with a lavender‑tinted gray (`#E8E2F5`) and subtle pulse.

### 3.3 Empty States

- Simple SVG illustration or Lucide icon in lavender, friendly copy, primary lavender button.

### 3.4 Error Boundaries

- Friendly message with retry button, using error red (`#F25E5E`) for emphasis.

---

## 4. Pages by Role

(Detailed descriptions follow the same structure as the original prompt, but all visual details now reflect the lavender‑centric palette.)

### 4.1 Public / Login

- Card background white (`#FFFFFF`), border `#E8E2F5`
- Primary button: lavender (`#BC9BF3`) background, white text, hover `#9B7BCF`
- Error messages: `#F25E5E`
- Input focus ring: lavender

### 4.2 Applicant Pages

- **Dashboard:** Status timeline steps: completed lavender, current light lavender (`#E8E2F5`), future gray.
- **Documents:** Table rows hover background `#FAF9FE`. Upload modal drag & drop border lavender.
- **Application:** Submit button disabled state light lavender-gray (`#E8E2F5`), enabled `#BC9BF3`.
- **Notifications:** Unread items background `#FFC857` (warning) with dark text.

### 4.3 Operator Pages

- **Assigned Applications:** Status badges:
  - Assigned: `#FFC857` (warning)
  - In Progress: `#BC9BF3` (lavender)
  - Accepted: `#8FC93A` (success green)
  - Returned: `#F25E5E` (error)
- **Application Detail:** Split view left panel `#FAF9FE`, right panel white. Checklist checkboxes lavender accent. Return modal red warning.

### 4.4 Responsible for Contracts

- Contract Registration modal: suggested number displayed in a box with lavender background and white text.

### 4.5 Responsible for Group Distribution

- Protocol viewer: group names in lavender (`#BC9BF3`), monospace for data.

### 4.6 Secretary / Deputy

- Order Generation: form fields focus ring lavender. Candidate list checkboxes lavender accent. Generate button lavender.

### 4.7 Administrator

- User Management: role badges: Operator → lavender light, Admin → lavender dark.
- Audit Logs: expandable rows with metadata background `#FAF9FE`.
- Settings: toggle switches lavender active.

---

## 5. Common Components & Interactions

### 5.1 Toast Notifications

- White background, subtle shadow
- Success: green (`#8FC93A`) icon and border
- Error: red (`#F25E5E`) icon and border
- Info: lavender (`#BC9BF3`) icon and border
- Position top‑right, auto‑dismiss 5s

### 5.2 Modals

- Overlay: `rgba(0,0,0,0.2)`
- Content background white, border radius `1rem`
- Buttons: primary lavender, secondary outline lavender

### 5.3 Tooltips

- Background `#2D2A4A`, text white

### 5.4 Form Validation

- Error text `#F25E5E`, input border `#F25E5E`, focus ring lavender

### 5.5 File Upload

- Drag & drop: dashed border lavender, background `#FAF9FE`
- Progress bar: lavender gradient
- Success: green checkmark

### 5.6 Document Preview

- PDF viewer: lavender navigation controls
- Lightbox: lavender close button

### 5.7 Data Tables

- Header background `#F5F3FE`, text `#2D2A4A`
- Row hover background `#FAF9FE`
- Sort icons: lavender when active

---

## 6. User Experience (UX) Guidelines

- **Idempotency:** disable button on click, show spinner inside
- **Optimistic UI:** immediate feedback for accept/return
- **Status indicators:** colored badges + icons
- **Responsive:** collapsible sidebar on tablet, full‑width on mobile
- **Accessibility:** contrast ratios >4.5:1, focus outlines, ARIA labels, keyboard navigation

---

## 7. Anti‑Patterns / Restrictions in Design

To avoid generic, “AI‑slop” aesthetics:

- **No glassmorphism, heavy gradients, or large drop shadows** – keep surfaces flat and subtle.
- **No pill‑shaped buttons everywhere** – use 8px radius for most elements.
- **Color restraint** – use lavender only for primary actions; do not over‑saturate.
- **No all‑caps body text** – sentence case for buttons and titles.
- **Icon consistency** – all icons from lucide‑react.
- **Avoid default Shadcn look** – customize colors, radii, shadows.
- **Smooth transitions** (150ms), no abrupt animations.
- **Accessibility first** – no color‑only indicators, sufficient contrast.

---

## 8. Technical Notes (for Designer)

- Use CSS custom properties for colors (e.g., `--primary: #BC9BF3`).
- Provide design tokens that map to Tailwind config.
- All interactive elements must have hover, focus, disabled, active states.
- Deliverables: Figma file with pages, component library, user flows, responsive views, style guide, assets.

---

## 9. Deliverables

1. High‑fidelity mockups for all pages (login, applicant dashboard, operator view, contract registration, grouping, orders, admin).
2. Component library with all UI elements and states.
3. Annotated user flows for key journeys.
4. Responsive designs (tablet, desktop).
5. Design tokens (colors, typography, spacing, shadows).
6. Vector assets (icons, minimal illustrations).

---

**Final note:** The design should feel professional, clean, and trustworthy. Lavender is the unifying accent; use it thoughtfully. Keep the palette minimal and the UI focused on functionality.
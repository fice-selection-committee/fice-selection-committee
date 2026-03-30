# Phase 4: Login Page Redesign — Form Upgrade & Check-Email State

## Objective
Transform the login page from a bare shadcn Card into a warm, trust-building entry point. Redesign both the email entry state and the "check your email" waiting state. Keep all existing magic-link logic unchanged.

**Depends on**: Phase 1 (CSS tokens, animations), Phase 2 (auth layout provides split-panel)

---

## Current State

### File: `client/web/src/components/auth/magic-link-form.tsx`
- Wrapped in shadcn `Card` with `CardHeader` + `CardContent`
- Email entry: `text-xl` title, email input, submit button, helper text
- Check-email state: Mail icon in `bg-primary/10` circle, email display, resend button, change-email button
- All business logic (cooldown, error handling, API calls) is correct and stays unchanged

---

## Step 4.1: Email Entry State Redesign

### File: `client/web/src/components/auth/magic-link-form.tsx`

Replace the Card-wrapped email entry form with:

```tsx
// Replace the return block after the sentEmail check (lines 125-161)

return (
  <div className="space-y-8">
    {/* Heading section */}
    <div className="text-center space-y-2">
      <h2 className="text-2xl font-bold tracking-tight">Вхід до системи</h2>
      <p className="text-base text-muted-foreground leading-relaxed">
        Введіть електронну пошту для входу або реєстрації
      </p>
    </div>

    {/* Form */}
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        <FormField
          control={form.control}
          name="email"
          render={({ field }) => (
            <FormItem>
              <FormLabel className="text-sm font-medium">Електронна пошта</FormLabel>
              <FormControl>
                <Input
                  type="email"
                  placeholder="email@example.com"
                  autoComplete="email"
                  className="h-12 rounded-xl text-base"
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <LoadingButton
          type="submit"
          className="w-full h-12 rounded-xl text-base font-semibold"
          size="lg"
          loading={isSubmitting}
        >
          Отримати посилання для входу
        </LoadingButton>
      </form>
    </Form>

    <p className="text-center text-sm text-muted-foreground">
      Якщо у вас ще немає акаунту, він буде створений автоматично
    </p>
  </div>
);
```

### Key Changes
- **Removed**: `Card`, `CardHeader`, `CardContent` wrappers — auth layout provides the container
- **Heading**: `text-2xl font-bold tracking-tight` (was `text-xl`)
- **Subtitle**: Added descriptive `text-base` subtitle
- **Input**: `h-12 rounded-xl text-base` (48px height, 12px radius, 16px font — prevents iOS zoom)
- **Button**: `h-12 rounded-xl text-base font-semibold` (48px, matches input height)
- **Spacing**: `space-y-8` between sections, `space-y-6` within form

---

## Step 4.2: Check-Email State Redesign

### File: `client/web/src/components/auth/magic-link-form.tsx`

Replace the Card-wrapped check-email state (lines 91-123):

```tsx
if (sentEmail) {
  return (
    <div className="space-y-8">
      {/* Animated mail icon with pulse ring */}
      <div className="flex justify-center">
        <div className="relative">
          <div className="rounded-full bg-primary/10 p-5 animate-[pulse-ring_2s_ease-out_infinite] motion-reduce:animate-none">
            <Mail className="h-8 w-8 text-primary" />
          </div>
        </div>
      </div>

      {/* Text content */}
      <div className="text-center space-y-4">
        <h2 className="text-2xl font-bold tracking-tight">Перевірте пошту</h2>
        <div>
          <p className="text-base text-muted-foreground">Ми надіслали посилання для входу на</p>
          <span className="mt-2 inline-block rounded-full bg-primary/10 px-4 py-1.5 text-sm font-medium text-foreground">
            {sentEmail}
          </span>
        </div>
        <p className="text-sm text-muted-foreground">
          Посилання дійсне 15 хвилин. Перевірте також папку «Спам».
        </p>
      </div>

      {/* Actions */}
      <div className="space-y-3">
        <Button
          variant="outline"
          onClick={handleResend}
          disabled={isSubmitting || cooldown > 0}
          className="w-full h-11 rounded-xl"
        >
          {cooldown > 0 ? `Надіслати повторно (${cooldown}с)` : "Надіслати повторно"}
        </Button>
        <Button
          variant="ghost"
          onClick={() => setSentEmail(null)}
          className="w-full h-10 text-sm"
        >
          Змінити email
        </Button>
      </div>
    </div>
  );
}
```

### Key Changes
- **Removed**: Card wrapper
- **Mail icon**: Larger (p-5 instead of p-4), with `pulse-ring` animation from Phase 1
- **Email display**: Shown in a `rounded-full bg-primary/10` pill/chip (not just bold text)
- **Heading**: `text-2xl font-bold tracking-tight` (was `text-xl`)
- **Spam hint**: Added "Перевірте також папку «Спам»" — reduces anxiety
- **Button sizing**: Resend at `h-11 rounded-xl` (secondary), change email at `h-10` (tertiary)
- **Pulse animation**: On the mail icon container, subtle ring expanding outward (2s loop)
  - Communicates "waiting/alive" state
  - Suppressed by `prefers-reduced-motion`

---

## Step 4.3: Loading Button in Login Context

The `LoadingButton` already handles the loading state well. Ensure the `disabled` state is visually distinct on the new larger button:

### File: `client/web/src/components/ui/loading-button.tsx`

No changes to logic. The existing implementation handles:
- Width preservation during loading (prevents layout shift)
- `aria-busy` for screen readers
- Fade-in spinner animation
- Disabled state

The `h-12 rounded-xl text-base font-semibold` classes are applied at the call site, not in the component.

---

## Step 4.4: Page Wrapper Update

### File: `client/web/src/app/(auth)/login/page.tsx`

No changes needed — the page just renders `<MagicLinkForm />` inside Suspense. The auth layout provides the split-panel container.

---

## Step 4.5: Entrance Animation

Add staggered entrance to the magic-link-form component. Wrap the content sections:

```tsx
// In the email entry return block, add animation to each section:
<div className="space-y-8">
  <div
    className="text-center space-y-2 animate-[stagger-in_0.5s_ease-out_both] motion-reduce:animate-none"
    style={{ animationDelay: "0ms" }}
  >
    {/* heading + subtitle */}
  </div>

  <div
    className="animate-[stagger-in_0.5s_ease-out_both] motion-reduce:animate-none"
    style={{ animationDelay: "80ms" }}
  >
    {/* form */}
  </div>

  <p
    className="... animate-[stagger-in_0.5s_ease-out_both] motion-reduce:animate-none"
    style={{ animationDelay: "160ms" }}
  >
    {/* helper text */}
  </p>
</div>
```

Same pattern for the check-email state — icon appears first, then text, then actions.

---

## Design Specifications

### Email Entry State
| Element | Spec |
|---------|------|
| Heading | text-2xl (24px), font-bold, tracking-tight |
| Subtitle | text-base (16px), text-muted-foreground, leading-relaxed |
| Input | h-12 (48px), rounded-xl (12px), text-base (16px) |
| Submit button | h-12 (48px), rounded-xl, text-base, font-semibold, w-full |
| Helper text | text-sm (14px), text-muted-foreground, centered |
| Section spacing | space-y-8 (32px) |
| Form internal spacing | space-y-6 (24px) |

### Check-Email State
| Element | Spec |
|---------|------|
| Icon container | rounded-full, bg-primary/10, p-5, pulse-ring animation |
| Icon | h-8 w-8, text-primary |
| Email chip | rounded-full, bg-primary/10, px-4 py-1.5, text-sm font-medium |
| Resend button | h-11 (44px), rounded-xl, variant="outline", w-full |
| Change email button | h-10 (40px), variant="ghost", text-sm, w-full |

---

## Verification Checklist
- [ ] Email entry: no Card visible, content fills right panel naturally
- [ ] Input height is 48px, rounded corners 12px
- [ ] Button height matches input (48px)
- [ ] Input text is 16px (no iOS zoom on focus)
- [ ] Check-email state: pulse ring animates on mail icon
- [ ] Check-email state: email shown in pill/chip
- [ ] Reduced motion: no pulse, no stagger, content visible immediately
- [ ] Cooldown timer displays correctly (60s countdown)
- [ ] Resend works after cooldown expires
- [ ] "Змінити email" returns to email entry state
- [ ] Error toasts still fire (429, 403, generic)
- [ ] Dark mode: all elements properly themed
- [ ] Screen reader: form labels associated, busy state announced
- [ ] Playwright: enter email → submit → see "Перевірте пошту" screen

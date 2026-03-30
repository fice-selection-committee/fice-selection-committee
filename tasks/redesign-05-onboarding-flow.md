# Phase 5: Onboarding Flow Redesign — All Steps + Progress Indicator

## Objective
Transform onboarding from "filling out a form in a Card" to "guided journey with purpose-driven screens." Each step should feel like its own app screen with bottom-anchored CTA, generous spacing, and optional illustration. Keep all existing logic unchanged.

**Depends on**: Phase 1 (CSS tokens, animations), Phase 2 (auth layout)

---

## Step 5.1: Onboarding Page — Remove Card Wrapper, Full-Screen Steps

### File: `client/web/src/app/(auth)/onboarding/page.tsx`

**Major changes**:
1. Remove `Card`/`CardContent` wrappers around steps
2. Each step occupies the full right panel
3. Progress indicator: mobile progress bar at top, desktop vertical stepper in left panel or above content
4. Step transitions: more pronounced slide (24px translateX, not 4px)

```tsx
"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { DateOfBirthStep } from "@/components/onboarding/dob-step";
import { NameStep } from "@/components/onboarding/name-step";
import { OnboardingProgress } from "@/components/onboarding/progress";
import { TermsStep } from "@/components/onboarding/terms-step";
import { WelcomeScreen } from "@/components/onboarding/welcome-screen";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/hooks/use-auth";
import { getOnboardingStep, getStepNumber, type OnboardingStep } from "@/lib/auth/onboarding";
import { cn } from "@/lib/utils";
import type { User } from "@/types";

const STEP_LABELS: Record<OnboardingStep, string> = {
  terms: "Умови та політика",
  dob: "Дата народження",
  name: "Особисті дані",
  complete: "Завершено",
};

export default function OnboardingPage() {
  const router = useRouter();
  const { user, isLoading, updateUser } = useAuth();

  const currentStep = user ? getOnboardingStep(user) : "terms";
  const [displayedStep, setDisplayedStep] = useState(currentStep);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [announcement, setAnnouncement] = useState("");

  useEffect(() => {
    if (!isLoading && !user) {
      router.push("/login");
    }
  }, [isLoading, user, router]);

  useEffect(() => {
    if (currentStep !== displayedStep) {
      setIsTransitioning(true);
      const timer = setTimeout(() => {
        setDisplayedStep(currentStep);
        setIsTransitioning(false);
        setAnnouncement(`Перейшли на крок: ${STEP_LABELS[currentStep]}`);
        requestAnimationFrame(() => {
          const heading = document.querySelector("[data-step-heading]");
          if (heading instanceof HTMLElement) heading.focus();
        });
      }, 250); // slightly longer for new slide animation
      return () => clearTimeout(timer);
    }
  }, [currentStep, displayedStep]);

  const handleStepComplete = useCallback(
    (updatedUser: User) => {
      updateUser(updatedUser);
    },
    [updateUser],
  );

  if (isLoading || !user) {
    return (
      <div className="space-y-6 py-8">
        <Skeleton className="h-1 w-full rounded-full lg:hidden" />
        <Skeleton className="h-8 w-48 mx-auto" />
        <Skeleton className="h-5 w-64 mx-auto" />
        <Skeleton className="h-40 w-full rounded-xl" />
        <Skeleton className="h-12 w-full rounded-xl" />
      </div>
    );
  }

  if (currentStep === "complete") {
    return <WelcomeScreen />;
  }

  const currentNum = getStepNumber(currentStep);

  return (
    <div className="flex flex-col min-h-[calc(100vh-120px)] lg:min-h-0">
      {/* Announcement for screen readers */}
      <div role="status" aria-live="polite" aria-atomic="true" className="sr-only">
        {announcement}
      </div>

      {/* Mobile progress bar */}
      <div className="lg:hidden mb-6">
        <OnboardingProgress currentStep={currentStep} />
      </div>

      {/* Desktop progress — above content */}
      <div className="hidden lg:block mb-8">
        <OnboardingProgress currentStep={currentStep} />
      </div>

      {/* Step content with transition */}
      <div className="flex-1">
        <div
          className={cn(
            "transition-all duration-250 ease-out motion-reduce:transition-none",
            isTransitioning
              ? "opacity-0 -translate-x-6"
              : "opacity-100 translate-x-0",
          )}
        >
          {displayedStep === "terms" && <TermsStep onComplete={handleStepComplete} />}
          {displayedStep === "dob" && <DateOfBirthStep onComplete={handleStepComplete} />}
          {displayedStep === "name" && <NameStep onComplete={handleStepComplete} />}
        </div>
      </div>
    </div>
  );
}
```

### Key Changes
- **Removed**: `Card`/`CardContent` wrappers — steps own their full space
- **Loading skeleton**: Updated to match new layout (progress bar, larger elements)
- **Welcome screen**: No longer wrapped in Card — renders directly
- **Transition**: `-translate-x-6` (24px) instead of `translate-x-4` (16px) for more pronounced slide
- **Progress**: Separate mobile/desktop rendering in the page, not inside each step
- **Layout**: `min-h-[calc(100vh-120px)]` on mobile for full-screen feel
- **Timing**: 250ms transition (from 200ms) for smoother feel

---

## Step 5.2: Progress Component Redesign

### File: `client/web/src/components/onboarding/progress.tsx`

Replace with dual-mode progress: mobile thin bar + desktop horizontal steps.

```tsx
"use client";

import { Check } from "lucide-react";
import { memo } from "react";
import { getStepNumber, type OnboardingStep } from "@/lib/auth/onboarding";
import { cn } from "@/lib/utils";

const STEPS = [
  { key: "terms" as const, label: "Умови" },
  { key: "dob" as const, label: "Дата народження" },
  { key: "name" as const, label: "Особисті дані" },
];

interface OnboardingProgressProps {
  currentStep: OnboardingStep;
}

export const OnboardingProgress = memo(function OnboardingProgress({
  currentStep,
}: OnboardingProgressProps) {
  const currentNum = getStepNumber(currentStep);

  return (
    <>
      {/* Mobile: thin progress bar + step label */}
      <div className="lg:hidden" role="progressbar" aria-valuenow={currentNum} aria-valuemin={1} aria-valuemax={3} aria-label="Процес реєстрації">
        <div className="h-1 w-full rounded-full bg-muted overflow-hidden">
          <div
            className="h-full rounded-full bg-primary transition-all duration-500 ease-out"
            style={{ width: `${(currentNum / 3) * 100}%` }}
          />
        </div>
        <p className="mt-2 text-xs text-muted-foreground">
          Крок {currentNum} з 3 — {STEPS[currentNum - 1]?.label}
        </p>
      </div>

      {/* Desktop: horizontal step list */}
      <ol aria-label="Процес реєстрації" className="hidden lg:flex items-center gap-3">
        {STEPS.map((step, i) => {
          const stepNum = i + 1;
          const isCompleted = currentNum > stepNum;
          const isCurrent = currentNum === stepNum;

          return (
            <li
              key={step.key}
              className="flex items-center gap-3"
              aria-current={isCurrent ? "step" : undefined}
            >
              <div className="flex items-center gap-2">
                <div
                  className={cn(
                    "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-medium transition-colors",
                    isCompleted && "bg-primary text-primary-foreground",
                    isCurrent && "bg-primary text-primary-foreground ring-4 ring-primary/20",
                    !isCompleted && !isCurrent && "border-2 border-muted-foreground/30 text-muted-foreground",
                  )}
                  role="img"
                  aria-label={`Крок ${stepNum}: ${step.label} — ${isCompleted ? "завершено" : isCurrent ? "поточний" : "очікування"}`}
                >
                  {isCompleted ? <Check className="h-4 w-4" /> : stepNum}
                </div>
                <span
                  className={cn(
                    "text-sm font-medium",
                    isCurrent ? "text-foreground" : "text-muted-foreground/60",
                  )}
                >
                  {step.label}
                </span>
              </div>
              {i < STEPS.length - 1 && (
                <div
                  className={cn(
                    "h-px w-8 shrink-0",
                    isCompleted ? "bg-primary" : "bg-muted-foreground/20",
                  )}
                />
              )}
            </li>
          );
        })}
      </ol>
    </>
  );
});
```

### Key Changes
- **Mobile**: Replaced numbered circles with a thin progress bar (1px height, rounded, fills based on step)
  - Below bar: "Крок X з 3 — {label}" text
  - Uses `role="progressbar"` with proper ARIA attributes
  - Takes minimal vertical space
- **Desktop**: Horizontal step list (was vertical) — more compact for the right panel
  - Shorter labels ("Умови" instead of "Умови та політика")
  - Connecting lines between steps
- **Touch targets**: No longer an issue — progress is display-only, not interactive

---

## Step 5.3: Terms Step Redesign

### File: `client/web/src/components/onboarding/terms-step.tsx`

Key changes:
- Increase document row touch targets to `min-h-[64px] p-5`
- Heading: `text-2xl font-bold tracking-tight`
- Subtitle: `text-base` (was `text-sm`)
- Button: bottom-anchored on mobile with sticky positioning
- Checkbox area: `text-base` label (was `text-sm`)
- Section spacing: `space-y-8` (was `space-y-6`)

```tsx
// Key layout changes within the return block:

return (
  <div className="space-y-8">
    {/* Heading */}
    <div className="text-center">
      <h2 className="text-2xl font-bold tracking-tight" data-step-heading tabIndex={-1}>
        Умови та політика
      </h2>
      <p className="mt-3 text-base text-muted-foreground leading-relaxed">
        Ознайомтесь з документами та погодьтесь для продовження
      </p>
    </div>

    {/* Document rows — larger touch targets */}
    <div className="space-y-3">
      <button
        type="button"
        onClick={() => openModal("terms")}
        className="flex w-full items-center justify-between rounded-xl border p-5 min-h-[64px] text-left transition-colors hover:bg-muted/50 active:scale-[0.99] motion-reduce:active:scale-100"
      >
        {/* ... same icon/text/check content but with text-base for the label ... */}
        <div className="flex items-center gap-3">
          <FileText className="h-5 w-5 shrink-0 text-muted-foreground" />
          <div>
            <p className="text-base font-medium">Умови користування</p>
            <p className="text-sm text-muted-foreground">
              {viewedTerms ? "Переглянуто" : "Натисніть щоб прочитати"}
            </p>
          </div>
        </div>
        {/* ... check/chevron indicator ... */}
      </button>

      {/* Same pattern for privacy button */}
    </div>

    {/* Modals — unchanged */}

    {/* Checkbox — larger */}
    <div className="flex items-start gap-3">
      <Checkbox
        id="terms-accept"
        checked={accepted}
        disabled={!allViewed}
        onCheckedChange={(checked) => setAccepted(checked === true)}
        aria-required="true"
        aria-describedby={!allViewed ? "terms-help-text" : undefined}
        className="mt-0.5"
      />
      <div>
        <label
          htmlFor="terms-accept"
          className={cn(
            "text-base leading-snug select-none",
            allViewed ? "cursor-pointer" : "cursor-not-allowed text-muted-foreground",
          )}
        >
          Я погоджуюсь з умовами користування та політикою конфіденційності
        </label>
        {!allViewed && (
          <p id="terms-help-text" className="mt-1 text-sm text-muted-foreground/70">
            Спочатку ознайомтесь з обома документами
          </p>
        )}
      </div>
    </div>

    {/* CTA — sticky on mobile */}
    <div className="sticky bottom-0 bg-background pt-4 pb-safe lg:static lg:pt-0 lg:pb-0">
      <LoadingButton
        onClick={handleSubmit}
        disabled={!accepted}
        loading={isSubmitting}
        className="w-full h-12 rounded-xl text-base font-semibold"
        size="lg"
      >
        Далі
      </LoadingButton>
    </div>
  </div>
);
```

### Changes Summary
- Heading: `text-xl` → `text-2xl font-bold tracking-tight`
- Subtitle: `text-sm` → `text-base`, `mt-1` → `mt-3`
- Document rows: `p-4` → `p-5 min-h-[64px]`, label `text-sm` → `text-base`
- Checkbox label: `text-sm` → `text-base`
- CTA: `h-12 rounded-xl text-base font-semibold`, sticky on mobile
- Spacing: `space-y-6` → `space-y-8`
- Active state on rows: `active:scale-[0.99]` for touch feedback

---

## Step 5.4: Date of Birth Step Redesign

### File: `client/web/src/components/onboarding/dob-step.tsx`

Key changes:
- Heading: conversational "Коли ви народились?" (was "Дата народження")
- Select triggers: `h-12 rounded-xl text-base`
- CTA: sticky bottom, h-12 rounded-xl
- Spacing: `space-y-8`

```tsx
// Key layout changes:

return (
  <div className="space-y-8">
    <div className="text-center">
      <h2 className="text-2xl font-bold tracking-tight" data-step-heading tabIndex={-1}>
        Коли ви народились?
      </h2>
      <p className="mt-3 text-base text-muted-foreground leading-relaxed">
        Вкажіть дату народження для продовження
      </p>
    </div>

    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          {/* Each FormField with Select using className="h-12 rounded-xl text-base" on SelectTrigger */}
          <FormField
            control={form.control}
            name="day"
            render={({ field }) => (
              <FormItem>
                <FormLabel>День</FormLabel>
                <FormControl>
                  <Select
                    value={field.value?.toString()}
                    onValueChange={(v) => field.onChange(Number(v))}
                  >
                    <SelectTrigger className="h-12 w-full rounded-xl text-base">
                      <SelectValue placeholder="День" />
                    </SelectTrigger>
                    <SelectContent>
                      {DAYS.map((d) => (
                        <SelectItem key={d} value={d.toString()}>
                          {d}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          {/* ... month and year selects with same h-12 rounded-xl text-base ... */}
        </div>

        {form.formState.errors.root && (
          <p className="text-sm text-destructive text-center" role="alert">
            {form.formState.errors.root.message}
          </p>
        )}

        {/* Sticky CTA */}
        <div className="sticky bottom-0 bg-background pt-4 pb-safe lg:static lg:pt-0 lg:pb-0">
          <LoadingButton
            type="submit"
            loading={form.formState.isSubmitting}
            disabled={!form.formState.isValid}
            className="w-full h-12 rounded-xl text-base font-semibold"
            size="lg"
          >
            Далі
          </LoadingButton>
        </div>
      </form>
    </Form>
  </div>
);
```

### Changes Summary
- Heading: "Дата народження" → "Коли ви народились?" (conversational, reduces form anxiety — Wroblewski, 2008)
- SelectTrigger: added `h-12 rounded-xl text-base`
- Grid gap: `gap-3` → `gap-4`
- CTA: sticky bottom on mobile
- Spacing: `space-y-6` → `space-y-8`

---

## Step 5.5: Name Step Redesign

### File: `client/web/src/components/onboarding/name-step.tsx`

Key changes:
- Heading: conversational "Як вас звати?" (was "Особисті дані")
- All inputs: `h-12 rounded-xl text-base`
- Button text: "Завершити реєстрацію" (was "Зберегти") — signals finality
- CTA: sticky bottom on mobile

```tsx
// Key layout changes:

return (
  <div className="space-y-8">
    <div className="text-center">
      <h2 className="text-2xl font-bold tracking-tight" data-step-heading tabIndex={-1}>
        Як вас звати?
      </h2>
      <p className="mt-3 text-base text-muted-foreground leading-relaxed">
        Вкажіть ваше прізвище, ім&apos;я та по батькові
      </p>
    </div>

    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
        <FormField
          control={form.control}
          name="lastName"
          render={({ field }) => (
            <FormItem>
              <FormLabel>
                Прізвище <span aria-hidden="true" className="text-destructive">*</span>
              </FormLabel>
              <FormControl>
                <Input
                  placeholder="Іванов"
                  required
                  className="h-12 rounded-xl text-base"
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        {/* ... firstName and middleName with same h-12 rounded-xl text-base ... */}

        {/* Sticky CTA */}
        <div className="sticky bottom-0 bg-background pt-6 pb-safe lg:static lg:pt-2 lg:pb-0">
          <LoadingButton
            type="submit"
            loading={form.formState.isSubmitting}
            className="w-full h-12 rounded-xl text-base font-semibold"
            size="lg"
          >
            Завершити реєстрацію
          </LoadingButton>
        </div>
      </form>
    </Form>
  </div>
);
```

### Changes Summary
- Heading: "Особисті дані" → "Як вас звати?"
- Inputs: added `h-12 rounded-xl text-base`
- CTA text: "Зберегти" → "Завершити реєстрацію" (signals this is the last step)
- CTA: sticky bottom on mobile
- Spacing: `space-y-6` → `space-y-8` between sections

---

## Step 5.6: Welcome Screen Enhancement

### File: `client/web/src/components/onboarding/welcome-screen.tsx`

Key changes:
- Larger mascot (120px)
- Manual "Перейти до кабінету" button (accessibility: WCAG 2.2.1 — user control over timing)
- Longer timer for reduced-motion users (5s vs 2.5s)
- Role-specific welcome message

```tsx
"use client";

import { Check } from "lucide-react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/hooks/use-auth";
import { getRoleDefaultRoute } from "@/lib/auth/roles";

export function WelcomeScreen() {
  const router = useRouter();
  const { user } = useAuth();

  useEffect(() => {
    if (!user) return;
    const prefersReduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const delay = prefersReduced ? 5000 : 2500;
    const timer = setTimeout(() => {
      router.replace(getRoleDefaultRoute(user.role));
    }, delay);
    return () => clearTimeout(timer);
  }, [user, router]);

  if (!user) return null;

  const handleContinue = () => {
    router.replace(getRoleDefaultRoute(user.role));
  };

  return (
    <div className="flex flex-col items-center justify-center py-16 space-y-8">
      <div className="animate-[checkmark-pop_0.5s_ease-out_both] flex flex-col items-center gap-4">
        <Image
          src="/frog-logo.png"
          alt=""
          width={120}
          height={120}
          className="rounded-3xl"
          aria-hidden="true"
          unoptimized
        />
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-success">
          <Check className="h-6 w-6 text-success-foreground" strokeWidth={3} />
        </div>
      </div>

      <div className="animate-[fade-in-up_0.5s_ease-out_0.3s_both] text-center space-y-3">
        <h2 className="text-2xl font-bold tracking-tight">
          Ласкаво просимо, {user.firstName}!
        </h2>
        <p className="text-base text-muted-foreground">
          Ваш кабінет готовий. Переходимо...
        </p>
      </div>

      {/* Manual continue button — accessibility (WCAG 2.2.1) */}
      <Button
        variant="ghost"
        onClick={handleContinue}
        className="animate-[fade-in-up_0.5s_ease-out_0.6s_both] text-sm text-muted-foreground"
      >
        Перейти зараз
      </Button>
    </div>
  );
}
```

### Changes Summary
- Mascot: 80px → 120px, `rounded-3xl`
- Checkmark circle: h-10 w-10 → h-12 w-12
- Added manual "Перейти зараз" button (ghost, delayed entrance animation)
- Reduced-motion timer: 5000ms (was 2500ms for all)
- Heading: `text-xl` → `text-2xl font-bold tracking-tight`
- Body: `text-sm` → `text-base`
- Greeting: Simplified to first name only
- Spacing: `space-y-6` → `space-y-8`, `py-12` → `py-16`
- Images: `alt=""` + `aria-hidden="true"`

---

## Verification Checklist
- [ ] Onboarding: no Card/border visible around steps
- [ ] Mobile: progress bar at top (thin, fills based on step)
- [ ] Desktop: horizontal step indicators above form content
- [ ] Step transition: smooth slide (24px translateX, 250ms)
- [ ] Terms: document rows are 64px tall, rounded-xl
- [ ] Terms: checkbox label is 16px
- [ ] Terms: CTA sticky at bottom on mobile, static on desktop
- [ ] DOB: heading says "Коли ви народились?"
- [ ] DOB: select triggers are 48px tall, rounded-xl
- [ ] Name: heading says "Як вас звати?"
- [ ] Name: inputs are 48px tall, rounded-xl
- [ ] Name: button says "Завершити реєстрацію"
- [ ] Welcome: mascot is 120px, manual "Перейти зараз" button visible
- [ ] Welcome: auto-redirect works (2.5s normal, 5s reduced-motion)
- [ ] Dark mode: all steps render correctly
- [ ] Reduced motion: no slide animations, content appears immediately
- [ ] Screen reader: step announcements via aria-live
- [ ] Screen reader: focus moves to step heading on transition
- [ ] All form validations still work (terms viewed, age >= 16, required fields)
- [ ] Playwright: complete full onboarding flow (terms → DOB → name → welcome → dashboard)

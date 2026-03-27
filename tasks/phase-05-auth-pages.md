# Phase 5: Auth Pages (UC-01)

**Depends on**: Phase 3, Phase 4
**Blocks**: None (can be built alongside Phase 6-7)

## Checklist

- [ ] `src/app/(auth)/login/page.tsx` + `src/components/auth/login-form.tsx`:
  - Fields: email, password
  - Zod schema: `{ email: z.string().email(), password: z.string().min(8).max(72) }`
  - React Hook Form with `@hookform/resolvers/zod`
  - Submit calls `auth.login()` → on success redirect to dashboard
  - Error states: 401 (invalid credentials), 429 (rate limited)
  - Link to register, forgot-password
  - `generateMetadata()`: title "Вхід — Приймальна комісія ФІОТ"

- [ ] `src/app/(auth)/register/page.tsx` + `src/components/auth/register-form.tsx`:
  - Fields: firstName, middleName (optional), lastName, email, password, confirmPassword, terms checkbox
  - Zod schema with password match refinement
  - On success: redirect to verify-email page
  - Error: email already registered
  - `generateMetadata()`: title "Реєстрація — Приймальна комісія ФІОТ"

- [ ] `src/app/(auth)/verify-email/page.tsx` + `src/components/auth/verify-email-form.tsx`:
  - Token input (from email link or manual)
  - Search params: `?token=...` auto-fills
  - Resend verification button with 60s cooldown
  - On success: redirect to login with success toast
  - `generateMetadata()`: title "Підтвердження пошти"

- [ ] `src/app/(auth)/forgot-password/page.tsx`:
  - Email input only
  - Submit shows success message regardless (security: don't reveal if email exists)
  - Link back to login
  - `generateMetadata()`: title "Відновлення пароля"

- [ ] `src/app/(auth)/reset-password/page.tsx`:
  - Fields: new password, confirm password
  - Token from URL search params
  - On success: redirect to login
  - `generateMetadata()`: title "Новий пароль"

## Deliverables
- All 5 auth pages functional with form validation
- Ukrainian metadata titles
- Proper error handling and loading states
- Redirect logic (auth → dashboard if logged in, dashboard → login if not)

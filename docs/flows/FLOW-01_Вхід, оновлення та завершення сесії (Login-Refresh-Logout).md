# FLOW-01: Вхід, оновлення та завершення сесії (Login/Refresh/Logout)

#### Purpose / Context
Безпечна автентифікація з короткоживучим access-токеном і refresh-флоу.
* * *
#### Actors
Користувач (Applicant/Commission), Gateway, Core / Auth.
* * *
#### Trigger
`POST /auth/login`, далі авто-refresh перед закінченням `exp`, `POST /auth/logout`
* * *
#### Preconditions
Обліковий запис активний; rate-limit не перевищено.
* * *
#### Main Flow / Sequence
1. UI → Gateway → Core `/auth/login` (email/phone + пароль або SSO)
2. Core: перевірка, видає **access** (15–30 хв) + **refresh** (7–14 днів), `roles[]`
3. UI зберігає токени (httpOnly cookie/secure storage), додає `Authorization: Bearer`
4. Перед `exp` — `POST /auth/refresh` → нові токени; `jti` старого вносимо в revoke-лист (Redis)
5. Logout: `POST /auth/logout` → revoke refresh, інвалід доступного `jti`
* * *
#### Alternatives
*   A1: MFA для ризикових входів (TOTP/SMS) між кроками 1–2.
* * *
#### Errors / Retry
401/403 (невірні креденшали/блок); 429 (rate-limit); backoff 1–5–15 с.
* * *
#### State & Data
`users`, `audit_log(login)`, Redis `revoke:<jti>`
* * *
#### Security / Compliance
*   RS256;
*   пароль-хеш Argon2/bcrypt;
*   CAPTCHA після n-спроб.
#### Observability & SLO
p95 login ≤ 300 мс; алерти на spike 401/429.
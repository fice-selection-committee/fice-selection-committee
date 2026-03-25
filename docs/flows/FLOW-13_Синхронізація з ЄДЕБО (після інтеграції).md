# FLOW-13: Синхронізація з ЄДЕБО (після інтеграції)

#### Purpose / Context
Отримання рекомендацій/підтверджень, вирівнювання статусів.
* * *
#### Actors
Core/Integrator, ЄДЕБО API.
* * *
#### Trigger
Ручний запуск / CRON.
* * *
#### Preconditions
Валідні креденшали; вікно доступу.
* * *
#### Main Flow / Sequence
1. Пакетні запити з пагінацією; тротлінг по rate-limits.
2. Маппінг відповідей → оновлення `applications.status`
3. Звіт синхронізації + alerts при розбіжностях.
* * *
#### Alternatives
*   A1: Часткова синхронізація по спеціальності/інтервалу дат.
* * *
#### Errors / Retry
*   Недоступність → backoff + зведений звіт.
* * *
#### State & Data
`integration_logs`
* * *
#### Security / Compliance
*   Секрети в менеджері; IP allowlist (якщо потрібно)
#### Observability & SLO
Завершення синка у вікні ≤ Х хвилин (узгоджується)
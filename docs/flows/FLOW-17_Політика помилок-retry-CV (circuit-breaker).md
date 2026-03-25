# FLOW-17: Політика помилок/retry/CV (circuit-breaker)

#### Purpose / Context
Стабільність при збоях інтеграцій/сховищ.
* * *
#### Actors
Core, Gateway, Зовнішні API.
* * *
#### Trigger
Будь-які зовнішні виклики.
* * *
#### Preconditions
Стандартизована виняткова модель.
* * *
#### Main Flow / Sequence
1. Для інтеграцій: CB-обгортка (open/half-open/close), експоненційний backoff.
2. Класифікація помилок: `4xx` (не повторювати), `5xx/timeout` (retry)
3. У користувача — бізнес-повідомлення + кореляційний `traceId`
* * *
#### Alternatives
*   A1: Queue-out для довгих/нестабільних задач.
* * *
#### Errors / Retry
*   За політиками:
    *   Private ([https://app.clickup.com/90151679901/docs/2kyqaxwx-755/2kyqaxwx-3855](https://app.clickup.com/90151679901/docs/2kyqaxwx-755/2kyqaxwx-3855))
    *   Private ([https://app.clickup.com/90151679901/docs/2kyqaxwx-755/2kyqaxwx-3935](https://app.clickup.com/90151679901/docs/2kyqaxwx-755/2kyqaxwx-3935))
* * *
#### State & Data
Логи CV (open/close), лічильники спроб.
* * *
#### Observability & SLO
ALERT при open-rate > порога, DLQ>0.
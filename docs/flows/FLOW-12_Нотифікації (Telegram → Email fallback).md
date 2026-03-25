# FLOW-12: Нотифікації (Telegram → Email fallback)

#### Purpose / Context
Оповіщення про статуси/помилки/дедлайни.
* * *
#### Actors
Core/Notifications, Telegram API, Gmail SMTP, Recipient.
* * *
#### Trigger
Події (`accepted`, `rework`, `order_ready`), SLA-таймери.
* * *
#### Preconditions
Підв’язаний chat\_id/email; дозволи на розсилку.
* * *
#### Main Flow / Sequence
1. Визначення каналу; підстановка шаблону (локаль uk/en).
2. Відправка; лог статусу доставки/прочитання (де доступно).
3. При фейлі TG → автоматичний фолбек на Email.
* * *
#### Alternatives
*   A1: Батч-розсилки (rate-limit)
* * *
#### Errors / Retry
*   429/5xx — backoff;
*   DLQ на 3-й помилці.
* * *
#### State & Data
`notifications`, (events/logs)
* * *
#### Security / Compliance
*   Відписка від не критичних; DMARC / DKIM / SPF для пошти.
#### Observability & SLO
Доставка ≥ 98%; час до доставки ≤ 60 с у 95-му перцентилі.
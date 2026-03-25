# FLOW-15: Аудит дій (Audit Trail)

#### Purpose / Context
Слідчість кожної критичної операції.
* * *
#### Actors
Всі користувачі/сервіси, Core/Audit.
* * *
#### Trigger
CRUD на сутностях, входи, налаштування, доступи.
* * *
#### Preconditions
Наявний `traceId`; час сервера синхронізований (NTP).
* * *
#### Main Flow / Sequence
1. Перед виконанням — підготовка контексту (actor, role, ip, ua, traceId)
2. Після виконання — запис події в `audit_log` (entity, entity\_id, diff/meta)
3. Експорт у сховище логів/трейсів.
* * *
#### Alternatives
*   A1: Батч-запис при високій інтенсивності (буфер)
* * *
#### Errors / Retry
*   При збоях — локальний буфер + повтор.
* * *
#### State & Data
`audit_log` таблиця, зберігання ≥ 1–2 роки.
* * *
#### Security / Compliance
*   Мінімізувати PII; контроль доступу до звітів.
#### Observability & SLO
100% критичних дій у журналі; ALERT на пропуски.